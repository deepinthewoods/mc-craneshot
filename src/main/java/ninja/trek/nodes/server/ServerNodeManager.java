package ninja.trek.nodes.server;

import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import ninja.trek.nodes.model.CameraNodeDTO;

import java.util.*;

public class ServerNodeManager {
    private static final int MAX_REQUESTS_PER_TICK = 64;
    private static final double MAX_COORD_ABS = 30_000_000.0;

    private static final ServerNodeManager INSTANCE = new ServerNodeManager();

    public static ServerNodeManager get() {
        return INSTANCE;
    }

    private final Map<UUID, PlayerSession> sessions = new HashMap<>();
    private final Map<UUID, Integer> rateLimiter = new HashMap<>();

    private ServerNodeManager() {}

    public PlayerSession getSession(ServerPlayerEntity player) {
        return sessions.computeIfAbsent(player.getUuid(), id -> new PlayerSession());
    }

    public boolean isHandshakeComplete(ServerPlayerEntity player) {
        return getSession(player).handshakeComplete;
    }

    public boolean isHandshakeSent(ServerPlayerEntity player) {
        return getSession(player).isHandshakeSent();
    }

    public void markHandshakeSent(ServerPlayerEntity player, boolean provisionalCanEdit) {
        getSession(player).setHandshakeSent(provisionalCanEdit);
    }

    public void markHandshakeComplete(ServerPlayerEntity player, boolean canEdit) {
        getSession(player).setHandshakeComplete(canEdit);
    }

    public boolean canEditOnServer(ServerPlayerEntity player) {
        return getSession(player).canEdit() || player.hasPermissionLevel(2);
    }

    public void onPlayerDisconnected(ServerPlayerEntity player) {
        sessions.remove(player.getUuid());
        rateLimiter.remove(player.getUuid());
    }

    public void resetRateLimiter() {
        rateLimiter.clear();
    }

    public boolean consumeRequest(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        int count = rateLimiter.getOrDefault(id, 0);
        if (count >= MAX_REQUESTS_PER_TICK) return false;
        rateLimiter.put(id, count + 1);
        return true;
    }

    public boolean hasCreatePermission(ServerPlayerEntity player) {
        // By default only operators can create nodes on the server.
        return player.hasPermissionLevel(2);
    }

    public boolean hasEditPermission(ServerPlayerEntity player, CameraNodeDTO dto) {
        if (player.hasPermissionLevel(2)) return true;
        if (dto == null) return false;
        return dto.owner != null && dto.owner.equals(player.getUuid());
    }

    public boolean markChunkStreamed(ServerPlayerEntity player, RegistryKey<World> dimension, ChunkPos pos) {
        return getSession(player).markChunkStreamed(dimension, pos);
    }

    public void retainStreamedChunks(ServerPlayerEntity player, RegistryKey<World> dimension, Set<Long> keep) {
        getSession(player).retainStreamed(dimension, keep);
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
        if (dto.areas != null) {
            for (int i = 0; i < dto.areas.size(); i++) {
                var area = dto.areas.get(i);
                if (area.minRadius < 0.0 || area.maxRadius < 0.0) {
                    return "negative_radius";
                }
                if (area.maxRadius < area.minRadius) {
                    return "max_lt_min";
                }
                if (area.advanced && area.minRadii != null && area.maxRadii != null) {
                    if (area.minRadii.x < 0 || area.minRadii.y < 0 || area.minRadii.z < 0) {
                        return "negative_min_axis";
                    }
                    if (area.maxRadii.x < area.minRadii.x ||
                            area.maxRadii.y < area.minRadii.y ||
                            area.maxRadii.z < area.minRadii.z) {
                        return "axis_max_lt_min";
                    }
                }
            }
        }
        return null;
    }

    public List<CameraNodeDTO> getChunkNodes(ServerWorld world, ChunkPos pos) {
        return CameraNodesState.get(world).getChunkNodes(world.getRegistryKey(), pos);
    }

    public void replaceChunk(ServerWorld world, ChunkPos pos, List<CameraNodeDTO> nodes) {
        CameraNodesState.get(world).replaceChunk(world.getRegistryKey(), pos, nodes);
    }

    public void upsertNode(ServerWorld world, ChunkPos pos, CameraNodeDTO dto) {
        CameraNodesState.get(world).upsertNode(world.getRegistryKey(), pos, dto);
    }

    public boolean removeNode(ServerWorld world, UUID nodeId) {
        return CameraNodesState.get(world).removeNode(world.getRegistryKey(), nodeId);
    }

    public CameraNodeDTO getNode(ServerWorld world, UUID nodeId) {
        return CameraNodesState.get(world).getNode(world.getRegistryKey(), nodeId);
    }

    public ChunkPos getNodeChunk(ServerWorld world, UUID nodeId) {
        return CameraNodesState.get(world).getNodeChunk(world.getRegistryKey(), nodeId);
    }

    public static ChunkPos chunkPosFromNode(CameraNodeDTO dto) {
        int x = MathHelper.floor(dto.position.x) >> 4;
        int z = MathHelper.floor(dto.position.z) >> 4;
        return new ChunkPos(x, z);
    }

    public static ServerWorld resolveWorld(MinecraftServer server, RegistryKey<World> key) {
        return server.getWorld(key);
    }

    public static class PlayerSession {
        private boolean handshakeSent = false;
        private boolean handshakeComplete = false;
        private boolean canEdit = false;
        private final Map<RegistryKey<World>, Set<Long>> streamedChunks = new HashMap<>();

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
        }

        private boolean markChunkStreamed(RegistryKey<World> dimension, ChunkPos pos) {
            return streamedChunks.computeIfAbsent(dimension, k -> new HashSet<>()).add(pos.toLong());
        }

        private void retainStreamed(RegistryKey<World> dimension, Set<Long> keep) {
            Set<Long> set = streamedChunks.get(dimension);
            if (set == null) return;
            set.retainAll(keep);
            if (set.isEmpty()) {
                streamedChunks.remove(dimension);
            }
        }
    }
}
