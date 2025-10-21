package ninja.trek.nodes.network.payload;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import ninja.trek.Craneshot;
import ninja.trek.nodes.model.CameraNodeDTO;

import java.util.ArrayList;
import java.util.List;

public record ChunkNodesPayload(RegistryKey<World> dimension, ChunkPos chunk, List<CameraNodeDTO> nodes) implements CustomPayload {
    public static final Id<ChunkNodesPayload> ID = new Id<>(Identifier.of(Craneshot.MOD_ID, "chunk_nodes"));
    
    public static final PacketCodec<RegistryByteBuf, ChunkNodesPayload> CODEC = PacketCodec.of(
            ChunkNodesPayload::write,
            ChunkNodesPayload::read
    );

    private ChunkNodesPayload(RegistryByteBuf buf) {
        this(
                RegistryKey.of(RegistryKeys.WORLD, buf.readIdentifier()),
                new ChunkPos(buf.readInt(), buf.readInt()),
                readNodes(buf)
        );
    }

    public ChunkNodesPayload(RegistryKey<World> dimension, ChunkPos chunk, List<CameraNodeDTO> nodes) {
        this.dimension = dimension;
        this.chunk = chunk;
        this.nodes = List.copyOf(nodes);
    }

    private static ChunkNodesPayload read(RegistryByteBuf buf) {
        return new ChunkNodesPayload(buf);
    }

    private static List<CameraNodeDTO> readNodes(RegistryByteBuf buf) {
        int size = buf.readVarInt();
        List<CameraNodeDTO> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(CameraNodeDTO.read(buf));
        }
        return list;
    }

    private void write(RegistryByteBuf buf) {
        buf.writeIdentifier(dimension.getValue());
        buf.writeInt(chunk.x);
        buf.writeInt(chunk.z);
        buf.writeVarInt(nodes.size());
        for (CameraNodeDTO dto : nodes) {
            dto.write(buf);
        }
    }

    @Override
    public Id<ChunkNodesPayload> getId() {
        return ID;
    }
}
