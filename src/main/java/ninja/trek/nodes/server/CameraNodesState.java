package ninja.trek.nodes.server;

import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import ninja.trek.nodes.model.AreaInstanceDTO;
import ninja.trek.nodes.model.CameraNodeDTO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CameraNodesState extends SavedData {
    public static final Identifier STORAGE_KEY = Identifier.fromNamespaceAndPath("craneshot", "nodes");
    private static final int FORMAT_VERSION = 1;

    private final Map<ResourceKey<net.minecraft.world.level.Level>, Map<Long, LinkedHashMap<UUID, CameraNodeDTO>>> nodesByDimension = new HashMap<>();
    private final Map<ResourceKey<net.minecraft.world.level.Level>, Map<UUID, Long>> nodeIndex = new HashMap<>();
    private final Map<ResourceKey<net.minecraft.world.level.Level>, LinkedHashMap<UUID, AreaInstanceDTO>> areasByDimension = new HashMap<>();

    private static final Codec<CameraNodesState> CODEC = CompoundTag.CODEC.xmap(
            CameraNodesState::fromNbt,
            CameraNodesState::writeNbt
    );

    private static final net.minecraft.world.level.saveddata.SavedDataType<CameraNodesState> TYPE =
        new net.minecraft.world.level.saveddata.SavedDataType<>(
            STORAGE_KEY,
            CameraNodesState::new,
            CODEC,
            null
        );

    public static CameraNodesState get(ServerLevel world) {
        return world.getDataStorage().computeIfAbsent(TYPE);
    }

    public List<CameraNodeDTO> getChunkNodes(ResourceKey<net.minecraft.world.level.Level> dimension, ChunkPos pos) {
        Map<Long, LinkedHashMap<UUID, CameraNodeDTO>> dimMap = nodesByDimension.get(dimension);
        if (dimMap == null) return List.of();
        Map<UUID, CameraNodeDTO> chunk = dimMap.get(pos.pack());
        if (chunk == null) return List.of();
        return new ArrayList<>(chunk.values());
    }

    public void replaceChunk(ResourceKey<net.minecraft.world.level.Level> dimension, ChunkPos pos, List<CameraNodeDTO> nodes) {
        Map<Long, LinkedHashMap<UUID, CameraNodeDTO>> dimMap = getDimensionMap(dimension);
        Map<UUID, Long> index = getIndexMap(dimension);
        long key = pos.pack();
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
        setDirty();
    }

    public void upsertNode(ResourceKey<net.minecraft.world.level.Level> dimension, ChunkPos pos, CameraNodeDTO dto) {
        Map<Long, LinkedHashMap<UUID, CameraNodeDTO>> dimMap = getDimensionMap(dimension);
        Map<UUID, Long> index = getIndexMap(dimension);
        long key = pos.pack();
        LinkedHashMap<UUID, CameraNodeDTO> chunk = dimMap.computeIfAbsent(key, k -> new LinkedHashMap<>());
        chunk.put(dto.uuid, dto);
        index.put(dto.uuid, key);
        if (chunk.isEmpty()) {
            dimMap.remove(key);
        }
        setDirty();
    }

    public boolean removeNode(ResourceKey<net.minecraft.world.level.Level> dimension, UUID nodeId) {
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
            setDirty();
        }
        return removed != null;
    }

    public CameraNodeDTO getNode(ResourceKey<net.minecraft.world.level.Level> dimension, UUID nodeId) {
        Map<UUID, Long> index = getIndexMap(dimension);
        Long key = index.get(nodeId);
        if (key == null) return null;
        Map<Long, LinkedHashMap<UUID, CameraNodeDTO>> dimMap = getDimensionMap(dimension);
        Map<UUID, CameraNodeDTO> chunk = dimMap.get(key);
        if (chunk == null) return null;
        return chunk.get(nodeId);
    }

    public ChunkPos getNodeChunk(ResourceKey<net.minecraft.world.level.Level> dimension, UUID nodeId) {
        Map<UUID, Long> index = getIndexMap(dimension);
        Long key = index.get(nodeId);
        if (key == null) return null;
        return ChunkPos.unpack(key);
    }

    public List<AreaInstanceDTO> getAreas(ResourceKey<net.minecraft.world.level.Level> dimension) {
        LinkedHashMap<UUID, AreaInstanceDTO> map = areasByDimension.get(dimension);
        if (map == null) return List.of();
        return new ArrayList<>(map.values());
    }

    public void replaceAreas(ResourceKey<net.minecraft.world.level.Level> dimension, List<AreaInstanceDTO> areas) {
        LinkedHashMap<UUID, AreaInstanceDTO> map = getAreaMap(dimension);
        map.clear();
        for (AreaInstanceDTO dto : areas) {
            if (dto != null && dto.uuid != null) {
                map.put(dto.uuid, dto);
            }
        }
        if (map.isEmpty()) {
            areasByDimension.remove(dimension);
        }
        setDirty();
    }

    public void upsertArea(ResourceKey<net.minecraft.world.level.Level> dimension, AreaInstanceDTO dto) {
        if (dto == null || dto.uuid == null) return;
        LinkedHashMap<UUID, AreaInstanceDTO> map = getAreaMap(dimension);
        map.put(dto.uuid, dto);
        setDirty();
    }

    public boolean removeArea(ResourceKey<net.minecraft.world.level.Level> dimension, UUID areaId) {
        LinkedHashMap<UUID, AreaInstanceDTO> map = areasByDimension.get(dimension);
        if (map == null) return false;
        AreaInstanceDTO removed = map.remove(areaId);
        if (map.isEmpty()) {
            areasByDimension.remove(dimension);
        }
        if (removed != null) {
            setDirty();
            return true;
        }
        return false;
    }

    public AreaInstanceDTO getArea(ResourceKey<net.minecraft.world.level.Level> dimension, UUID areaId) {
        LinkedHashMap<UUID, AreaInstanceDTO> map = areasByDimension.get(dimension);
        if (map == null) return null;
        return map.get(areaId);
    }

    private CompoundTag writeNbt() {
        CompoundTag nbt = new CompoundTag();
        nbt.putInt("formatVersion", FORMAT_VERSION);
        ListTag dims = new ListTag();
        java.util.Set<ResourceKey<net.minecraft.world.level.Level>> dimensionKeys = new java.util.HashSet<>(nodesByDimension.keySet());
        dimensionKeys.addAll(areasByDimension.keySet());
        for (ResourceKey<net.minecraft.world.level.Level> dimension : dimensionKeys) {
            CompoundTag dimTag = new CompoundTag();
            dimTag.putString("dimension", dimension.identifier().toString());
            ListTag chunks = new ListTag();
            Map<Long, LinkedHashMap<UUID, CameraNodeDTO>> dimNodes = nodesByDimension.get(dimension);
            if (dimNodes != null) {
                for (var chunkEntry : dimNodes.entrySet()) {
                    CompoundTag chunkTag = new CompoundTag();
                    chunkTag.putLong("chunk", chunkEntry.getKey());
                    ListTag nodes = new ListTag();
                    for (CameraNodeDTO dto : chunkEntry.getValue().values()) {
                        nodes.add(dto.toNbt());
                    }
                    chunkTag.put("nodes", nodes);
                    chunks.add(chunkTag);
                }
            }
            dimTag.put("chunks", chunks);

            LinkedHashMap<UUID, AreaInstanceDTO> dimAreas = areasByDimension.get(dimension);
            if (dimAreas != null && !dimAreas.isEmpty()) {
                ListTag areas = new ListTag();
                for (AreaInstanceDTO dto : dimAreas.values()) {
                    areas.add(dto.toNbt());
                }
                dimTag.put("areas", areas);
            }

            dims.add(dimTag);
        }
        nbt.put("dimensions", dims);
        return nbt;
    }

    private static CameraNodesState fromNbt(CompoundTag nbt) {
        CameraNodesState state = new CameraNodesState();
        nbt.getList("dimensions").ifPresent(dimList -> {
            for (Tag element : dimList) {
                if (!(element instanceof CompoundTag dimTag)) continue;
                String dimId = dimTag.getString("dimension").orElse(null);
                if (dimId == null) continue;
                ResourceKey<net.minecraft.world.level.Level> dimension = parseDimension(dimId);
                if (dimension == null) continue;
                Map<Long, LinkedHashMap<UUID, CameraNodeDTO>> dimMap = state.getDimensionMap(dimension);
                Map<UUID, Long> index = state.getIndexMap(dimension);
                dimTag.getList("chunks").ifPresent(chunkList -> {
                    for (Tag chunkElement : chunkList) {
                        if (!(chunkElement instanceof CompoundTag chunkTag)) continue;
                        Long chunkKeyOpt = chunkTag.getLong("chunk").orElse(null);
                        if (chunkKeyOpt == null) continue;
                        long chunkKey = chunkKeyOpt;
                        LinkedHashMap<UUID, CameraNodeDTO> nodeMap = new LinkedHashMap<>();
                        chunkTag.getList("nodes").ifPresent(nodeList -> {
                            for (Tag nodeElement : nodeList) {
                                if (!(nodeElement instanceof CompoundTag nodeTag)) continue;
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
                dimTag.getList("areas").ifPresent(areaList -> {
                    LinkedHashMap<UUID, AreaInstanceDTO> areaMap = state.getAreaMap(dimension);
                    for (Tag areaElement : areaList) {
                        if (!(areaElement instanceof CompoundTag areaTag)) continue;
                        AreaInstanceDTO dto = AreaInstanceDTO.fromNbt(areaTag);
                        if (dto.uuid == null) {
                            dto.uuid = UUID.randomUUID();
                        }
                        areaMap.put(dto.uuid, dto);
                    }
                    if (areaMap.isEmpty()) {
                        state.areasByDimension.remove(dimension);
                    }
                });
            }
        });
        return state;
    }

    private Map<Long, LinkedHashMap<UUID, CameraNodeDTO>> getDimensionMap(ResourceKey<net.minecraft.world.level.Level> dimension) {
        return nodesByDimension.computeIfAbsent(dimension, k -> new HashMap<>());
    }

    private Map<UUID, Long> getIndexMap(ResourceKey<net.minecraft.world.level.Level> dimension) {
        return nodeIndex.computeIfAbsent(dimension, k -> new HashMap<>());
    }

    private Map<UUID, CameraNodeDTO> getChunkMap(ResourceKey<net.minecraft.world.level.Level> dimension, ChunkPos pos) {
        long key = pos.pack();
        Map<Long, LinkedHashMap<UUID, CameraNodeDTO>> dimMap = getDimensionMap(dimension);
        return dimMap.computeIfAbsent(key, k -> new LinkedHashMap<>());
    }

    private LinkedHashMap<UUID, AreaInstanceDTO> getAreaMap(ResourceKey<net.minecraft.world.level.Level> dimension) {
        return areasByDimension.computeIfAbsent(dimension, k -> new LinkedHashMap<>());
    }

    private static ResourceKey<net.minecraft.world.level.Level> parseDimension(String id) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null) return null;
        return ResourceKey.create(Registries.DIMENSION, identifier);
    }
}
