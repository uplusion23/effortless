package dev.huskuraft.effortless.networking.packets.player;

import java.util.UUID;

import dev.huskuraft.universal.api.core.Player;
import dev.huskuraft.universal.api.networking.NetByteBuf;
import dev.huskuraft.universal.api.networking.NetByteBufSerializer;
import dev.huskuraft.universal.api.networking.ResponsiblePacket;
import dev.huskuraft.effortless.networking.packets.AllPacketListener;

// Sends the flight range of the player. If toggle is true, the flight also changes.
// The response contains the range that the server uses and the maximum range for the player.
public record PlayerFlightPacket(
        UUID responseId,
        boolean toggle,
        int range,
        int maxRange
) implements ResponsiblePacket<AllPacketListener> {

    public PlayerFlightPacket(boolean toggle, int range) {
        this(UUID.randomUUID(), toggle, range, 0);
    }

    @Override
    public void handle(AllPacketListener packetListener, Player sender) {
        packetListener.handle(this, sender);
    }

    public static class Serializer implements NetByteBufSerializer<PlayerFlightPacket> {

        @Override
        public PlayerFlightPacket read(NetByteBuf byteBuf) {
            return new PlayerFlightPacket(byteBuf.readUUID(), byteBuf.readBoolean(), byteBuf.readVarInt(), byteBuf.readVarInt());
        }

        @Override
        public void write(NetByteBuf byteBuf, PlayerFlightPacket packet) {
            byteBuf.writeUUID(packet.responseId());
            byteBuf.writeBoolean(packet.toggle());
            byteBuf.writeVarInt(packet.range());
            byteBuf.writeVarInt(packet.maxRange());
        }

    }

}
