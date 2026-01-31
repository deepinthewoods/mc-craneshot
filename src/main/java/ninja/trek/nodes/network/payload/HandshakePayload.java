package ninja.trek.nodes.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import ninja.trek.Craneshot;

public record HandshakePayload(int stage, int protocol, boolean serverAuthoritative, boolean canEdit) implements CustomPacketPayload {
    public static final Type<HandshakePayload> ID = new Type<>(Identifier.fromNamespaceAndPath(Craneshot.MOD_ID, "handshake"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, HandshakePayload> CODEC = StreamCodec.ofMember(
            HandshakePayload::write,
            HandshakePayload::read
    );

    private HandshakePayload(RegistryFriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean());
    }

    public HandshakePayload(int stage, int protocol) {
        this(stage, protocol, false, false);
    }

    private static HandshakePayload read(RegistryFriendlyByteBuf buf) {
        return new HandshakePayload(buf);
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(stage);
        buf.writeVarInt(protocol);
        buf.writeBoolean(serverAuthoritative);
        buf.writeBoolean(canEdit);
    }

    @Override
    public Type<HandshakePayload> type() {
        return ID;
    }
}
