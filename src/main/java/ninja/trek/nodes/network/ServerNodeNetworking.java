package ninja.trek.nodes.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import ninja.trek.Craneshot;
import ninja.trek.nodes.model.AreaInstanceDTO;
import ninja.trek.nodes.model.CameraNodeDTO;
import ninja.trek.nodes.network.ServerNodeNetworking.NodeDelta.Type;
import ninja.trek.nodes.network.payload.ChunkNodesPayload;
import ninja.trek.nodes.network.payload.EditRequestPayload;
import ninja.trek.nodes.network.payload.HandshakePayload;
import ninja.trek.nodes.network.payload.NodesDeltaPayload;
import ninja.trek.nodes.network.payload.AreaEditRequestPayload;
import ninja.trek.nodes.network.payload.AreasDeltaPayload;
import ninja.trek.nodes.network.payload.AreasSnapshotPayload;
import ninja.trek.nodes.network.payload.FollowerConfigPayload;
import ninja.trek.nodes.network.payload.FollowerZoomRequestPayload;
import ninja.trek.nodes.network.payload.FollowerZoomStatePayload;
import ninja.trek.nodes.server.ServerNodeManager;

import java.util.*;

public final class ServerNodeNetworking {
    private ServerNodeNetworking() {}

    private static volatile String latestFollowerConfigJson = null;
    private static final Map<UUID, FollowerZoomStatePayload> latestFollowerZoomStates = new HashMap<>();
    private static final double MAX_FOLLOWER_ZOOM_TARGET_DISTANCE = 132.0;

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register(ServerNodeNetworking::onPlayerJoin);
        ServerPlayConnectionEvents.DISCONNECT.register(ServerNodeNetworking::onPlayerDisconnect);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            latestFollowerConfigJson = null;
            latestFollowerZoomStates.clear();
        });

        // Register CustomPayload receivers
        ServerPlayNetworking.registerGlobalReceiver(HandshakePayload.ID, ServerNodeNetworking::handleHandshakePayload);
        ServerPlayNetworking.registerGlobalReceiver(EditRequestPayload.ID, ServerNodeNetworking::handleEditRequestPayload);
        ServerPlayNetworking.registerGlobalReceiver(AreaEditRequestPayload.ID, ServerNodeNetworking::handleAreaEditRequestPayload);
        ServerPlayNetworking.registerGlobalReceiver(FollowerConfigPayload.ID, ServerNodeNetworking::handleFollowerConfigPayload);
        ServerPlayNetworking.registerGlobalReceiver(FollowerZoomRequestPayload.ID, ServerNodeNetworking::handleFollowerZoomRequestPayload);

        ServerChunkEvents.CHUNK_LOAD.register(ServerNodeNetworking::onChunkLoad);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ServerNodeManager.get().resetRateLimiter();
            for (ServerLevel world : server.getAllLevels()) {
                for (ServerPlayer player : world.players()) {
                    if (!ServerNodeManager.get().isHandshakeComplete(player)) continue;
                    syncTrackedChunks(player, world);
                }
            }
        });
    }

    private static void onPlayerJoin(ServerGamePacketListenerImpl handler, net.fabricmc.fabric.api.networking.v1.PacketSender sender, MinecraftServer server) {
        ServerPlayer player = handler.player;
        boolean canEdit = ServerNodeManager.get().canEditOnServer(player);
        sendHandshakeOffer(player, canEdit);
        ServerNodeManager.get().markHandshakeSent(player, canEdit);

    }

    private static void onPlayerDisconnect(ServerGamePacketListenerImpl handler, MinecraftServer server) {
        UUID playerId = handler.player.getUUID();
        if (latestFollowerZoomStates.remove(playerId) != null) {
            broadcastFollowerZoomState(server, FollowerZoomStatePayload.inactive(playerId), handler.player);
        }
        ServerNodeManager.get().onPlayerDisconnected(handler.player);
    }

    private static void handleHandshakePayload(HandshakePayload payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        // stage 0 is server->client, stage 1 is client->server ack
        if (payload.stage() == 1) {
            if (payload.protocol() != NodeNetworkConstants.PROTOCOL_VERSION) {
                Craneshot.LOGGER.warn("Player {} has incompatible craneshot protocol {} (server {}). Falling back to client storage.",
                    player.getName().getString(), payload.protocol(), NodeNetworkConstants.PROTOCOL_VERSION);
                return;
            }
            boolean canEdit = ServerNodeManager.get().canEditOnServer(player);
            ServerNodeManager.get().markHandshakeComplete(player, canEdit);
            String storedConfig = latestFollowerConfigJson;
            if (storedConfig != null) {
                ServerPlayNetworking.send(player, new FollowerConfigPayload(storedConfig));
            }
            for (FollowerZoomStatePayload zoomState : latestFollowerZoomStates.values()) {
                ServerPlayNetworking.send(player, zoomState);
            }
            // Get the world the player is in by looking through all worlds
            for (ServerLevel world : context.server().getAllLevels()) {
                if (world.players().contains(player)) {
                    sendInitialChunks(player, world);
                    break;
                }
            }
            Craneshot.LOGGER.info("Player {} completed craneshot handshake (edit={})", player.getName().getString(), canEdit);
        }
    }

    private static void handleEditRequestPayload(EditRequestPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        if (!ServerNodeManager.get().isHandshakeComplete(player)) {
            Craneshot.LOGGER.debug("Ignoring edit request from {} before handshake completion", player.getName().getString());
            return;
        }
        if (!ServerNodeManager.get().consumeRequest(player)) {
            player.sendSystemMessage(Component.literal("[Craneshot] Too many edit requests; slow down."));
            return;
        }

        ServerLevel world = ServerNodeManager.resolveWorld(context.server(), payload.dimension());
        if (world == null) {
            Craneshot.LOGGER.warn("Received edit request for unknown dimension {}", payload.dimension().identifier());
            return;
        }

        switch (payload.operation()) {
            case CREATE -> {
                if (payload.nodeData() == null) {
                    rejectMalformedRequest(player, "create request has no node data");
                    return;
                }
                handleCreate(player, world, payload.nodeData());
            }
            case UPDATE -> {
                if (payload.nodeData() == null || payload.nodeData().uuid == null) {
                    rejectMalformedRequest(player, "update request has incomplete node data");
                    return;
                }
                handleUpdate(player, world, payload.nodeData());
            }
            case DELETE -> {
                if (payload.nodeIdForDelete() == null) {
                    rejectMalformedRequest(player, "delete request has no node id");
                    return;
                }
                handleDelete(player, world, payload.nodeIdForDelete());
            }
        }
    }

    private static void handleAreaEditRequestPayload(AreaEditRequestPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        if (!ServerNodeManager.get().isHandshakeComplete(player)) {
            Craneshot.LOGGER.debug("Ignoring area edit request from {} before handshake completion", player.getName().getString());
            return;
        }
        if (!ServerNodeManager.get().consumeRequest(player)) {
            player.sendSystemMessage(Component.literal("[Craneshot] Too many edit requests; slow down."));
            return;
        }

        ServerLevel world = ServerNodeManager.resolveWorld(context.server(), payload.dimension());
        if (world == null) {
            Craneshot.LOGGER.warn("Received area edit request for unknown dimension {}", payload.dimension().identifier());
            return;
        }

        switch (payload.operation()) {
            case CREATE -> {
                if (payload.areaData() == null) {
                    rejectMalformedRequest(player, "create request has no area data");
                    return;
                }
                handleAreaCreate(player, world, payload.areaData());
            }
            case UPDATE -> {
                if (payload.areaData() == null || payload.areaData().uuid == null) {
                    rejectMalformedRequest(player, "update request has incomplete area data");
                    return;
                }
                handleAreaUpdate(player, world, payload.areaData());
            }
            case DELETE -> {
                if (payload.areaIdForDelete() == null) {
                    rejectMalformedRequest(player, "delete request has no area id");
                    return;
                }
                handleAreaDelete(player, world, payload.areaIdForDelete());
            }
        }
    }

    private static void handleCreate(ServerPlayer player, ServerLevel world, CameraNodeDTO incoming) {
        if (!ServerNodeManager.get().hasCreatePermission(player)) {
            player.sendSystemMessage(Component.literal("[Craneshot] You do not have permission to create nodes on this server."));
            return;
        }
        String error = ServerNodeManager.get().validateNodePayload(incoming);
        if (error != null) {
            player.sendSystemMessage(Component.literal("[Craneshot] Invalid node: " + error));
            return;
        }

        UUID tempId = incoming.uuid;
        incoming.uuid = UUID.randomUUID();
        incoming.owner = player.getUUID();
        incoming.clientRequestId = null;

        ChunkPos chunk = ServerNodeManager.chunkPosFromNode(incoming);
        ServerNodeManager.get().upsertNode(world, chunk, incoming);

        CameraNodeDTO packetDto = incoming.copy();
        packetDto.clientRequestId = tempId;

        NodeDelta delta = NodeDelta.add(world.dimension(), chunk, packetDto);
        broadcastDeltas(world, List.of(delta));
        Craneshot.LOGGER.info("Player {} created node {} in chunk {} {}", player.getName().getString(), incoming.uuid, chunk.x(), chunk.z());
    }

    private static void handleUpdate(ServerPlayer player, ServerLevel world, CameraNodeDTO incoming) {
        CameraNodeDTO existing = ServerNodeManager.get().getNode(world, incoming.uuid);
        if (existing == null) {
            player.sendSystemMessage(Component.literal("[Craneshot] Node was not found on the server."));
            return;
        }
        if (!ServerNodeManager.get().hasEditPermission(player, existing)) {
            player.sendSystemMessage(Component.literal("[Craneshot] You do not have permission to edit this node."));
            return;
        }
        incoming.owner = existing.owner;
        String error = ServerNodeManager.get().validateNodePayload(incoming);
        if (error != null) {
            player.sendSystemMessage(Component.literal("[Craneshot] Invalid update: " + error));
            return;
        }

        ChunkPos oldChunk = ServerNodeManager.chunkPosFromNode(existing);
        ChunkPos newChunk = ServerNodeManager.chunkPosFromNode(incoming);

        if (!oldChunk.equals(newChunk)) {
            ServerNodeManager.get().removeNode(world, existing.uuid);
            ServerNodeManager.get().upsertNode(world, newChunk, incoming);
            NodeDelta remove = NodeDelta.remove(world.dimension(), oldChunk, existing.uuid);
            NodeDelta add = NodeDelta.add(world.dimension(), newChunk, incoming);
            broadcastDeltas(world, List.of(remove, add));
        } else {
            ServerNodeManager.get().upsertNode(world, newChunk, incoming);
            NodeDelta update = NodeDelta.update(world.dimension(), newChunk, incoming);
            broadcastDeltas(world, List.of(update));
        }
        Craneshot.LOGGER.info("Player {} updated node {}", player.getName().getString(), incoming.uuid);
    }

    private static void handleDelete(ServerPlayer player, ServerLevel world, UUID nodeId) {
        CameraNodeDTO existing = ServerNodeManager.get().getNode(world, nodeId);
        if (existing == null) return;
        if (!ServerNodeManager.get().hasEditPermission(player, existing)) {
            player.sendSystemMessage(Component.literal("[Craneshot] You do not have permission to delete this node."));
            return;
        }
        ChunkPos chunk = ServerNodeManager.chunkPosFromNode(existing);
        if (ServerNodeManager.get().removeNode(world, nodeId)) {
            NodeDelta delta = NodeDelta.remove(world.dimension(), chunk, nodeId);
            broadcastDeltas(world, List.of(delta));
            Craneshot.LOGGER.info("Player {} removed node {}", player.getName().getString(), nodeId);
        }
    }

    /** Upsert a server-authoritative integration node and notify tracking clients. */
    public static void upsertManagedNode(ServerLevel world, CameraNodeDTO incoming) {
        CameraNodeDTO existing = ServerNodeManager.get().getNode(world, incoming.uuid);
        ChunkPos newChunk = ServerNodeManager.chunkPosFromNode(incoming);
        if (existing == null) {
            ServerNodeManager.get().upsertNode(world, newChunk, incoming);
            broadcastDeltas(world, List.of(NodeDelta.add(world.dimension(), newChunk, incoming)));
            return;
        }

        ChunkPos oldChunk = ServerNodeManager.chunkPosFromNode(existing);
        if (!oldChunk.equals(newChunk)) {
            ServerNodeManager.get().removeNode(world, existing.uuid);
            ServerNodeManager.get().upsertNode(world, newChunk, incoming);
            broadcastDeltas(world, List.of(
                    NodeDelta.remove(world.dimension(), oldChunk, existing.uuid),
                    NodeDelta.add(world.dimension(), newChunk, incoming)));
        } else {
            ServerNodeManager.get().upsertNode(world, newChunk, incoming);
            broadcastDeltas(world, List.of(NodeDelta.update(world.dimension(), newChunk, incoming)));
        }
    }

    private static void handleFollowerConfigPayload(FollowerConfigPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayer sender = context.player();
        if (!ServerNodeManager.get().isHandshakeComplete(sender)
                || !ServerNodeManager.get().hasCreatePermission(sender)) {
            Craneshot.LOGGER.warn("Ignoring follower configuration from unauthorized player {}",
                    sender.getName().getString());
            return;
        }
        if (!ServerNodeManager.get().consumeRequest(sender)) {
            sender.sendSystemMessage(Component.literal("[Craneshot] Too many configuration requests; slow down."));
            return;
        }

        String configJson = payload.configJson();
        if (!isValidFollowerConfig(configJson)) {
            sender.sendSystemMessage(Component.literal("[Craneshot] Invalid follower configuration."));
            return;
        }

        latestFollowerConfigJson = configJson;
        for (ServerPlayer player : PlayerLookup.all(context.server())) {
            if (player != sender && ServerNodeManager.get().isHandshakeComplete(player)) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

    private static void handleFollowerZoomRequestPayload(
            FollowerZoomRequestPayload payload,
            ServerPlayNetworking.Context context) {
        ServerPlayer sender = context.player();
        if (!ServerNodeManager.get().isHandshakeComplete(sender)) {
            Craneshot.LOGGER.debug("Ignoring follower zoom state from {} before handshake completion",
                    sender.getName().getString());
            return;
        }
        if (!ServerNodeManager.get().consumeRequest(sender)) {
            Craneshot.LOGGER.debug("Rate-limited follower zoom state from {}", sender.getName().getString());
            return;
        }

        UUID playerId = sender.getUUID();
        if (!payload.active()) {
            latestFollowerZoomStates.remove(playerId);
            broadcastFollowerZoomState(context.server(), FollowerZoomStatePayload.inactive(playerId), sender);
            return;
        }

        if (!isValidFollowerZoomTarget(sender, payload)) {
            Craneshot.LOGGER.warn("Ignoring invalid follower zoom target from {}", sender.getName().getString());
            latestFollowerZoomStates.remove(playerId);
            broadcastFollowerZoomState(context.server(), FollowerZoomStatePayload.inactive(playerId), sender);
            return;
        }

        FollowerZoomStatePayload state = new FollowerZoomStatePayload(
                playerId,
                true,
                payload.dimension(),
                payload.blockPos(),
                payload.hitLocation(),
                payload.yaw(),
                payload.pitch()
        );
        latestFollowerZoomStates.put(playerId, state);
        broadcastFollowerZoomState(context.server(), state, sender);
    }

    private static boolean isValidFollowerZoomTarget(ServerPlayer sender, FollowerZoomRequestPayload payload) {
        if (payload.dimension() == null
                || payload.dimension().length() > FollowerZoomRequestPayload.MAX_DIMENSION_LENGTH
                || !payload.dimension().equals(sender.level().dimension().identifier().toString())
                || payload.blockPos() == null
                || payload.hitLocation() == null
                || !Double.isFinite(payload.hitLocation().x)
                || !Double.isFinite(payload.hitLocation().y)
                || !Double.isFinite(payload.hitLocation().z)
                || !Float.isFinite(payload.yaw())
                || !Float.isFinite(payload.pitch())
                || payload.pitch() < -90.0f
                || payload.pitch() > 90.0f) {
            return false;
        }
        double maxDistanceSquared = MAX_FOLLOWER_ZOOM_TARGET_DISTANCE * MAX_FOLLOWER_ZOOM_TARGET_DISTANCE;
        return sender.getEyePosition().distanceToSqr(payload.hitLocation()) <= maxDistanceSquared;
    }

    private static void broadcastFollowerZoomState(
            MinecraftServer server,
            FollowerZoomStatePayload payload,
            ServerPlayer sender) {
        for (ServerPlayer player : PlayerLookup.all(server)) {
            if (player != sender && ServerNodeManager.get().isHandshakeComplete(player)) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

    private static boolean isValidFollowerConfig(String configJson) {
        if (configJson == null || configJson.length() > FollowerConfigPayload.MAX_CONFIG_LENGTH) {
            return false;
        }
        try {
            JsonElement parsed = JsonParser.parseString(configJson);
            if (!parsed.isJsonObject()) return false;
            JsonObject root = parsed.getAsJsonObject();

            if (root.has("targetPlayerName")) {
                JsonElement name = root.get("targetPlayerName");
                if (!name.isJsonPrimitive() || !name.getAsJsonPrimitive().isString()
                        || name.getAsString().length() > 64) {
                    return false;
                }
            }

            if (!root.has("followers") || !root.get("followers").isJsonArray()) return false;
            JsonArray followers = root.getAsJsonArray("followers");
            if (followers.size() > 32) return false;
            for (JsonElement followerElement : followers) {
                if (!followerElement.isJsonObject()) return false;
                JsonObject follower = followerElement.getAsJsonObject();
                if (follower.has("useZones")
                        && (!follower.get("useZones").isJsonPrimitive()
                        || !follower.get("useZones").getAsJsonPrimitive().isBoolean())) {
                    return false;
                }
                if (follower.has("movement") && !follower.get("movement").isJsonNull()
                        && !isValidMovementConfig(follower.get("movement"))) {
                    return false;
                }
                if (follower.has("speakingMovement") && !follower.get("speakingMovement").isJsonNull()
                        && !isValidMovementConfig(follower.get("speakingMovement"))) {
                    return false;
                }
                if (follower.has("zoomMovement") && !follower.get("zoomMovement").isJsonNull()
                        && !isValidMovementConfig(follower.get("zoomMovement"))) {
                    return false;
                }
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean isValidMovementConfig(JsonElement movementElement) {
        if (!movementElement.isJsonObject()) return false;
        JsonObject movement = movementElement.getAsJsonObject();
        if (!movement.has("type") || !movement.get("type").isJsonPrimitive()
                || !movement.get("type").getAsJsonPrimitive().isString()
                || movement.get("type").getAsString().length() > 256) {
            return false;
        }
        if (!movement.has("settings")) return true;
        if (!movement.get("settings").isJsonObject()) return false;
        JsonObject settings = movement.getAsJsonObject("settings");
        if (settings.size() > 128) return false;
        for (Map.Entry<String, JsonElement> entry : settings.entrySet()) {
            if (entry.getKey().length() > 128 || !entry.getValue().isJsonPrimitive()
                    || entry.getValue().getAsString().length() > 2048) {
                return false;
            }
        }
        return true;
    }

    private static void rejectMalformedRequest(ServerPlayer player, String reason) {
        Craneshot.LOGGER.warn("Ignoring malformed edit request from {}: {}",
                player.getName().getString(), reason);
        player.sendSystemMessage(Component.literal("[Craneshot] Malformed edit request."));
    }

    private static void handleAreaCreate(ServerPlayer player, ServerLevel world, AreaInstanceDTO incoming) {
        if (incoming == null) return;
        if (!ServerNodeManager.get().hasCreatePermission(player)) {
            player.sendSystemMessage(Component.literal("[Craneshot] You do not have permission to create areas on this server."));
            return;
        }
        String error = ServerNodeManager.get().validateAreaPayload(world, incoming);
        if (error != null) {
            player.sendSystemMessage(Component.literal("[Craneshot] Invalid area: " + error));
            return;
        }

        UUID tempId = incoming.uuid;
        incoming.uuid = UUID.randomUUID();
        incoming.owner = player.getUUID();
        incoming.clientRequestId = null;

        ServerNodeManager.get().upsertArea(world, incoming);

        AreaInstanceDTO packetDto = AreaInstanceDTO.fromAreaInstance(incoming.toAreaInstance());
        packetDto.clientRequestId = tempId;

        AreaDelta delta = AreaDelta.add(world.dimension(), packetDto);
        broadcastAreaDeltas(world, List.of(delta));
        Craneshot.LOGGER.info("Player {} created area {}", player.getName().getString(), incoming.uuid);
    }

    private static void handleAreaUpdate(ServerPlayer player, ServerLevel world, AreaInstanceDTO incoming) {
        if (incoming == null) return;
        AreaInstanceDTO existing = ServerNodeManager.get().getArea(world, incoming.uuid);
        if (existing == null) {
            player.sendSystemMessage(Component.literal("[Craneshot] Area was not found on the server."));
            return;
        }
        if (!ServerNodeManager.get().hasAreaEditPermission(player, existing)) {
            player.sendSystemMessage(Component.literal("[Craneshot] You do not have permission to edit this area."));
            return;
        }
        incoming.owner = existing.owner;
        String error = ServerNodeManager.get().validateAreaPayload(world, incoming);
        if (error != null) {
            player.sendSystemMessage(Component.literal("[Craneshot] Invalid area update: " + error));
            return;
        }
        incoming.clientRequestId = null;
        ServerNodeManager.get().upsertArea(world, incoming);
        AreaInstanceDTO packetDto = AreaInstanceDTO.fromAreaInstance(incoming.toAreaInstance());
        AreaDelta delta = AreaDelta.update(world.dimension(), packetDto);
        broadcastAreaDeltas(world, List.of(delta));
        Craneshot.LOGGER.info("Player {} updated area {}", player.getName().getString(), incoming.uuid);
    }

    private static void handleAreaDelete(ServerPlayer player, ServerLevel world, UUID areaId) {
        if (areaId == null) return;
        AreaInstanceDTO existing = ServerNodeManager.get().getArea(world, areaId);
        if (existing == null) return;
        if (!ServerNodeManager.get().hasAreaEditPermission(player, existing)) {
            player.sendSystemMessage(Component.literal("[Craneshot] You do not have permission to delete this area."));
            return;
        }
        if (ServerNodeManager.get().removeArea(world, areaId)) {
            AreaDelta delta = AreaDelta.remove(world.dimension(), areaId);
            broadcastAreaDeltas(world, List.of(delta));
            Craneshot.LOGGER.info("Player {} removed area {}", player.getName().getString(), areaId);
        }
    }

    private static void onChunkLoad(ServerLevel world, LevelChunk chunk, boolean alreadyLoaded) {
        ChunkPos pos = chunk.getPos();
        Iterable<ServerPlayer> players = PlayerLookup.tracking(world, pos);
        for (ServerPlayer player : players) {
            if (!ServerNodeManager.get().isHandshakeComplete(player)) continue;
            boolean fresh = ServerNodeManager.get().markChunkStreamed(player, world.dimension(), pos);
            if (fresh) {
                sendChunkSnapshot(player, world, pos);
            }
        }
    }

    private static void sendHandshakeOffer(ServerPlayer player, boolean canEdit) {
        HandshakePayload payload = new HandshakePayload(
            0, // stage 0: server -> client offer
            NodeNetworkConstants.PROTOCOL_VERSION,
            true, // server authoritative
            canEdit
        );
        ServerPlayNetworking.send(player, payload);
    }

    private static void sendInitialChunks(ServerPlayer player, ServerLevel world) {
        syncTrackedChunks(player, world);
    }

    private static void syncTrackedChunks(ServerPlayer player, ServerLevel world) {
        ResourceKey<Level> dimension = world.dimension();
        if (ServerNodeManager.get().markAreasSynced(player, dimension)) {
            sendAreasSnapshot(player, world);
        }
        ChunkPos center = player.chunkPosition();
        MinecraftServer server = world.getServer();
        int viewDistance = Math.max(2, server != null ? server.getPlayerList().getViewDistance() : 10);
        Set<Long> keep = new HashSet<>();
        for (int dx = -viewDistance; dx <= viewDistance; dx++) {
            for (int dz = -viewDistance; dz <= viewDistance; dz++) {
                int cx = center.x() + dx;
                int cz = center.z() + dz;
                if (world.getChunkSource().hasChunk(cx, cz)) {
                    ChunkPos pos = new ChunkPos(cx, cz);
                    long key = pos.pack();
                    keep.add(key);
                    if (ServerNodeManager.get().markChunkStreamed(player, dimension, pos)) {
                        sendChunkSnapshot(player, world, pos);
                    }
                }
            }
        }
        ServerNodeManager.get().retainStreamedChunks(player, dimension, keep);
    }

    private static void sendChunkSnapshot(ServerPlayer player, ServerLevel world, ChunkPos pos) {
        List<CameraNodeDTO> nodes = ServerNodeManager.get().getChunkNodes(world, pos);
        // Clear client request IDs for chunk snapshots
        List<CameraNodeDTO> cleanNodes = new ArrayList<>(nodes.size());
        for (CameraNodeDTO dto : nodes) {
            CameraNodeDTO clean = dto.copy();
            clean.clientRequestId = null;
            cleanNodes.add(clean);
        }
        ChunkNodesPayload payload = new ChunkNodesPayload(world.dimension(), pos, cleanNodes);
        ServerPlayNetworking.send(player, payload);
    }

    private static void sendAreasSnapshot(ServerPlayer player, ServerLevel world) {
        List<AreaInstanceDTO> areas = ServerNodeManager.get().getAreas(world);
        List<AreaInstanceDTO> cleanAreas = new ArrayList<>(areas.size());
        for (AreaInstanceDTO dto : areas) {
            AreaInstanceDTO copy = AreaInstanceDTO.fromAreaInstance(dto.toAreaInstance());
            copy.clientRequestId = null;
            cleanAreas.add(copy);
        }
        AreasSnapshotPayload payload = new AreasSnapshotPayload(world.dimension(), cleanAreas);
        ServerPlayNetworking.send(player, payload);
    }

    private static void broadcastDeltas(ServerLevel world, List<NodeDelta> deltas) {
        if (deltas.isEmpty()) return;
        Map<ChunkGroupKey, List<NodeDelta>> grouped = new HashMap<>();
        for (NodeDelta delta : deltas) {
            ChunkGroupKey key = new ChunkGroupKey(delta.dimension(), delta.chunk());
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(delta);
        }

        for (var entry : grouped.entrySet()) {
            ChunkPos chunk = entry.getKey().chunk();
            ResourceKey<Level> dimension = entry.getKey().dimension();
            Iterable<ServerPlayer> players = PlayerLookup.tracking(world, chunk);

            // Convert NodeDelta list to NodeOperation list for payload
            List<NodesDeltaPayload.NodeOperation> operations = new ArrayList<>();
            for (NodeDelta delta : entry.getValue()) {
                NodesDeltaPayload.OperationType opType = switch (delta.type()) {
                    case ADD -> NodesDeltaPayload.OperationType.ADD;
                    case UPDATE -> NodesDeltaPayload.OperationType.UPDATE;
                    case REMOVE -> NodesDeltaPayload.OperationType.REMOVE;
                };

                UUID nodeId = delta.type() == Type.REMOVE ? delta.removedId() : delta.node().uuid;
                Optional<CameraNodeDTO> nodeData;

                if (delta.type() == Type.ADD || delta.type() == Type.UPDATE) {
                    CameraNodeDTO dto = delta.node().copy();
                    if (delta.type() == Type.ADD && delta.clientRequestId() != null) {
                        dto.clientRequestId = delta.clientRequestId();
                    } else {
                        dto.clientRequestId = null;
                    }
                    nodeData = Optional.of(dto);
                } else {
                    nodeData = Optional.empty();
                }

                operations.add(new NodesDeltaPayload.NodeOperation(opType, nodeId, nodeData));
            }

            NodesDeltaPayload payload = new NodesDeltaPayload(dimension, chunk, operations);

            for (ServerPlayer player : players) {
                if (!ServerNodeManager.get().isHandshakeComplete(player)) continue;
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

    private static void broadcastAreaDeltas(ServerLevel world, List<AreaDelta> deltas) {
        if (deltas.isEmpty()) return;
        List<AreasDeltaPayload.AreaOperation> operations = new ArrayList<>(deltas.size());
        for (AreaDelta delta : deltas) {
            AreasDeltaPayload.OperationType opType = switch (delta.type()) {
                case ADD -> AreasDeltaPayload.OperationType.ADD;
                case UPDATE -> AreasDeltaPayload.OperationType.UPDATE;
                case REMOVE -> AreasDeltaPayload.OperationType.REMOVE;
            };
            UUID areaId = delta.type() == AreaDelta.Type.REMOVE ? delta.removedId() : delta.area().uuid;
            Optional<AreaInstanceDTO> areaData;
            if (delta.type() == AreaDelta.Type.ADD || delta.type() == AreaDelta.Type.UPDATE) {
                AreaInstanceDTO source = delta.area();
                AreaInstanceDTO copy = AreaInstanceDTO.fromAreaInstance(source.toAreaInstance());
                copy.clientRequestId = source.clientRequestId;
                areaData = Optional.of(copy);
            } else {
                areaData = Optional.empty();
            }
            operations.add(new AreasDeltaPayload.AreaOperation(opType, areaId, areaData));
        }

        AreasDeltaPayload payload = new AreasDeltaPayload(world.dimension(), operations);
        for (ServerPlayer player : PlayerLookup.level(world)) {
            if (!ServerNodeManager.get().isHandshakeComplete(player)) continue;
            ServerPlayNetworking.send(player, payload);
        }
    }

    private record ChunkGroupKey(ResourceKey<Level> dimension, ChunkPos chunk) {}

    public record NodeDelta(Type type, ResourceKey<Level> dimension, ChunkPos chunk, CameraNodeDTO node, UUID removedId, UUID clientRequestId) {
        static NodeDelta add(ResourceKey<Level> dimension, ChunkPos chunk, CameraNodeDTO node) {
            return new NodeDelta(Type.ADD, dimension, chunk, node, null, node.clientRequestId);
        }

        static NodeDelta update(ResourceKey<Level> dimension, ChunkPos chunk, CameraNodeDTO node) {
            node.clientRequestId = null;
            return new NodeDelta(Type.UPDATE, dimension, chunk, node, null, null);
        }

        static NodeDelta remove(ResourceKey<Level> dimension, ChunkPos chunk, UUID removedId) {
            return new NodeDelta(Type.REMOVE, dimension, chunk, null, removedId, null);
        }

        public enum Type {
            ADD, UPDATE, REMOVE
        }
    }

    public record AreaDelta(ninja.trek.nodes.network.ServerNodeNetworking.AreaDelta.Type type, ResourceKey<Level> dimension, AreaInstanceDTO area, UUID removedId) {
        static AreaDelta add(ResourceKey<Level> dimension, AreaInstanceDTO area) {
            return new AreaDelta(ninja.trek.nodes.network.ServerNodeNetworking.AreaDelta.Type.ADD, dimension, area, null);
        }

        static AreaDelta update(ResourceKey<Level> dimension, AreaInstanceDTO area) {
            return new AreaDelta(ninja.trek.nodes.network.ServerNodeNetworking.AreaDelta.Type.UPDATE, dimension, area, null);
        }

        static AreaDelta remove(ResourceKey<Level> dimension, UUID removedId) {
            return new AreaDelta(ninja.trek.nodes.network.ServerNodeNetworking.AreaDelta.Type.REMOVE, dimension, null, removedId);
        }

        public enum Type {
            ADD, UPDATE, REMOVE
        }
    }
}
