package dev.huskuraft.effortless.building.mirror;

import dev.huskuraft.universal.api.core.Axis;
import dev.huskuraft.effortless.building.Option;

public enum MirrorAxis implements Option {
    MIRROR_X("mirror_x", Axis.X),
    MIRROR_Y("mirror_y", Axis.Y),
    MIRROR_Z("mirror_z", Axis.Z),
    ;

    private final String name;
    private final Axis axis;

    MirrorAxis(String name, Axis axis) {
        this.name = name;
        this.axis = axis;
    }

    public static MirrorAxis of(Axis axis) {
        return switch (axis) {
            case X -> MIRROR_X;
            case Y -> MIRROR_Y;
            case Z -> MIRROR_Z;
        };
    }

    public Axis getAxis() {
        return axis;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getCategory() {
        return "mirror";
    }

}
