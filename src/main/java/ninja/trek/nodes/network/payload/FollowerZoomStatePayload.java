package ninja.trek.nodes.network.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import ninja.trek.Craneshot;

import java.util.UUID;

public record FollowerZoomStatePayload(
        UUID playerId,
        boolean active,
        String dimension,
        BlockPos blockPos,
        Vec3 hitLocation,
        float yaw,
        float pitch
) implements CustomPacketPayload {
    public static final Type<FollowerZoomStatePayload> ID = new Type<>(
            Identifier.fromNamespaceAndPath(Craneshot.MOD_ID, "follower_zoom_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FollowerZoomStatePayload> CODEC = StreamCodec.ofMember(
            FollowerZoomStatePayload::write,
            FollowerZoomStatePayload::read
    );

    public static FollowerZoomStatePayload inactive(UUID playerId) {
        return new FollowerZoomStatePayload(playerId, false, "", BlockPos.ZERO, Vec3.ZERO, 0.0f, 0.0f);
    }

    private static FollowerZoomStatePayload read(RegistryFriendlyByteBuf buf) {
        return new FollowerZoomStatePayload(
                buf.readUUID(),
                buf.readBoolean(),
                buf.readUtf(FollowerZoomRequestPayload.MAX_DIMENSION_LENGTH),
                buf.readBlockPos(),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                buf.readFloat(),
                buf.readFloat()
        );
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(playerId);
        buf.writeBoolean(active);
        buf.writeUtf(dimension, FollowerZoomRequestPayload.MAX_DIMENSION_LENGTH);
        buf.writeBlockPos(blockPos);
        buf.writeDouble(hitLocation.x);
        buf.writeDouble(hitLocation.y);
        buf.writeDouble(hitLocation.z);
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
    }

    @Override
    public Type<FollowerZoomStatePayload> type() {
        return ID;
    }
}
