package ninja.trek.nodes.network.payload;

import ninja.trek.Craneshot;
import ninja.trek.nodes.model.CameraNodeDTO;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

public record NodesDeltaPayload(
        ResourceKey<Level> dimension,
        ChunkPos chunk,
        List<NodeOperation> operations
) implements CustomPacketPayload {
    public static final Type<NodesDeltaPayload> ID = new Type<>(Identifier.fromNamespaceAndPath(Craneshot.MOD_ID, "nodes_delta"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NodesDeltaPayload> CODEC = StreamCodec.ofMember(
            NodesDeltaPayload::write,
            NodesDeltaPayload::read
    );

    private NodesDeltaPayload(RegistryFriendlyByteBuf buf) {
        this(
                ResourceKey.create(Registries.DIMENSION, buf.readIdentifier()),
                new ChunkPos(buf.readInt(), buf.readInt()),
                readOperations(buf)
        );
    }

    private static NodesDeltaPayload read(RegistryFriendlyByteBuf buf) {
        return new NodesDeltaPayload(buf);
    }

    private static List<NodeOperation> readOperations(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<NodeOperation> ops = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ops.add(NodeOperation.read(buf));
        }
        return ops;
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeIdentifier(dimension.identifier());
        buf.writeInt(chunk.x);
        buf.writeInt(chunk.z);
        buf.writeVarInt(operations.size());
        for (NodeOperation op : operations) {
            op.write(buf);
        }
    }

    @Override
    public Type<NodesDeltaPayload> type() {
        return ID;
    }

    public enum OperationType {
        ADD, UPDATE, REMOVE
    }

    public record NodeOperation(
            OperationType type,
            UUID nodeId,
            Optional<CameraNodeDTO> nodeData
    ) {
        public static NodeOperation read(RegistryFriendlyByteBuf buf) {
            OperationType type = buf.readEnum(OperationType.class);
            UUID nodeId = buf.readUUID();
            Optional<CameraNodeDTO> nodeData;
            if (type == OperationType.ADD || type == OperationType.UPDATE) {
                nodeData = Optional.of(CameraNodeDTO.read(buf));
            } else {
                nodeData = Optional.empty();
            }
            return new NodeOperation(type, nodeId, nodeData);
        }

        public void write(RegistryFriendlyByteBuf buf) {
            buf.writeEnum(type);
            buf.writeUUID(nodeId);
            if (type == OperationType.ADD || type == OperationType.UPDATE) {
                nodeData.ifPresent(dto -> dto.write(buf));
            }
        }
    }
}
