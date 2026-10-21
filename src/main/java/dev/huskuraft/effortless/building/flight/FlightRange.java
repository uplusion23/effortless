package dev.huskuraft.effortless.building.flight;

import dev.huskuraft.universal.api.text.Text;

// The maximum flight distance from the respawn bed. The player sets it, and the server limits it.
public final class FlightRange {

    public static final int DEFAULT = 64;
    public static final int MIN = 0;
    public static final int MAX = 64;
    // The limit for server operators and players with EXTENDED_PERMISSION.
    public static final int EXTENDED_MAX = 256;
    // The last value of the slider.
    public static final int INFINITE = EXTENDED_MAX + 1;
    public static final String EXTENDED_PERMISSION = "effortless.flight.extended_range";
    // The operator level to use if the server has no permission mod.
    public static final int EXTENDED_PERMISSION_LEVEL = 2;

    private FlightRange() {
    }

    public static boolean isInfinite(int range) {
        return range >= INFINITE;
    }

    public static int clamp(int range, int max) {
        return Math.max(MIN, Math.min(range, max));
    }

    public static Text getDisplayName(int range) {
        if (isInfinite(range)) {
            return Text.translate("effortless.flight_range.infinite");
        }
        return Text.translate("effortless.flight_range.blocks", range);
    }

}
