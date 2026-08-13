package ninja.trek.nodes.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import ninja.trek.Craneshot;

public record FollowerConfigPayload(String configJson) implements CustomPacketPayload {
    public static final int MAX_CONFIG_LENGTH = 32_767;
    public static final Type<FollowerConfigPayload> ID = new Type<>(Identifier.fromNamespaceAndPath(Craneshot.MOD_ID, "follower_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FollowerConfigPayload> CODEC = StreamCodec.ofMember(
            FollowerConfigPayload::write,
            FollowerConfigPayload::read
    );

    private static FollowerConfigPayload read(RegistryFriendlyByteBuf buf) {
        return new FollowerConfigPayload(buf.readUtf(MAX_CONFIG_LENGTH));
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(configJson, MAX_CONFIG_LENGTH);
    }

    @Override
    public Type<FollowerConfigPayload> type() {
        return ID;
    }
}
