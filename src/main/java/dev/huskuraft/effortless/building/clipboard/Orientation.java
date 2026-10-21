package dev.huskuraft.effortless.building.clipboard;

import java.util.Arrays;
import java.util.function.UnaryOperator;

import dev.huskuraft.universal.api.core.Axis;
import dev.huskuraft.universal.api.core.AxisDirection;
import dev.huskuraft.universal.api.core.BlockPosition;
import dev.huskuraft.universal.api.core.BlockState;
import dev.huskuraft.universal.api.core.Direction;
import dev.huskuraft.universal.api.core.Revolve;
import dev.huskuraft.universal.api.math.MathUtils;
import dev.huskuraft.universal.api.math.Vector3i;

/**
 * A combination of quarter turns and flips of the block grid around the snapshot origin. It stores the directions of
 * the positive axes after the transform.
 * <p>
 * Apply the combined orientation to a snapshot in one step. Some block states have no intermediate value, for example
 * a stair on its side. Thus a sequence of single quarter turns can change these states incorrectly.
 */
public record Orientation(Direction x, Direction y, Direction z) {

    public static final Orientation IDENTITY = new Orientation(Direction.EAST, Direction.UP, Direction.SOUTH);

    // These are the same transforms as in RotateContext and MirrorContext.
    public static final Orientation ROTATE_X = of(direction -> Direction.getNearest(direction.step().rotX(MathUtils.PI / 2)));
    public static final Orientation ROTATE_Y = of(direction -> Direction.getNearest(direction.step().rotY(MathUtils.PI / 2)));
    public static final Orientation ROTATE_Z = of(direction -> Direction.getNearest(direction.step().rotZ(MathUtils.PI / 2)));
    public static final Orientation MIRROR_X = of(direction -> direction.getAxis() == Axis.X ? direction.getOpposite() : direction);
    public static final Orientation MIRROR_Y = of(direction -> direction.getAxis() == Axis.Y ? direction.getOpposite() : direction);
    public static final Orientation MIRROR_Z = of(direction -> direction.getAxis() == Axis.Z ? direction.getOpposite() : direction);

    private static final Orientation HALF_TURN_X = ROTATE_X.then(ROTATE_X);

    public static Orientation of(UnaryOperator<Direction> mapping) {
        return new Orientation(mapping.apply(Direction.EAST), mapping.apply(Direction.UP), mapping.apply(Direction.SOUTH));
    }

    public Direction apply(Direction direction) {
        var image = switch (direction.getAxis()) {
            case X -> x;
            case Y -> y;
            case Z -> z;
        };
        return direction.getAxisDirection() == AxisDirection.POSITIVE ? image : image.getOpposite();
    }

    public Vector3i apply(Vector3i vector) {
        return new Vector3i(
                vector.x() * x.getStepX() + vector.y() * y.getStepX() + vector.z() * z.getStepX(),
                vector.x() * x.getStepY() + vector.y() * y.getStepY() + vector.z() * z.getStepY(),
                vector.x() * x.getStepZ() + vector.y() * y.getStepZ() + vector.z() * z.getStepZ()
        );
    }

    public BlockPosition apply(BlockPosition blockPosition) {
        return BlockPosition.at(apply(new Vector3i(blockPosition.x(), blockPosition.y(), blockPosition.z())));
    }

    public BlockState apply(BlockState blockState) {
        return getBlockStateTransform().apply(blockState);
    }

    public UnaryOperator<BlockState> getBlockStateTransform() {
        // Split the orientation into a vertical turn and a horizontal part. The mirror and rotate functions of
        // Minecraft apply the horizontal part correctly to rails, signs, stair shapes and door hinges.
        var vertical = getVerticalTurn(apply(Direction.UP));
        var horizontal = then(vertical.inverse());
        var mirror = horizontal.isReflection();
        var revolve = switch (horizontal.apply(Direction.NORTH)) {
            case EAST -> Revolve.CLOCKWISE_90;
            case SOUTH -> Revolve.CLOCKWISE_180;
            case WEST -> Revolve.COUNTERCLOCKWISE_90;
            default -> Revolve.NONE;
        };
        return blockState -> {
            if (blockState == null) {
                return null;
            }
            var result = mirror ? blockState.mirror(Axis.X) : blockState;
            if (revolve != Revolve.NONE) {
                result = result.rotate(revolve);
            }
            if (!vertical.equals(IDENTITY)) {
                result = result.reorient(vertical::apply);
            }
            return result;
        };
    }

    // Returns this orientation, then the other orientation.
    public Orientation then(Orientation other) {
        return new Orientation(other.apply(x), other.apply(y), other.apply(z));
    }

    public Orientation inverse() {
        return of(direction -> Arrays.stream(Direction.values()).filter(source -> apply(source) == direction).findFirst().orElseThrow());
    }

    public boolean isReflection() {
        var cross = x.getNormal().cross(y.getNormal());
        return cross.x() * z.getStepX() + cross.y() * z.getStepY() + cross.z() * z.getStepZ() < 0;
    }

    private static Orientation getVerticalTurn(Direction up) {
        return switch (up) {
            case UP -> IDENTITY;
            case DOWN -> HALF_TURN_X;
            case SOUTH -> ROTATE_X;
            case NORTH -> ROTATE_X.inverse();
            case WEST -> ROTATE_Z;
            case EAST -> ROTATE_Z.inverse();
        };
    }

}
