package ninja.trek.nodes.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import ninja.trek.Craneshot;

public record FollowerRegistrationPayload(int followerIndex) implements CustomPacketPayload {
    public static final Type<FollowerRegistrationPayload> ID = new Type<>(
            Identifier.fromNamespaceAndPath(Craneshot.MOD_ID, "follower_registration"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FollowerRegistrationPayload> CODEC = StreamCodec.ofMember(
            FollowerRegistrationPayload::write,
            FollowerRegistrationPayload::read
    );

    private static FollowerRegistrationPayload read(RegistryFriendlyByteBuf buf) {
        return new FollowerRegistrationPayload(buf.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(followerIndex);
    }

    @Override
    public Type<FollowerRegistrationPayload> type() {
        return ID;
    }
}
