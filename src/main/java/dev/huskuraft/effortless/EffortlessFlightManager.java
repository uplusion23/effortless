package dev.huskuraft.effortless;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import dev.huskuraft.universal.api.core.Player;
import dev.huskuraft.universal.api.events.lifecycle.ServerTick;
import dev.huskuraft.universal.api.platform.Server;
import dev.huskuraft.universal.api.text.ChatFormatting;
import dev.huskuraft.universal.api.text.Text;
import dev.huskuraft.effortless.building.flight.FlightRange;
import dev.huskuraft.effortless.networking.packets.player.PlayerFlightPacket;

// Gives creative flight to survival and adventure players near their respawn bed.
// The speed is half of the creative speed. Damage stops the flight.
// TODO: Add a server setting that disables the flight for all players.
public final class EffortlessFlightManager {

    private static final int TICKS_PER_SECOND = 20;
    private static final float DEFAULT_FLYING_SPEED = 0.05f;
    // This speed also identifies the flight of Effortless. A player saved after a crash can still have it.
    public static final float FLYING_SPEED = DEFAULT_FLYING_SPEED / 2;
    private static final int BED_RANGE_WARNING_DISTANCE = 16;
    public static final int HURT_COOLDOWN_TICKS = 15 * TICKS_PER_SECOND;
    // The permission of a player can change. Thus examine the range limit at this interval.
    private static final int RANGE_CHECK_TICKS = TICKS_PER_SECOND;

    private final Effortless entrance;
    private final Map<UUID, FlightState> states = new HashMap<>();
    private long tick = 0;

    private static final class FlightState {
        private boolean isEnabled = false;
        private int range = FlightRange.DEFAULT;
        // Health plus absorption. A decrease is damage.
        private float lastHealth = -1;
        private long lastHurtTick = -HURT_COOLDOWN_TICKS;
    }

    public EffortlessFlightManager(Effortless entrance) {
        this.entrance = entrance;

        getEntrance().getEventRegistry().getServerTickEvent().register(this::onServerTick);
        getEntrance().getEventRegistry().getPlayerLoggedInEvent().register(this::onPlayerLoggedIn);
        getEntrance().getEventRegistry().getPlayerLoggedOutEvent().register(this::onPlayerLoggedOut);
        getEntrance().getEventRegistry().getPlayerRespawnEvent().register(this::onPlayerRespawn);
        getEntrance().getEventRegistry().getServerStoppedEvent().register(server -> states.clear());
    }

    private Effortless getEntrance() {
        return entrance;
    }

    private FlightState getState(Player player) {
        return states.computeIfAbsent(player.getId(), uuid -> new FlightState());
    }

    // Creative and spectator modes have their own flight.
    private static boolean canUseFlight(Player player) {
        var gameMode = player.getGameMode();
        return gameMode != null && !gameMode.isCreative() && !gameMode.isSpectator();
    }

    private static boolean isFlightLeftOver(Player player) {
        return canUseFlight(player) && player.mayFly() && player.getFlyingSpeed() == FLYING_SPEED;
    }

    private static void sendMessage(Player player, Text message) {
        player.sendMessage(Effortless.getSystemMessage(message));
    }

    public int getMaxRange(Player player) {
        var isSinglePlayerOwner = getEntrance().getServerManager().getRunningServer().isSinglePlayerOwner(player.getProfile());
        if (isSinglePlayerOwner || player.hasPermission(FlightRange.EXTENDED_PERMISSION, FlightRange.EXTENDED_PERMISSION_LEVEL)) {
            return FlightRange.INFINITE;
        }
        return FlightRange.MAX;
    }

    private static Text getRangeText(int range) {
        return FlightRange.getDisplayName(range).withStyle(ChatFormatting.GOLD);
    }

    // Each packet contains the range. The response contains the range that the server uses.
    public void onFlightPacket(Player player, PlayerFlightPacket packet) {
        var state = getState(player);
        var maxRange = getMaxRange(player);
        state.range = FlightRange.clamp(packet.range(), maxRange);
        if (packet.toggle()) {
            toggleFlight(player);
        }
        getEntrance().getChannel().sendPacket(new PlayerFlightPacket(packet.responseId(), false, state.range, maxRange), player);
    }

    private void toggleFlight(Player player) {
        if (!getEntrance().getSessionManager().getLastSessionConfig().getByPlayer(player).allowUseMod()) {
            sendMessage(player, Text.translate("effortless.message.building.server.no_permission"));
            return;
        }
        var state = getState(player);
        if (state.isEnabled) {
            disableFlight(player, state, Text.translate("effortless.message.flight.disabled"));
            return;
        }
        if (!canUseFlight(player)) {
            sendMessage(player, Text.translate("effortless.message.flight.game_mode"));
            return;
        }
        var cooldownTicks = state.lastHurtTick + HURT_COOLDOWN_TICKS - tick;
        if (cooldownTicks > 0) {
            var cooldownSeconds = (cooldownTicks + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND;
            sendMessage(player, Text.translate("effortless.message.flight.hurt_cooldown", Text.text(String.valueOf(cooldownSeconds)).withStyle(ChatFormatting.GOLD)));
            return;
        }
        // Load the chunk of the bed for this check. The bed can be broken.
        var bed = player.getRespawnBedPosition(true);
        if (bed == null) {
            sendMessage(player, Text.translate("effortless.message.flight.no_bed"));
            return;
        }
        var distance = (int) player.getPosition().distance(bed.getCenter());
        if (!FlightRange.isInfinite(state.range) && distance > state.range) {
            sendMessage(player, Text.translate("effortless.message.flight.bed_too_far", Text.text(String.valueOf(distance)).withStyle(ChatFormatting.GOLD), getRangeText(state.range)));
            return;
        }
        state.isEnabled = true;
        player.setFlight(true, false, FLYING_SPEED);
        sendMessage(player, Text.translate("effortless.message.flight.enabled"));
    }

    private void disableFlight(Player player, FlightState state, Text message) {
        state.isEnabled = false;
        player.setFlight(false, false, DEFAULT_FLYING_SPEED);
        if (message != null) {
            sendMessage(player, message);
        }
    }

    private void onServerTick(Server server, ServerTick.Phase phase) {
        if (phase != ServerTick.Phase.END) {
            return;
        }
        this.tick++;
        for (var player : server.getPlayerList().getPlayers()) {
            var state = getState(player);
            var health = player.getHealth() + player.getAbsorptionAmount();
            var isHurt = state.lastHealth >= 0 && health < state.lastHealth;
            state.lastHealth = health;
            if (isHurt) {
                state.lastHurtTick = tick;
            }
            if (!state.isEnabled) {
                continue;
            }
            if (!canUseFlight(player)) {
                // The new game mode sets the flight. Reset only the speed.
                state.isEnabled = false;
                player.setFlight(player.mayFly(), player.isFlying(), DEFAULT_FLYING_SPEED);
                continue;
            }
            if (!player.mayFly()) {
                // Something else removed the flight, for example a command.
                disableFlight(player, state, null);
                continue;
            }
            if (player.isDeadOrDying()) {
                disableFlight(player, state, null);
                continue;
            }
            if (isHurt) {
                disableFlight(player, state, Text.translate("effortless.message.flight.disabled_hurt", Text.text(String.valueOf(HURT_COOLDOWN_TICKS / TICKS_PER_SECOND)).withStyle(ChatFormatting.GOLD)));
                continue;
            }
            var bed = player.getRespawnBedPosition(false);
            if (bed == null) {
                disableFlight(player, state, Text.translate("effortless.message.flight.disabled_no_bed"));
                continue;
            }
            if (tick % RANGE_CHECK_TICKS == 0) {
                state.range = FlightRange.clamp(state.range, getMaxRange(player));
            }
            if (FlightRange.isInfinite(state.range)) {
                continue;
            }
            var distance = player.getPosition().distance(bed.getCenter());
            if (distance > state.range) {
                disableFlight(player, state, Text.translate("effortless.message.flight.disabled_bed_too_far", getRangeText(state.range)));
                continue;
            }
            // For a short range, warn only in the last quarter.
            if (distance > state.range - Math.min(BED_RANGE_WARNING_DISTANCE, state.range / 4)) {
                player.sendMessage(Text.translate("effortless.message.flight.bed_range_warning", Text.text(String.valueOf((int) Math.ceil(state.range - distance))).withStyle(ChatFormatting.GOLD)), true);
            }
        }
    }

    private void onPlayerLoggedIn(Player player) {
        // The server stopped while the player flew. Remove the saved flight.
        if (isFlightLeftOver(player)) {
            player.setFlight(false, false, DEFAULT_FLYING_SPEED);
        }
    }

    // This occurs before the server saves the player. Thus the flight is not saved.
    private void onPlayerLoggedOut(Player player) {
        var state = states.remove(player.getId());
        if (state != null && state.isEnabled) {
            player.setFlight(false, false, DEFAULT_FLYING_SPEED);
        }
    }

    private void onPlayerRespawn(Player oldPlayer, Player newPlayer, boolean alive) {
        var state = states.get(oldPlayer.getId());
        if (state == null) {
            return;
        }
        state.lastHealth = -1;
        if (state.isEnabled) {
            state.isEnabled = false;
            if (isFlightLeftOver(newPlayer)) {
                newPlayer.setFlight(false, false, DEFAULT_FLYING_SPEED);
            }
        }
    }

}
