package ninja.trek.nodes.network.payload;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import ninja.trek.Craneshot;
import ninja.trek.nodes.model.CameraNodeDTO;

import java.util.UUID;

public record EditRequestPayload(
        EditOperation operation,
        RegistryKey<World> dimension,
        CameraNodeDTO nodeData,
        UUID nodeIdForDelete
) implements CustomPayload {
    public static final Id<EditRequestPayload> ID = new Id<>(Identifier.of(Craneshot.MOD_ID, "edit_request"));

    public static final PacketCodec<RegistryByteBuf, EditRequestPayload> CODEC = PacketCodec.of(
            EditRequestPayload::write,
            EditRequestPayload::read
    );

    private EditRequestPayload(RegistryByteBuf buf) {
        this(
                buf.readEnumConstant(EditOperation.class),
                RegistryKey.of(RegistryKeys.WORLD, buf.readIdentifier()),
                readNodeData(buf),
                readNodeId(buf)
        );
    }

    private static CameraNodeDTO readNodeData(RegistryByteBuf buf) {
        // For CREATE and UPDATE operations
        if (buf.readBoolean()) {
            return CameraNodeDTO.read(buf);
        }
        return null;
    }

    private static UUID readNodeId(RegistryByteBuf buf) {
        // For DELETE operation
        if (buf.readBoolean()) {
            return buf.readUuid();
        }
        return null;
    }

    private static EditRequestPayload read(RegistryByteBuf buf) {
        return new EditRequestPayload(buf);
    }

    private void write(RegistryByteBuf buf) {
        buf.writeEnumConstant(operation);
        buf.writeIdentifier(dimension.getValue());
        boolean hasNodeData = nodeData != null;
        buf.writeBoolean(hasNodeData);
        if (hasNodeData) {
            nodeData.write(buf);
        }
        boolean hasNodeId = nodeIdForDelete != null;
        buf.writeBoolean(hasNodeId);
        if (hasNodeId) {
            buf.writeUuid(nodeIdForDelete);
        }
    }

    @Override
    public Id<EditRequestPayload> getId() {
        return ID;
    }

    public enum EditOperation {
        CREATE, UPDATE, DELETE
    }

    // Factory methods for convenience
    public static EditRequestPayload create(RegistryKey<World> dimension, CameraNodeDTO nodeData) {
        return new EditRequestPayload(EditOperation.CREATE, dimension, nodeData, null);
    }

    public static EditRequestPayload update(RegistryKey<World> dimension, CameraNodeDTO nodeData) {
        return new EditRequestPayload(EditOperation.UPDATE, dimension, nodeData, null);
    }

    public static EditRequestPayload delete(RegistryKey<World> dimension, UUID nodeId) {
        return new EditRequestPayload(EditOperation.DELETE, dimension, null, nodeId);
    }
}
