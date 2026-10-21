package dev.huskuraft.effortless.building.mirror;

import dev.huskuraft.universal.api.core.BlockState;

// Controls which blocks a break removes on the other side of a mirror.
public enum MirrorBreakMode {
    // Break only the mirrored copy of the broken block. A different block on the other side stays.
    COPIES_ONLY("copies_only"),
    ALL_BLOCKS("all_blocks"),
    ;

    private final String name;

    MirrorBreakMode(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    // A mirrored copy is the same block. Only its orientation can be different.
    public boolean shouldBreak(BlockState broken, BlockState mirrored) {
        return switch (this) {
            case COPIES_ONLY -> broken != null && mirrored != null && broken.getBlock().equals(mirrored.getBlock());
            case ALL_BLOCKS -> true;
        };
    }

}
