package dev.huskuraft.effortless.building.config;

import dev.huskuraft.universal.api.math.Range1i;
import dev.huskuraft.effortless.building.flight.FlightRange;
import dev.huskuraft.effortless.building.mirror.MirrorBreakMode;

public record BuilderConfig(
        int reservedToolDurability,
        boolean passiveMode,
        MirrorBreakMode mirrorBreakMode,
        // The server limits this value to the permission of the player.
        int flightRange
) {

    public static final Range1i RESERVED_TOOL_DURABILITY_RANGE = new Range1i(0, 32);

    public static BuilderConfig DEFAULT = new BuilderConfig(
            1,
            Boolean.FALSE,
            MirrorBreakMode.COPIES_ONLY,
            FlightRange.DEFAULT
    );

    public BuilderConfig withReservedToolDurability(int reservedToolDurability) {
        return new BuilderConfig(reservedToolDurability, passiveMode, mirrorBreakMode, flightRange);
    }

    public BuilderConfig withPassiveMode(boolean passiveMode) {
        return new BuilderConfig(reservedToolDurability, passiveMode, mirrorBreakMode, flightRange);
    }

    public BuilderConfig withMirrorBreakMode(MirrorBreakMode mirrorBreakMode) {
        return new BuilderConfig(reservedToolDurability, passiveMode, mirrorBreakMode, flightRange);
    }

    public BuilderConfig withFlightRange(int flightRange) {
        return new BuilderConfig(reservedToolDurability, passiveMode, mirrorBreakMode, flightRange);
    }


}
