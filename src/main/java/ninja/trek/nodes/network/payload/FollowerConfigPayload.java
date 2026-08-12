package ninja.trek.nodes.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import ninja.trek.Craneshot;

public record FollowerConfigPayload(String configJson) implements CustomPacketPayload {
    public static final Type<FollowerConfigPayload> ID = new Type<>(Identifier.fromNamespaceAndPath(Craneshot.MOD_ID, "follower_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FollowerConfigPayload> CODEC = StreamCodec.ofMember(
            FollowerConfigPayload::write,
            FollowerConfigPayload::read
    );

    private static FollowerConfigPayload read(RegistryFriendlyByteBuf buf) {
        return new FollowerConfigPayload(buf.readUtf());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(configJson);
    }

    @Override
    public Type<FollowerConfigPayload> type() {
        return ID;
    }
}
