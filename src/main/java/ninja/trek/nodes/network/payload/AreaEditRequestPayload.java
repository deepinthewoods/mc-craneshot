package ninja.trek.nodes.network.payload;

import ninja.trek.Craneshot;
import ninja.trek.nodes.model.AreaInstanceDTO;

import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

public record AreaEditRequestPayload(
        EditOperation operation,
        ResourceKey<Level> dimension,
        AreaInstanceDTO areaData,
        UUID areaIdForDelete
) implements CustomPacketPayload {
    public static final Type<AreaEditRequestPayload> ID = new Type<>(Identifier.fromNamespaceAndPath(Craneshot.MOD_ID, "area_edit_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AreaEditRequestPayload> CODEC = StreamCodec.ofMember(
            AreaEditRequestPayload::write,
            AreaEditRequestPayload::read
    );

    private AreaEditRequestPayload(RegistryFriendlyByteBuf buf) {
        this(
                buf.readEnum(EditOperation.class),
                ResourceKey.create(Registries.DIMENSION, buf.readIdentifier()),
                readAreaData(buf),
                readAreaId(buf)
        );
    }

    private static AreaInstanceDTO readAreaData(RegistryFriendlyByteBuf buf) {
        if (buf.readBoolean()) {
            return AreaInstanceDTO.read(buf);
        }
        return null;
    }

    private static UUID readAreaId(RegistryFriendlyByteBuf buf) {
        if (buf.readBoolean()) {
            return buf.readUUID();
        }
        return null;
    }

    private static AreaEditRequestPayload read(RegistryFriendlyByteBuf buf) {
        return new AreaEditRequestPayload(buf);
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeEnum(operation);
        buf.writeIdentifier(dimension.identifier());
        boolean hasAreaData = areaData != null;
        buf.writeBoolean(hasAreaData);
        if (hasAreaData) {
            areaData.write(buf);
        }
        boolean hasAreaId = areaIdForDelete != null;
        buf.writeBoolean(hasAreaId);
        if (hasAreaId) {
            buf.writeUUID(areaIdForDelete);
        }
    }

    @Override
    public Type<AreaEditRequestPayload> type() {
        return ID;
    }

    public enum EditOperation {
        CREATE, UPDATE, DELETE
    }

    public static AreaEditRequestPayload create(ResourceKey<Level> dimension, AreaInstanceDTO area) {
        return new AreaEditRequestPayload(EditOperation.CREATE, dimension, area, null);
    }

    public static AreaEditRequestPayload update(ResourceKey<Level> dimension, AreaInstanceDTO area) {
        return new AreaEditRequestPayload(EditOperation.UPDATE, dimension, area, null);
    }

    public static AreaEditRequestPayload delete(ResourceKey<Level> dimension, UUID areaId) {
        return new AreaEditRequestPayload(EditOperation.DELETE, dimension, null, areaId);
    }
}
