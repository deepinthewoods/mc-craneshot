package ninja.trek.nodes.server;

import com.mojang.serialization.Codec;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import ninja.trek.nodes.model.CameraNodeDTO;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public class CameraNodesState extends PersistentState {
    public static final String STORAGE_KEY = "craneshot_nodes";
    private static final int FORMAT_VERSION = 1;

    private final Map<RegistryKey<net.minecraft.world.World>, Map<Long, LinkedHashMap<UUID, CameraNodeDTO>>> nodesByDimension = new HashMap<>();
    private final Map<RegistryKey<net.minecraft.world.World>, Map<UUID, Long>> nodeIndex = new HashMap<>();

    // Create a Codec that wraps our NBT-based serialization
    private static final Codec<CameraNodesState> CODEC = Codec.unit(() -> {
        // This codec is only used for creating empty instances
        // The actual serialization still uses writeNbt/fromNbt through PersistentState
        return new CameraNodesState();
    });

    private static final net.minecraft.world.PersistentStateType<CameraNodesState> TYPE =
        new net.minecraft.world.PersistentStateType<>(
            STORAGE_KEY,
            CameraNodesState::new,
            CODEC,
            null
        );

    public static CameraNodesState get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(TYPE);
    }

    public List<CameraNodeDTO> getChunkNodes(RegistryKey<net.minecraft.world.World> dimension, ChunkPos pos) {
        Map<Long, LinkedHashMap<UUID, CameraNodeDTO>> dimMap = nodesByDimension.get(dimension);
        if (dimMap == null) return List.of();
        Map<UUID, CameraNodeDTO> chunk = dimMap.get(pos.toLong());
        if (chunk == null) return List.of();
        return new ArrayList<>(chunk.values());
    }

    public void replaceChunk(RegistryKey<net.minecraft.world.World> dimension, ChunkPos pos, List<CameraNodeDTO> nodes) {
        Map<Long, LinkedHashMap<UUID, CameraNodeDTO>> dimMap = getDimensionMap(dimension);
        Map<UUID, Long> index = getIndexMap(dimension);
        long key = pos.toLong();
        Map<UUID, CameraNodeDTO> existing = dimMap.remove(key);
        if (existing != null) {
            for (UUID id : existing.keySet()) {
                index.remove(id);
            }
        }
        LinkedHashMap<UUID, CameraNodeDTO> map = new LinkedHashMap<>();
        for (CameraNodeDTO dto : nodes) {
            map.put(dto.uuid, dto);
            index.put(dto.uuid, key);
        }
        if (!map.isEmpty()) {
            dimMap.put(key, map);
        }
        markDirty();
    }

    public void upsertNode(RegistryKey<net.minecraft.world.World> dimension, ChunkPos pos, CameraNodeDTO dto) {
        Map<Long, LinkedHashMap<UUID, CameraNodeDTO>> dimMap = getDimensionMap(dimension);
        Map<UUID, Long> index = getIndexMap(dimension);
        long key = pos.toLong();
        LinkedHashMap<UUID, CameraNodeDTO> chunk = dimMap.computeIfAbsent(key, k -> new LinkedHashMap<>());
        chunk.put(dto.uuid, dto);
        index.put(dto.uuid, key);
        if (chunk.isEmpty()) {
            dimMap.remove(key);
        }
        markDirty();
    }

    public boolean removeNode(RegistryKey<net.minecraft.world.World> dimension, UUID nodeId) {
        Map<UUID, Long> index = getIndexMap(dimension);
        Long key = index.remove(nodeId);
        if (key == null) return false;
        Map<Long, LinkedHashMap<UUID, CameraNodeDTO>> dimMap = getDimensionMap(dimension);
        LinkedHashMap<UUID, CameraNodeDTO> chunk = dimMap.get(key);
        if (chunk == null) return false;
        CameraNodeDTO removed = chunk.remove(nodeId);
        if (chunk.isEmpty()) {
            dimMap.remove(key);
        }
        if (removed != null) {
            markDirty();
        }
        return removed != null;
    }

    public CameraNodeDTO getNode(RegistryKey<net.minecraft.world.World> dimension, UUID nodeId) {
        Map<UUID, Long> index = getIndexMap(dimension);
        Long key = index.get(nodeId);
        if (key == null) return null;
        Map<Long, LinkedHashMap<UUID, CameraNodeDTO>> dimMap = getDimensionMap(dimension);
        Map<UUID, CameraNodeDTO> chunk = dimMap.get(key);
        if (chunk == null) return null;
        return chunk.get(nodeId);
    }

    public ChunkPos getNodeChunk(RegistryKey<net.minecraft.world.World> dimension, UUID nodeId) {
        Map<UUID, Long> index = getIndexMap(dimension);
        Long key = index.get(nodeId);
        if (key == null) return null;
        return new ChunkPos(key);
    }

    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        nbt.putInt("formatVersion", FORMAT_VERSION);
        NbtList dims = new NbtList();
        for (var dimEntry : nodesByDimension.entrySet()) {
            NbtCompound dimTag = new NbtCompound();
            dimTag.putString("dimension", dimEntry.getKey().getValue().toString());
            NbtList chunks = new NbtList();
            for (var chunkEntry : dimEntry.getValue().entrySet()) {
                NbtCompound chunkTag = new NbtCompound();
                chunkTag.putLong("chunk", chunkEntry.getKey());
                NbtList nodes = new NbtList();
                for (CameraNodeDTO dto : chunkEntry.getValue().values()) {
                    nodes.add(dto.toNbt());
                }
                chunkTag.put("nodes", nodes);
                chunks.add(chunkTag);
            }
            dimTag.put("chunks", chunks);
            dims.add(dimTag);
        }
        nbt.put("dimensions", dims);
        return nbt;
    }

    private static CameraNodesState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        CameraNodesState state = new CameraNodesState();
        nbt.getList("dimensions").ifPresent(dimList -> {
            for (NbtElement element : dimList) {
                if (!(element instanceof NbtCompound dimTag)) continue;
                String dimId = dimTag.getString("dimension").orElse(null);
                if (dimId == null) continue;
                RegistryKey<net.minecraft.world.World> dimension = parseDimension(dimId);
                if (dimension == null) continue;
                Map<Long, LinkedHashMap<UUID, CameraNodeDTO>> dimMap = state.getDimensionMap(dimension);
                Map<UUID, Long> index = state.getIndexMap(dimension);
                dimTag.getList("chunks").ifPresent(chunkList -> {
                    for (NbtElement chunkElement : chunkList) {
                        if (!(chunkElement instanceof NbtCompound chunkTag)) continue;
                        Long chunkKeyOpt = chunkTag.getLong("chunk").orElse(null);
                        if (chunkKeyOpt == null) continue;
                        long chunkKey = chunkKeyOpt;
                        LinkedHashMap<UUID, CameraNodeDTO> nodeMap = new LinkedHashMap<>();
                        chunkTag.getList("nodes").ifPresent(nodeList -> {
                            for (NbtElement nodeElement : nodeList) {
                                if (!(nodeElement instanceof NbtCompound nodeTag)) continue;
                                CameraNodeDTO dto = CameraNodeDTO.fromNbt(nodeTag);
                                nodeMap.put(dto.uuid, dto);
                                index.put(dto.uuid, chunkKey);
                            }
                        });
                        if (!nodeMap.isEmpty()) {
                            dimMap.put(chunkKey, nodeMap);
                        }
                    }
                });
            }
        });
        return state;
    }

    private Map<Long, LinkedHashMap<UUID, CameraNodeDTO>> getDimensionMap(RegistryKey<net.minecraft.world.World> dimension) {
        return nodesByDimension.computeIfAbsent(dimension, k -> new HashMap<>());
    }

    private Map<UUID, Long> getIndexMap(RegistryKey<net.minecraft.world.World> dimension) {
        return nodeIndex.computeIfAbsent(dimension, k -> new HashMap<>());
    }

    private Map<UUID, CameraNodeDTO> getChunkMap(RegistryKey<net.minecraft.world.World> dimension, ChunkPos pos) {
        long key = pos.toLong();
        Map<Long, LinkedHashMap<UUID, CameraNodeDTO>> dimMap = getDimensionMap(dimension);
        return dimMap.computeIfAbsent(key, k -> new LinkedHashMap<>());
    }

    private static RegistryKey<net.minecraft.world.World> parseDimension(String id) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null) return null;
        return RegistryKey.of(RegistryKeys.WORLD, identifier);
    }
}
