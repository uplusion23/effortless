package dev.huskuraft.effortless.networking.serializer;

import dev.huskuraft.universal.api.math.BoundingBox3i;
import dev.huskuraft.universal.api.networking.NetByteBuf;
import dev.huskuraft.universal.api.networking.NetByteBufSerializer;
import dev.huskuraft.effortless.building.clipboard.Snapshot;

public class SnapshotSerializer implements NetByteBufSerializer<Snapshot> {

    @Override
    public Snapshot read(NetByteBuf byteBuf) {
        return new Snapshot(
                byteBuf.readString(),
                byteBuf.readLong(),
                byteBuf.readList(new BlockDataSerializer()),
                byteBuf.readNullable(new RegionSerializer())
        );
    }

    @Override
    public void write(NetByteBuf byteBuf, Snapshot snapshot) {
        byteBuf.writeString(snapshot.name());
        byteBuf.writeLong(snapshot.createdTimestamp());
        byteBuf.writeList(snapshot.blockData(), new BlockDataSerializer());
        byteBuf.writeNullable(snapshot.region(), new RegionSerializer());
    }

    public static class RegionSerializer implements NetByteBufSerializer<BoundingBox3i> {

        @Override
        public BoundingBox3i read(NetByteBuf byteBuf) {
            return new BoundingBox3i(
                    byteBuf.readVarInt(),
                    byteBuf.readVarInt(),
                    byteBuf.readVarInt(),
                    byteBuf.readVarInt(),
                    byteBuf.readVarInt(),
                    byteBuf.readVarInt()
            );
        }

        @Override
        public void write(NetByteBuf byteBuf, BoundingBox3i region) {
            byteBuf.writeVarInt(region.minX());
            byteBuf.writeVarInt(region.minY());
            byteBuf.writeVarInt(region.minZ());
            byteBuf.writeVarInt(region.maxX());
            byteBuf.writeVarInt(region.maxY());
            byteBuf.writeVarInt(region.maxZ());
        }
    }

}
