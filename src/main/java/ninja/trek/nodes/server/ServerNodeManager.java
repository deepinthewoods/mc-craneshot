package ninja.trek.nodes.server;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import ninja.trek.nodes.model.AreaInstanceDTO;
import ninja.trek.nodes.model.AreaMovementConfig;
import ninja.trek.nodes.model.CameraNodeDTO;

import java.util.*;

public class ServerNodeManager {
    private static final int MAX_REQUESTS_PER_TICK = 64;
    private static final double MAX_COORD_ABS = 30_000_000.0;
    private static final double MAX_AREA_RADIUS = 8_192.0;
    private static final Set<String> ALLOWED_MOVEMENTS = Set.of("craneshot:static");

    private static final ServerNodeManager INSTANCE = new ServerNodeManager();

    public static ServerNodeManager get() {
        return INSTANCE;
    }

    private final Map<UUID, PlayerSession> sessions = new HashMap<>();
    private final Map<UUID, Integer> rateLimiter = new HashMap<>();

    private ServerNodeManager() {}

    public PlayerSession getSession(ServerPlayer player) {
        return sessions.computeIfAbsent(player.getUUID(), id -> new PlayerSession());
    }

    public boolean isHandshakeComplete(ServerPlayer player) {
        return getSession(player).handshakeComplete;
    }

    public boolean isHandshakeSent(ServerPlayer player) {
        return getSession(player).isHandshakeSent();
    }

    public void markHandshakeSent(ServerPlayer player, boolean provisionalCanEdit) {
        getSession(player).setHandshakeSent(provisionalCanEdit);
    }

    public void markHandshakeComplete(ServerPlayer player, boolean canEdit) {
        getSession(player).setHandshakeComplete(canEdit);
    }

    public boolean canEditOnServer(ServerPlayer player) {
        return getSession(player).canEdit() || isGameMaster(player);
    }

    public void onPlayerDisconnected(ServerPlayer player) {
        sessions.remove(player.getUUID());
        rateLimiter.remove(player.getUUID());
    }

    public void resetRateLimiter() {
        rateLimiter.clear();
    }

    public boolean consumeRequest(ServerPlayer player) {
        UUID id = player.getUUID();
        int count = rateLimiter.getOrDefault(id, 0);
        if (count >= MAX_REQUESTS_PER_TICK) return false;
        rateLimiter.put(id, count + 1);
        return true;
    }

    public boolean hasCreatePermission(ServerPlayer player) {
        // By default only operators can create nodes on the server.
        return isGameMaster(player);
    }

    public boolean hasEditPermission(ServerPlayer player, CameraNodeDTO dto) {
        if (isGameMaster(player)) return true;
        if (dto == null) return false;
        return dto.owner != null && dto.owner.equals(player.getUUID());
    }

    public boolean hasAreaEditPermission(ServerPlayer player, AreaInstanceDTO dto) {
        if (isGameMaster(player)) return true;
        if (dto == null) return false;
        return dto.owner != null && dto.owner.equals(player.getUUID());
    }

    public boolean markChunkStreamed(ServerPlayer player, ResourceKey<Level> dimension, ChunkPos pos) {
        return getSession(player).markChunkStreamed(dimension, pos);
    }

    public void retainStreamedChunks(ServerPlayer player, ResourceKey<Level> dimension, Set<Long> keep) {
        getSession(player).retainStreamed(dimension, keep);
    }

    public boolean markAreasSynced(ServerPlayer player, ResourceKey<Level> dimension) {
        return getSession(player).markAreasSynced(dimension);
    }

    public String validateNodePayload(CameraNodeDTO dto) {
        if (dto.position == null) return "position_missing";
        if (Math.abs(dto.position.x) > MAX_COORD_ABS ||
                Math.abs(dto.position.y) > MAX_COORD_ABS ||
                Math.abs(dto.position.z) > MAX_COORD_ABS) {
            return "position_out_of_bounds";
        }
        if (dto.droneRadius < 0.0 || dto.droneRadius > 512.0) {
            return "invalid_drone_radius";
        }
        return null;
    }

    public String validateAreaPayload(ServerLevel world, AreaInstanceDTO dto) {
        if (dto.center == null) return "center_missing";
        if (Math.abs(dto.center.x) > MAX_COORD_ABS ||
                Math.abs(dto.center.y) > MAX_COORD_ABS ||
                Math.abs(dto.center.z) > MAX_COORD_ABS) {
            return "center_out_of_bounds";
        }
        if (dto.insideRadius < 0.0 || dto.outsideRadius < 0.0) {
            return "invalid_radius";
        }
        if (dto.outsideRadius < dto.insideRadius) {
            return "radius_mismatch";
        }
        if (dto.insideRadius > MAX_AREA_RADIUS || dto.outsideRadius > MAX_AREA_RADIUS) {
            return "radius_too_large";
        }
        if (dto.advanced) {
            if (dto.insideRadii == null || dto.outsideRadii == null) {
                return "missing_advanced_radii";
            }
            if (dto.insideRadii.x < 0 || dto.insideRadii.y < 0 || dto.insideRadii.z < 0) {
                return "invalid_inside_radii";
            }
            if (dto.outsideRadii.x < dto.insideRadii.x
                    || dto.outsideRadii.y < dto.insideRadii.y
                    || dto.outsideRadii.z < dto.insideRadii.z) {
                return "invalid_outside_radii";
            }
            if (dto.outsideRadii.x > MAX_AREA_RADIUS || dto.outsideRadii.y > MAX_AREA_RADIUS || dto.outsideRadii.z > MAX_AREA_RADIUS) {
                return "advanced_radius_too_large";
            }
        }
        Set<String> claimedStates = new HashSet<>();
        var state = CameraNodesState.get(world);
        for (AreaMovementConfig cfg : dto.movements) {
            if (cfg.movementType == null || cfg.movementType.isBlank()) {
                return "movement_type_missing";
            }
            if (!ALLOWED_MOVEMENTS.contains(cfg.movementType)) {
                return "movement_type_forbidden";
            }
            if (cfg.weight < 0.0f || cfg.weight > 10.0f) {
                return "invalid_weight";
            }
            if (cfg.id == null) {
                cfg.id = UUID.randomUUID();
            }
            if (cfg.stateFilters != null) {
                for (String filter : cfg.stateFilters) {
                    if (filter == null || filter.isBlank()) continue;
                    if (!claimedStates.add(filter)) {
                        return "duplicate_state_filter";
                    }
                }
            }
            if (cfg.settings != null) {
                for (var entry : cfg.settings.entrySet()) {
                    String key = entry.getKey();
                    UUID referenced = parseUuid(entry.getValue());
                    if (referenced != null && key != null && key.toLowerCase(Locale.ROOT).contains("node")) {
                        if (state.getNode(world.dimension(), referenced) == null) {
                            return "missing_node_reference";
                        }
                    }
                }
            }
        }
        return null;
    }

    public List<CameraNodeDTO> getChunkNodes(ServerLevel world, ChunkPos pos) {
        return CameraNodesState.get(world).getChunkNodes(world.dimension(), pos);
    }

    public void replaceChunk(ServerLevel world, ChunkPos pos, List<CameraNodeDTO> nodes) {
        CameraNodesState.get(world).replaceChunk(world.dimension(), pos, nodes);
    }

    public void upsertNode(ServerLevel world, ChunkPos pos, CameraNodeDTO dto) {
        CameraNodesState.get(world).upsertNode(world.dimension(), pos, dto);
    }

    public boolean removeNode(ServerLevel world, UUID nodeId) {
        return CameraNodesState.get(world).removeNode(world.dimension(), nodeId);
    }

    public CameraNodeDTO getNode(ServerLevel world, UUID nodeId) {
        return CameraNodesState.get(world).getNode(world.dimension(), nodeId);
    }

    public ChunkPos getNodeChunk(ServerLevel world, UUID nodeId) {
        return CameraNodesState.get(world).getNodeChunk(world.dimension(), nodeId);
    }

    public List<AreaInstanceDTO> getAreas(ServerLevel world) {
        return CameraNodesState.get(world).getAreas(world.dimension());
    }

    public void replaceAreas(ServerLevel world, List<AreaInstanceDTO> areas) {
        CameraNodesState.get(world).replaceAreas(world.dimension(), areas);
    }

    public void upsertArea(ServerLevel world, AreaInstanceDTO dto) {
        if (dto.uuid == null) {
            dto.uuid = UUID.randomUUID();
        }
        CameraNodesState.get(world).upsertArea(world.dimension(), dto);
    }

    public boolean removeArea(ServerLevel world, UUID areaId) {
        return CameraNodesState.get(world).removeArea(world.dimension(), areaId);
    }

    public AreaInstanceDTO getArea(ServerLevel world, UUID areaId) {
        return CameraNodesState.get(world).getArea(world.dimension(), areaId);
    }

    public static ChunkPos chunkPosFromNode(CameraNodeDTO dto) {
        int x = Mth.floor(dto.position.x) >> 4;
        int z = Mth.floor(dto.position.z) >> 4;
        return new ChunkPos(x, z);
    }

    public static ServerLevel resolveWorld(MinecraftServer server, ResourceKey<Level> key) {
        return server.getLevel(key);
    }

    public static class PlayerSession {
        private boolean handshakeSent = false;
        private boolean handshakeComplete = false;
        private boolean canEdit = false;
        private final Map<ResourceKey<Level>, Set<Long>> streamedChunks = new HashMap<>();
        private final Set<ResourceKey<Level>> syncedAreas = new HashSet<>();

        public boolean isHandshakeSent() {
            return handshakeSent;
        }

        public boolean isHandshakeComplete() {
            return handshakeComplete;
        }

        public boolean canEdit() {
            return canEdit;
        }

        private void setHandshakeSent(boolean canEdit) {
            this.handshakeSent = true;
            this.canEdit = canEdit;
        }

        private void setHandshakeComplete(boolean canEdit) {
            this.handshakeComplete = true;
            this.canEdit = canEdit;
            this.streamedChunks.clear();
            this.syncedAreas.clear();
        }

        private boolean markChunkStreamed(ResourceKey<Level> dimension, ChunkPos pos) {
            return streamedChunks.computeIfAbsent(dimension, k -> new HashSet<>()).add(pos.toLong());
        }

        private void retainStreamed(ResourceKey<Level> dimension, Set<Long> keep) {
            Set<Long> set = streamedChunks.get(dimension);
            if (set == null) return;
            set.retainAll(keep);
            if (set.isEmpty()) {
                streamedChunks.remove(dimension);
            }
        }

        private boolean markAreasSynced(ResourceKey<Level> dimension) {
            return syncedAreas.add(dimension);
        }
    }

    private static boolean isGameMaster(ServerPlayer player) {
        return player.permissions() instanceof LevelBasedPermissionSet lps
                && lps.level().isEqualOrHigherThan(PermissionLevel.GAMEMASTERS);
    }

    private static UUID parseUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof String s) {
            try {
                return UUID.fromString(s);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }
}
