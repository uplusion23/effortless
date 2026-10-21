package dev.huskuraft.effortless.building.clipboard;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import dev.huskuraft.universal.api.core.BlockPosition;
import dev.huskuraft.universal.api.core.ItemStack;
import dev.huskuraft.universal.api.math.BoundingBox3d;
import dev.huskuraft.universal.api.math.BoundingBox3i;
import dev.huskuraft.universal.api.math.Vector3d;
import dev.huskuraft.universal.api.math.Vector3i;
import dev.huskuraft.effortless.building.operation.ItemStackUtils;

// Block positions are relative to the origin. The origin is the block where the paste starts.
public record Snapshot(
        String name, long createdTimestamp, List<BlockData> blockData,
        // The full copied area, air included. It is null for snapshots that contain only blocks.
        @Nullable BoundingBox3i region
) {

    public static Snapshot EMPTY = new Snapshot("", 0, List.of());

    public Snapshot(String name, long createdTimestamp, List<BlockData> blockData) {
        this(name, createdTimestamp, blockData, null);
    }

    public boolean isEmpty() {
        return blockData.isEmpty();
    }

    public List<ItemStack> getItems() {
        return ItemStackUtils.flattenStack(blockData().stream().map(BlockData::blockState).filter(Objects::nonNull).map(blockState -> blockState.getItem().getDefaultStack().withCount(blockState.getRequiredItemCount())).collect(Collectors.toList()));
    }

    public int volume() {
        return box().volume();
    }

    public Vector3i box() {
        var region = getRegion();
        if (region == null) {
            return Vector3i.ZERO;
        }
        return new Vector3i(region.getXSpan(), region.getYSpan(), region.getZSpan());
    }

    @Nullable
    public BoundingBox3i getBlockRegion() {
        if (blockData().isEmpty()) {
            return null;
        }
        return BoundingBox3i.of(
                blockData().stream().mapToInt(blockData -> blockData.blockPosition().x()).min().getAsInt(),
                blockData().stream().mapToInt(blockData -> blockData.blockPosition().y()).min().getAsInt(),
                blockData().stream().mapToInt(blockData -> blockData.blockPosition().z()).min().getAsInt(),
                blockData().stream().mapToInt(blockData -> blockData.blockPosition().x()).max().getAsInt(),
                blockData().stream().mapToInt(blockData -> blockData.blockPosition().y()).max().getAsInt(),
                blockData().stream().mapToInt(blockData -> blockData.blockPosition().z()).max().getAsInt()
        );
    }

    // Older snapshots have no region. For these snapshots, use the bounds of the blocks.
    @Nullable
    public BoundingBox3i getRegion() {
        if (region != null) {
            return region;
        }
        return getBlockRegion();
    }

    // A region from the network can have any size. Thus the volume is a long.
    public long getRegionVolume() {
        var region = getRegion();
        if (region == null) {
            return 0;
        }
        return ((long) region.maxX() - region.minX() + 1) * ((long) region.maxY() - region.minY() + 1) * ((long) region.maxZ() - region.minZ() + 1);
    }

    public Stream<BlockPosition> getAirPositions() {
        var region = getRegion();
        if (region == null) {
            return Stream.empty();
        }
        var occupied = blockData().stream().map(BlockData::blockPosition).collect(Collectors.toCollection(HashSet::new));
        return IntStream.rangeClosed(region.minX(), region.maxX()).boxed().flatMap(x ->
                IntStream.rangeClosed(region.minY(), region.maxY()).boxed().flatMap(y ->
                        IntStream.rangeClosed(region.minZ(), region.maxZ()).mapToObj(z -> BlockPosition.at(x, y, z))
                )
        ).filter(blockPosition -> !occupied.contains(blockPosition));
    }

    public Vector3d getCenter() {
        if (blockData().isEmpty()) {
            return Vector3d.ZERO;
        }

        var maxX = blockData().stream().mapToInt(blockSnapshot -> blockSnapshot.blockPosition().x()).max().getAsInt();
        var maxY = blockData().stream().mapToInt(blockSnapshot -> blockSnapshot.blockPosition().y()).max().getAsInt();
        var maxZ = blockData().stream().mapToInt(blockSnapshot -> blockSnapshot.blockPosition().z()).max().getAsInt();
        var minX = blockData().stream().mapToInt(blockSnapshot -> blockSnapshot.blockPosition().x()).min().getAsInt();
        var minY = blockData().stream().mapToInt(blockSnapshot -> blockSnapshot.blockPosition().y()).min().getAsInt();
        var minZ = blockData().stream().mapToInt(blockSnapshot -> blockSnapshot.blockPosition().z()).min().getAsInt();

        return new Vector3d(
                minX + (maxX - minX) / 2f + 0.5f,
                minY + (maxY - minY) / 2f + 0.5f,
                minZ + (maxZ - minZ) / 2f + 0.5f
        );

    }

    public BoundingBox3d getBoundingBox() {
        var region = getRegion();
        if (region == null) {
            return BoundingBox3d.of(0, 0, 0, 0, 0, 0);
        }
        return BoundingBox3d.of(region.minX(), region.minY(), region.minZ(), region.maxX() + 1, region.maxY() + 1, region.maxZ() + 1);
    }

    public Snapshot withBlockData(List<BlockData> blockData) {
        return new Snapshot(name, createdTimestamp, blockData, region);
    }

    public Snapshot withRegion(@Nullable BoundingBox3i region) {
        return new Snapshot(name, createdTimestamp, blockData, region);
    }

    public Snapshot transform(Orientation orientation, Vector3i offset) {
        var blockStateTransform = orientation.getBlockStateTransform();
        return new Snapshot(name, createdTimestamp, blockData().stream().map(blockData -> new BlockData(
                orientation.apply(blockData.blockPosition()).add(offset),
                blockStateTransform.apply(blockData.blockState()),
                blockData.entityTag()
        )).collect(Collectors.toList()), region == null ? null : transform(region, orientation, offset));
    }

    private static BoundingBox3i transform(BoundingBox3i region, Orientation orientation, Vector3i offset) {
        var min = orientation.apply(new Vector3i(region.minX(), region.minY(), region.minZ())).add(offset);
        var max = orientation.apply(new Vector3i(region.maxX(), region.maxY(), region.maxZ())).add(offset);
        return BoundingBox3i.fromCorners(min, max);
    }

    public Snapshot update(SnapshotTransform action) {
        return transform(action.getOrientation(), action.getOffset());
    }
}
