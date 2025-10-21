package ninja.trek.nodes.network.payload;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import ninja.trek.Craneshot;

public record HandshakePayload(int stage, int protocol, boolean serverAuthoritative, boolean canEdit) implements CustomPayload {
    public static final Id<HandshakePayload> ID = new Id<>(Identifier.of(Craneshot.MOD_ID, "handshake"));
    
    public static final PacketCodec<RegistryByteBuf, HandshakePayload> CODEC = PacketCodec.of(
            HandshakePayload::write,
            HandshakePayload::read
    );

    private HandshakePayload(RegistryByteBuf buf) {
        this(buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean());
    }

    public HandshakePayload(int stage, int protocol) {
        this(stage, protocol, false, false);
    }

    private static HandshakePayload read(RegistryByteBuf buf) {
        return new HandshakePayload(buf);
    }

    private void write(RegistryByteBuf buf) {
        buf.writeVarInt(stage);
        buf.writeVarInt(protocol);
        buf.writeBoolean(serverAuthoritative);
        buf.writeBoolean(canEdit);
    }

    @Override
    public Id<HandshakePayload> getId() {
        return ID;
    }
}
