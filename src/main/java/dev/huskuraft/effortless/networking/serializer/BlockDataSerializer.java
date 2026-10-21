package dev.huskuraft.effortless.networking.serializer;

import dev.huskuraft.universal.api.core.BlockPosition;
import dev.huskuraft.universal.api.networking.NetByteBuf;
import dev.huskuraft.universal.api.networking.NetByteBufSerializer;
import dev.huskuraft.effortless.building.clipboard.BlockData;

public class BlockDataSerializer implements NetByteBufSerializer<BlockData> {

    // Positions near the origin are frequently negative. A var int needs five bytes for a negative value, a zigzag value needs fewer.
    private static int readZigZag(NetByteBuf byteBuf) {
        var value = byteBuf.readVarInt();
        return (value >>> 1) ^ -(value & 1);
    }

    private static void writeZigZag(NetByteBuf byteBuf, int value) {
        byteBuf.writeVarInt((value << 1) ^ (value >> 31));
    }

    @Override
    public BlockData read(NetByteBuf byteBuf) {
        return new BlockData(
                BlockPosition.at(readZigZag(byteBuf), readZigZag(byteBuf), readZigZag(byteBuf)),
                byteBuf.readNullable(NetByteBuf::readBlockState),
                byteBuf.readNullable(NetByteBuf::readRecordTag)
        );
    }

    @Override
    public void write(NetByteBuf byteBuf, BlockData blockData) {
        writeZigZag(byteBuf, blockData.blockPosition().x());
        writeZigZag(byteBuf, blockData.blockPosition().y());
        writeZigZag(byteBuf, blockData.blockPosition().z());
        byteBuf.writeNullable(blockData.blockState(), NetByteBuf::writeBlockState);
        byteBuf.writeNullable(blockData.entityTag(), NetByteBuf::writeRecordTag);
    }
}
