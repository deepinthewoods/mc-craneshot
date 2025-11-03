package ninja.trek.nodes.network.payload;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import ninja.trek.Craneshot;
import ninja.trek.nodes.model.AreaInstanceDTO;

import java.util.UUID;

public record AreaEditRequestPayload(
        EditOperation operation,
        RegistryKey<World> dimension,
        AreaInstanceDTO areaData,
        UUID areaIdForDelete
) implements CustomPayload {
    public static final Id<AreaEditRequestPayload> ID = new Id<>(Identifier.of(Craneshot.MOD_ID, "area_edit_request"));

    public static final PacketCodec<RegistryByteBuf, AreaEditRequestPayload> CODEC = PacketCodec.of(
            AreaEditRequestPayload::write,
            AreaEditRequestPayload::read
    );

    private AreaEditRequestPayload(RegistryByteBuf buf) {
        this(
                buf.readEnumConstant(EditOperation.class),
                RegistryKey.of(RegistryKeys.WORLD, buf.readIdentifier()),
                readAreaData(buf),
                readAreaId(buf)
        );
    }

    private static AreaInstanceDTO readAreaData(RegistryByteBuf buf) {
        if (buf.readBoolean()) {
            return AreaInstanceDTO.read(buf);
        }
        return null;
    }

    private static UUID readAreaId(RegistryByteBuf buf) {
        if (buf.readBoolean()) {
            return buf.readUuid();
        }
        return null;
    }

    private static AreaEditRequestPayload read(RegistryByteBuf buf) {
        return new AreaEditRequestPayload(buf);
    }

    private void write(RegistryByteBuf buf) {
        buf.writeEnumConstant(operation);
        buf.writeIdentifier(dimension.getValue());
        boolean hasAreaData = areaData != null;
        buf.writeBoolean(hasAreaData);
        if (hasAreaData) {
            areaData.write(buf);
        }
        boolean hasAreaId = areaIdForDelete != null;
        buf.writeBoolean(hasAreaId);
        if (hasAreaId) {
            buf.writeUuid(areaIdForDelete);
        }
    }

    @Override
    public Id<AreaEditRequestPayload> getId() {
        return ID;
    }

    public enum EditOperation {
        CREATE, UPDATE, DELETE
    }

    public static AreaEditRequestPayload create(RegistryKey<World> dimension, AreaInstanceDTO area) {
        return new AreaEditRequestPayload(EditOperation.CREATE, dimension, area, null);
    }

    public static AreaEditRequestPayload update(RegistryKey<World> dimension, AreaInstanceDTO area) {
        return new AreaEditRequestPayload(EditOperation.UPDATE, dimension, area, null);
    }

    public static AreaEditRequestPayload delete(RegistryKey<World> dimension, UUID areaId) {
        return new AreaEditRequestPayload(EditOperation.DELETE, dimension, null, areaId);
    }
}
