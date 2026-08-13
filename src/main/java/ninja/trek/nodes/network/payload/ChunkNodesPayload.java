package ninja.trek.nodes.network.payload;

import ninja.trek.Craneshot;
import ninja.trek.nodes.model.CameraNodeDTO;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

public record ChunkNodesPayload(ResourceKey<Level> dimension, ChunkPos chunk, List<CameraNodeDTO> nodes) implements CustomPacketPayload {
    public static final Type<ChunkNodesPayload> ID = new Type<>(Identifier.fromNamespaceAndPath(Craneshot.MOD_ID, "chunk_nodes"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, ChunkNodesPayload> CODEC = StreamCodec.ofMember(
            ChunkNodesPayload::write,
            ChunkNodesPayload::read
    );

    private ChunkNodesPayload(RegistryFriendlyByteBuf buf) {
        this(
                ResourceKey.create(Registries.DIMENSION, buf.readIdentifier()),
                new ChunkPos(buf.readInt(), buf.readInt()),
                readNodes(buf)
        );
    }

    public ChunkNodesPayload(ResourceKey<Level> dimension, ChunkPos chunk, List<CameraNodeDTO> nodes) {
        this.dimension = dimension;
        this.chunk = chunk;
        this.nodes = List.copyOf(nodes);
    }

    private static ChunkNodesPayload read(RegistryFriendlyByteBuf buf) {
        return new ChunkNodesPayload(buf);
    }

    private static List<CameraNodeDTO> readNodes(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<CameraNodeDTO> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(CameraNodeDTO.read(buf));
        }
        return list;
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeIdentifier(dimension.identifier());
        buf.writeInt(chunk.x());
        buf.writeInt(chunk.z());
        buf.writeVarInt(nodes.size());
        for (CameraNodeDTO dto : nodes) {
            dto.write(buf);
        }
    }

    @Override
    public Type<ChunkNodesPayload> type() {
        return ID;
    }
}
