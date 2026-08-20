package ninja.trek.integration;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/**
 * Wire-compatible fallback for Mouth Anim's clientbound speaking-state relay.
 * Craneshot registers it only when the Mouth Anim mod is absent locally.
 */
public record MouthAnimRelayPayload(UUID playerUuid, byte stateId) implements CustomPacketPayload {
    public static final Type<MouthAnimRelayPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("mouth-anim", "s2c_mouth_state"));

    public static final StreamCodec<FriendlyByteBuf, MouthAnimRelayPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, value) -> {
                        buf.writeUUID(value.playerUuid());
                        buf.writeByte(value.stateId());
                    },
                    buf -> new MouthAnimRelayPayload(buf.readUUID(), buf.readByte())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
