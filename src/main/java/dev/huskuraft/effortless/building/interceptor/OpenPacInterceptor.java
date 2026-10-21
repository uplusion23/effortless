package dev.huskuraft.effortless.building.interceptor;

import dev.huskuraft.universal.api.core.BlockInteraction;
import dev.huskuraft.universal.api.core.BlockPosition;
import dev.huskuraft.universal.api.core.Player;
import dev.huskuraft.universal.api.core.World;
import dev.huskuraft.universal.api.platform.ClientEntrance;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.plugin.openpac.OpenPacChunkClaimsManager;
import dev.huskuraft.universal.api.plugin.openpac.OpenPacChunkProtection;
import dev.huskuraft.universal.api.plugin.openpac.OpenPacPlugin;

public final class OpenPacInterceptor implements BuildInterceptor {

    private final OpenPacChunkProtection openPacChunkProtection;
    private final OpenPacChunkClaimsManager openPacChunkClaimsManager;

    public OpenPacInterceptor(
            Entrance entrance
    ) {
        if (entrance instanceof ClientEntrance) {
            // Only the server knows the party, ally and exception permissions of a claim. The server checks each build.
            this.openPacChunkProtection = null;
            this.openPacChunkClaimsManager = null;
        } else {
            var plugin = entrance.findPlugin(OpenPacPlugin.class);
            this.openPacChunkProtection = plugin.map(openPacPlugin -> openPacPlugin.getChunkProtection(entrance.getServer())).orElse(null);
            // If the platform has no claim protection, use the claim owner.
            this.openPacChunkClaimsManager = openPacChunkProtection != null ? null : plugin.map(openPacPlugin -> openPacPlugin.getServerClaimManager(entrance.getServer())).orElse(null);
        }
    }

    @Override
    public boolean isEnabled() {
        return openPacChunkProtection != null || openPacChunkClaimsManager != null;
    }

    @Override
    public boolean allowBreakBlock(Player player, World world, BlockPosition blockPosition) {
        if (openPacChunkProtection != null) {
            return openPacChunkProtection.canBreakBlock(player, world, blockPosition);
        }
        return isClaimOwner(player, world, blockPosition);
    }

    @Override
    public boolean allowPlaceBlock(Player player, World world, BlockPosition blockPosition) {
        if (openPacChunkProtection != null) {
            return openPacChunkProtection.canPlaceBlock(player, world, blockPosition);
        }
        return isClaimOwner(player, world, blockPosition);
    }

    @Override
    public boolean allowInteractBlock(Player player, World world, BlockInteraction blockInteraction) {
        if (openPacChunkProtection != null) {
            return openPacChunkProtection.canInteractBlock(player, world, blockInteraction);
        }
        return isClaimOwner(player, world, blockInteraction.getBlockPosition());
    }

    private boolean isClaimOwner(Player player, World world, BlockPosition blockPosition) {
        if (openPacChunkClaimsManager == null) {
            return true;
        }
        var claim = openPacChunkClaimsManager.get(world.getDimensionId().location(), blockPosition);
        return claim == null || claim.getPlayerId().equals(player.getId());
    }
}
