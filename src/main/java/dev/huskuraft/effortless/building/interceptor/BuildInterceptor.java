package dev.huskuraft.effortless.building.interceptor;

import dev.huskuraft.universal.api.core.BlockInteraction;
import dev.huskuraft.universal.api.core.BlockPosition;
import dev.huskuraft.universal.api.core.Player;
import dev.huskuraft.universal.api.core.World;

public interface BuildInterceptor {

    boolean isEnabled();

    boolean allowBreakBlock(Player player, World world, BlockPosition blockPosition);

    boolean allowPlaceBlock(Player player, World world, BlockPosition blockPosition);

    boolean allowInteractBlock(Player player, World world, BlockInteraction blockInteraction);

}
