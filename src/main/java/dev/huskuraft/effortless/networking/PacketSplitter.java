package dev.huskuraft.effortless.networking;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.Nullable;

import dev.huskuraft.effortless.Effortless;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

// Minecraft disconnects a client that sends a payload of more than 32767 bytes.
// Thus this class splits large packets, for example large clipboards, into parts.
public final class PacketSplitter {

    // Packet IDs start at 0. Thus a buffer that is not split keeps its usual format.
    private static final int PART_ID = -1;
    private static final int PART_HEADER_SIZE = Integer.BYTES + Long.BYTES + Integer.BYTES + Integer.BYTES;
    // Less than the limit, because the platform adds data around the payload.
    private static final int MAX_BUFFER_SIZE = 30000;
    private static final int MAX_PART_DATA_SIZE = MAX_BUFFER_SIZE - PART_HEADER_SIZE;
    // These limits prevent a client from filling the memory of the server with parts.
    private static final int MAX_PARTS = 256;
    private static final int MAX_TRANSFERS_PER_SENDER = 2;
    private static final long TRANSFER_TIMEOUT_MILLIS = 30_000;
    private static final UUID UNKNOWN_SENDER = new UUID(0, 0);

    private final Map<TransferKey, Transfer> transfers = new ConcurrentHashMap<>();

    private record TransferKey(UUID sender, long id) {
    }

    private static final class Transfer {
        private final byte[][] parts;
        private final long startedAt = System.currentTimeMillis();
        private int received = 0;

        private Transfer(int count) {
            this.parts = new byte[count][];
        }
    }

    public static boolean fitsInOneBuffer(ByteBuf buffer) {
        return buffer.readableBytes() <= MAX_BUFFER_SIZE;
    }

    public List<ByteBuf> split(ByteBuf buffer) {
        if (fitsInOneBuffer(buffer)) {
            return List.of(buffer);
        }
        var size = buffer.readableBytes();
        var count = (size + MAX_PART_DATA_SIZE - 1) / MAX_PART_DATA_SIZE;
        if (count > MAX_PARTS) {
            throw new IllegalArgumentException("Packet of " + size + " bytes is too large to send");
        }
        var id = ThreadLocalRandom.current().nextLong();
        var parts = new ArrayList<ByteBuf>(count);
        for (var index = 0; index < count; index++) {
            var offset = buffer.readerIndex() + index * MAX_PART_DATA_SIZE;
            var length = Math.min(MAX_PART_DATA_SIZE, size - index * MAX_PART_DATA_SIZE);
            var part = Unpooled.buffer(PART_HEADER_SIZE + length);
            part.writeInt(PART_ID);
            part.writeLong(id);
            part.writeInt(index);
            part.writeInt(count);
            part.writeBytes(buffer, offset, length);
            parts.add(part);
        }
        return parts;
    }

    // Returns the buffer if it is not a part, or the full buffer when the last part arrives.
    // Returns null while parts are missing, or if the part is not valid.
    @Nullable
    public ByteBuf receive(ByteBuf buffer, @Nullable UUID sender) {
        if (buffer.readableBytes() < Integer.BYTES || buffer.getInt(buffer.readerIndex()) != PART_ID) {
            return buffer;
        }
        if (buffer.readableBytes() < PART_HEADER_SIZE) {
            Effortless.LOGGER.warn("Dropped a packet part without a header");
            return null;
        }
        sender = Objects.requireNonNullElse(sender, UNKNOWN_SENDER);
        buffer.readInt();
        var key = new TransferKey(sender, buffer.readLong());
        var index = buffer.readInt();
        var count = buffer.readInt();
        if (count < 2 || count > MAX_PARTS || index < 0 || index >= count || buffer.readableBytes() > MAX_PART_DATA_SIZE) {
            Effortless.LOGGER.warn("Dropped an invalid packet part {} of {} from {}", index, count, sender);
            return null;
        }
        var data = new byte[buffer.readableBytes()];
        buffer.readBytes(data);

        removeTimedOutTransfers();
        var transfer = transfers.get(key);
        if (transfer == null) {
            var finalSender = sender;
            if (transfers.keySet().stream().filter(other -> other.sender().equals(finalSender)).count() >= MAX_TRANSFERS_PER_SENDER) {
                Effortless.LOGGER.warn("Dropped a packet part from {}, too many packets are being received from it", sender);
                return null;
            }
            transfer = transfers.computeIfAbsent(key, k -> new Transfer(count));
        }
        synchronized (transfer) {
            if (transfer.parts.length != count || transfer.parts[index] != null) {
                Effortless.LOGGER.warn("Dropped a packet part {} of {} from {} that does not match its packet", index, count, sender);
                return null;
            }
            transfer.parts[index] = data;
            transfer.received++;
            if (transfer.received < count) {
                return null;
            }
        }
        transfers.remove(key);
        return Unpooled.wrappedBuffer(transfer.parts);
    }

    private void removeTimedOutTransfers() {
        var now = System.currentTimeMillis();
        transfers.values().removeIf(transfer -> now - transfer.startedAt > TRANSFER_TIMEOUT_MILLIS);
    }

}
