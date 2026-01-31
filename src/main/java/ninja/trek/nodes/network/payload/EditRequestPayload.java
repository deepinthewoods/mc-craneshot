package ninja.trek.nodes.network.payload;

import ninja.trek.Craneshot;
import ninja.trek.nodes.model.CameraNodeDTO;

import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

public record EditRequestPayload(
        EditOperation operation,
        ResourceKey<Level> dimension,
        CameraNodeDTO nodeData,
        UUID nodeIdForDelete
) implements CustomPacketPayload {
    public static final Type<EditRequestPayload> ID = new Type<>(Identifier.fromNamespaceAndPath(Craneshot.MOD_ID, "edit_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EditRequestPayload> CODEC = StreamCodec.ofMember(
            EditRequestPayload::write,
            EditRequestPayload::read
    );

    private EditRequestPayload(RegistryFriendlyByteBuf buf) {
        this(
                buf.readEnum(EditOperation.class),
                ResourceKey.create(Registries.DIMENSION, buf.readIdentifier()),
                readNodeData(buf),
                readNodeId(buf)
        );
    }

    private static CameraNodeDTO readNodeData(RegistryFriendlyByteBuf buf) {
        // For CREATE and UPDATE operations
        if (buf.readBoolean()) {
            return CameraNodeDTO.read(buf);
        }
        return null;
    }

    private static UUID readNodeId(RegistryFriendlyByteBuf buf) {
        // For DELETE operation
        if (buf.readBoolean()) {
            return buf.readUUID();
        }
        return null;
    }

    private static EditRequestPayload read(RegistryFriendlyByteBuf buf) {
        return new EditRequestPayload(buf);
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeEnum(operation);
        buf.writeIdentifier(dimension.identifier());
        boolean hasNodeData = nodeData != null;
        buf.writeBoolean(hasNodeData);
        if (hasNodeData) {
            nodeData.write(buf);
        }
        boolean hasNodeId = nodeIdForDelete != null;
        buf.writeBoolean(hasNodeId);
        if (hasNodeId) {
            buf.writeUUID(nodeIdForDelete);
        }
    }

    @Override
    public Type<EditRequestPayload> type() {
        return ID;
    }

    public enum EditOperation {
        CREATE, UPDATE, DELETE
    }

    // Factory methods for convenience
    public static EditRequestPayload create(ResourceKey<Level> dimension, CameraNodeDTO nodeData) {
        return new EditRequestPayload(EditOperation.CREATE, dimension, nodeData, null);
    }

    public static EditRequestPayload update(ResourceKey<Level> dimension, CameraNodeDTO nodeData) {
        return new EditRequestPayload(EditOperation.UPDATE, dimension, nodeData, null);
    }

    public static EditRequestPayload delete(ResourceKey<Level> dimension, UUID nodeId) {
        return new EditRequestPayload(EditOperation.DELETE, dimension, null, nodeId);
    }
}
