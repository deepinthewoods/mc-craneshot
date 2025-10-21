package ninja.trek.nodes.network.payload;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import ninja.trek.Craneshot;
import ninja.trek.nodes.model.CameraNodeDTO;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record NodesDeltaPayload(
        RegistryKey<World> dimension,
        ChunkPos chunk,
        List<NodeOperation> operations
) implements CustomPayload {
    public static final Id<NodesDeltaPayload> ID = new Id<>(Identifier.of(Craneshot.MOD_ID, "nodes_delta"));

    public static final PacketCodec<RegistryByteBuf, NodesDeltaPayload> CODEC = PacketCodec.of(
            NodesDeltaPayload::write,
            NodesDeltaPayload::read
    );

    private NodesDeltaPayload(RegistryByteBuf buf) {
        this(
                RegistryKey.of(RegistryKeys.WORLD, buf.readIdentifier()),
                new ChunkPos(buf.readInt(), buf.readInt()),
                readOperations(buf)
        );
    }

    private static NodesDeltaPayload read(RegistryByteBuf buf) {
        return new NodesDeltaPayload(buf);
    }

    private static List<NodeOperation> readOperations(RegistryByteBuf buf) {
        int count = buf.readVarInt();
        List<NodeOperation> ops = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ops.add(NodeOperation.read(buf));
        }
        return ops;
    }

    private void write(RegistryByteBuf buf) {
        buf.writeIdentifier(dimension.getValue());
        buf.writeInt(chunk.x);
        buf.writeInt(chunk.z);
        buf.writeVarInt(operations.size());
        for (NodeOperation op : operations) {
            op.write(buf);
        }
    }

    @Override
    public Id<NodesDeltaPayload> getId() {
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
        public static NodeOperation read(RegistryByteBuf buf) {
            OperationType type = buf.readEnumConstant(OperationType.class);
            UUID nodeId = buf.readUuid();
            Optional<CameraNodeDTO> nodeData;
            if (type == OperationType.ADD || type == OperationType.UPDATE) {
                nodeData = Optional.of(CameraNodeDTO.read(buf));
            } else {
                nodeData = Optional.empty();
            }
            return new NodeOperation(type, nodeId, nodeData);
        }

        public void write(RegistryByteBuf buf) {
            buf.writeEnumConstant(type);
            buf.writeUuid(nodeId);
            if (type == OperationType.ADD || type == OperationType.UPDATE) {
                nodeData.ifPresent(dto -> dto.write(buf));
            }
        }
    }
}
