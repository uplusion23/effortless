package dev.huskuraft.effortless.building.clipboard;

import dev.huskuraft.universal.api.math.Vector3i;

public enum SnapshotTransform {
    ROTATE_X,
    ROTATE_Y,
    ROTATE_Z,
    MIRROR_X,
    MIRROR_Y,
    MIRROR_Z,
    INCREASE_X,
    INCREASE_Y,
    INCREASE_Z,
    DECREASE_X,
    DECREASE_Y,
    DECREASE_Z;

    public Orientation getOrientation() {
        return switch (this) {
            case ROTATE_X -> Orientation.ROTATE_X;
            case ROTATE_Y -> Orientation.ROTATE_Y;
            case ROTATE_Z -> Orientation.ROTATE_Z;
            case MIRROR_X -> Orientation.MIRROR_X;
            case MIRROR_Y -> Orientation.MIRROR_Y;
            case MIRROR_Z -> Orientation.MIRROR_Z;
            default -> Orientation.IDENTITY;
        };
    }

    public Vector3i getOffset() {
        return switch (this) {
            case INCREASE_X -> Vector3i.UNIT_X;
            case INCREASE_Y -> Vector3i.UNIT_Y;
            case INCREASE_Z -> Vector3i.UNIT_Z;
            case DECREASE_X -> Vector3i.UNIT_MINUS_X;
            case DECREASE_Y -> Vector3i.UNIT_MINUS_Y;
            case DECREASE_Z -> Vector3i.UNIT_MINUS_Z;
            default -> Vector3i.ZERO;
        };
    }
}
