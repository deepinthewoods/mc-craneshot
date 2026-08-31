package ninja.trek.nodes.network.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import ninja.trek.Craneshot;

public record FollowerZoomRequestPayload(
        boolean active,
        String dimension,
        BlockPos blockPos,
        Vec3 hitLocation,
        float yaw,
        float pitch
) implements CustomPacketPayload {
    public static final int MAX_DIMENSION_LENGTH = 256;
    public static final Type<FollowerZoomRequestPayload> ID = new Type<>(
            Identifier.fromNamespaceAndPath(Craneshot.MOD_ID, "follower_zoom_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FollowerZoomRequestPayload> CODEC = StreamCodec.ofMember(
            FollowerZoomRequestPayload::write,
            FollowerZoomRequestPayload::read
    );

    public static FollowerZoomRequestPayload inactive() {
        return new FollowerZoomRequestPayload(false, "", BlockPos.ZERO, Vec3.ZERO, 0.0f, 0.0f);
    }

    private static FollowerZoomRequestPayload read(RegistryFriendlyByteBuf buf) {
        return new FollowerZoomRequestPayload(
                buf.readBoolean(),
                buf.readUtf(MAX_DIMENSION_LENGTH),
                buf.readBlockPos(),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                buf.readFloat(),
                buf.readFloat()
        );
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(active);
        buf.writeUtf(dimension, MAX_DIMENSION_LENGTH);
        buf.writeBlockPos(blockPos);
        buf.writeDouble(hitLocation.x);
        buf.writeDouble(hitLocation.y);
        buf.writeDouble(hitLocation.z);
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
    }

    @Override
    public Type<FollowerZoomRequestPayload> type() {
        return ID;
    }
}
