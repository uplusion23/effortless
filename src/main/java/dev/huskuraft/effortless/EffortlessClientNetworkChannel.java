package dev.huskuraft.effortless;

import dev.huskuraft.universal.api.core.Player;
import dev.huskuraft.universal.api.networking.NetworkChannel;
import dev.huskuraft.universal.api.networking.Packet;
import dev.huskuraft.universal.api.networking.Side;
import dev.huskuraft.effortless.building.config.ClientConfig;
import dev.huskuraft.effortless.networking.PacketSplitter;
import dev.huskuraft.effortless.networking.packets.AllPacketListener;
import dev.huskuraft.effortless.networking.packets.player.PlayerBuildPacket;
import dev.huskuraft.effortless.networking.packets.player.PlayerBuildTooltipPacket;
import dev.huskuraft.effortless.networking.packets.player.PlayerCommandPacket;
import dev.huskuraft.effortless.networking.packets.player.PlayerFlightPacket;
import dev.huskuraft.effortless.networking.packets.player.PlayerPermissionCheckPacket;
import dev.huskuraft.effortless.networking.packets.player.PlayerSettingsPacket;
import dev.huskuraft.effortless.networking.packets.player.PlayerSnapshotCapturePacket;
import dev.huskuraft.effortless.networking.packets.player.PlayerSnapshotSharePacket;
import dev.huskuraft.effortless.networking.packets.session.SessionConfigPacket;
import dev.huskuraft.effortless.networking.packets.session.SessionPacket;
import io.netty.buffer.ByteBuf;

public final class EffortlessClientNetworkChannel extends NetworkChannel<AllPacketListener> {

    private static final int COMPATIBILITY_VERSION = Effortless.PROTOCOL_VERSION;
    private final EffortlessClient entrance;
    private final AllPacketListener listener;
    private final PacketSplitter packetSplitter = new PacketSplitter();

    public EffortlessClientNetworkChannel(EffortlessClient entrance) {
        this(entrance, Effortless.DEFAULT_CHANNEL);
    }

    public EffortlessClientNetworkChannel(EffortlessClient entrance, String name) {
        super(entrance, name, Side.CLIENT);
        this.entrance = entrance;
        this.listener = new ClientPacketListener();

        registerPacket(SessionPacket.class, new SessionPacket.Serializer());
        registerPacket(SessionConfigPacket.class, new SessionConfigPacket.Serializer());

        registerPacket(PlayerCommandPacket.class, new PlayerCommandPacket.Serializer());
        registerPacket(PlayerSettingsPacket.class, new PlayerSettingsPacket.Serializer());
        registerPacket(PlayerBuildPacket.class, new PlayerBuildPacket.Serializer());
        registerPacket(PlayerPermissionCheckPacket.class, new PlayerPermissionCheckPacket.Serializer());
        registerPacket(PlayerBuildTooltipPacket.class, new PlayerBuildTooltipPacket.Serializer());
        registerPacket(PlayerSnapshotCapturePacket.class, new PlayerSnapshotCapturePacket.Serializer());
        registerPacket(PlayerSnapshotSharePacket.class, new PlayerSnapshotSharePacket.Serializer());
        registerPacket(PlayerFlightPacket.class, new PlayerFlightPacket.Serializer());

        getEntrance().getEventRegistry().getRegisterNetworkEvent().register(this::onRegisterNetwork);
    }

    private EffortlessClient getEntrance() {
        return entrance;
    }

    @Override
    public void sendBuffer(ByteBuf byteBuf, Player player) {
        for (var part : packetSplitter.split(byteBuf)) {
            super.sendBuffer(part, player);
        }
    }

    // Use for packets that the client sends again soon, for example build previews. These packets are not split.
    public boolean sendPacketIfSmall(Packet<AllPacketListener> packet) {
        var buffer = createBuffer(packet);
        if (!PacketSplitter.fitsInOneBuffer(buffer)) {
            return false;
        }
        super.sendBuffer(buffer, null);
        return true;
    }

    @Override
    public void receiveBuffer(ByteBuf byteBuf, Player player) {
        var buffer = packetSplitter.receive(byteBuf, player == null ? null : player.getId());
        if (buffer != null) {
            super.receiveBuffer(buffer, player);
        }
    }

    @Override
    public void receivePacket(Packet packet, Player player) {
        packet.handle(listener, player);
    }

    @Override
    public int getCompatibilityVersion() {
        return COMPATIBILITY_VERSION;
    }

    private class ClientPacketListener implements AllPacketListener {

        private boolean isValidReceiver() {
            if (getEntrance().getClient() == null) {
                return false;
            }
            if (getEntrance().getClient().getWorld() == null) {
                return false;
            }
            return true;
        }

        @Override
        public void handle(PlayerCommandPacket packet, Player player) {
            switch (packet.action()) {
                case REDO, UNDO -> {
                    // noop
                }
            }
        }

        @Override
        public void handle(PlayerSettingsPacket packet, Player player) {
        }

        @Override
        public void handle(PlayerBuildPacket packet, Player player) {
            if (!isValidReceiver()) {
                return;
            }
            var owner = getEntrance().getClient().getWorld().getPlayer(packet.playerId());
            if (owner == null) {
                return;
            }
            getEntrance().getClient().execute(() -> getEntrance().getStructureBuilder().onContextReceived(owner, packet.context()));
        }

        @Override
        public void handle(PlayerPermissionCheckPacket packet, Player player) {
        }

        @Override
        public void handle(PlayerBuildTooltipPacket packet, Player player) {
            if (!isValidReceiver()) {
                return;
            }
            getEntrance().getClient().execute(() -> getEntrance().getStructureBuilder().onTooltipReceived(player, packet.operationTooltip()));

        }

        @Override
        public void handle(SessionPacket packet, Player player) {
            getEntrance().getClient().execute(() -> getEntrance().getSessionManager().onSession(packet.session(), player));
        }

        @Override
        public void handle(SessionConfigPacket packet, Player player) {
            getEntrance().getClient().execute(() -> getEntrance().getSessionManager().onSessionConfig(packet.sessionConfig(), player));

        }

        @Override
        public void handle(PlayerSnapshotCapturePacket packet, Player player) {
            getEntrance().getConfigStorage().update(config -> new ClientConfig(config.renderConfig(), config.patternConfig(), config.clipboardConfig().appendHistory(packet.snapshot())));
            getEntrance().getClient().execute(() -> getEntrance().getStructureBuilder().onSnapshotCaptured(player, packet.snapshot()));
        }

        @Override
        public void handle(PlayerSnapshotSharePacket packet, Player player) {
            getEntrance().getClient().execute(() -> {


            });
        }

        // The response goes to the callback of the request.
        @Override
        public void handle(PlayerFlightPacket packet, Player player) {
        }
    }

}
