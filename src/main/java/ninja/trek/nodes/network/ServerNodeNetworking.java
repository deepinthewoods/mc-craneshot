package ninja.trek.nodes.network;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
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
import ninja.trek.nodes.server.ServerNodeManager;

import java.util.*;

public final class ServerNodeNetworking {
    private ServerNodeNetworking() {}

    private static volatile String latestFollowerConfigJson = null;

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register(ServerNodeNetworking::onPlayerJoin);
        ServerPlayConnectionEvents.DISCONNECT.register(ServerNodeNetworking::onPlayerDisconnect);

        // Register CustomPayload receivers
        ServerPlayNetworking.registerGlobalReceiver(HandshakePayload.ID, ServerNodeNetworking::handleHandshakePayload);
        ServerPlayNetworking.registerGlobalReceiver(EditRequestPayload.ID, ServerNodeNetworking::handleEditRequestPayload);
        ServerPlayNetworking.registerGlobalReceiver(AreaEditRequestPayload.ID, ServerNodeNetworking::handleAreaEditRequestPayload);
        ServerPlayNetworking.registerGlobalReceiver(FollowerConfigPayload.ID, ServerNodeNetworking::handleFollowerConfigPayload);

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

        // Send stored follower config to joining player so late-connecting followers get it immediately
        String storedConfig = latestFollowerConfigJson;
        if (storedConfig != null) {
            ServerPlayNetworking.send(player, new FollowerConfigPayload(storedConfig));
        }
    }

    private static void onPlayerDisconnect(ServerGamePacketListenerImpl handler, MinecraftServer server) {
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
            case CREATE -> handleCreate(player, world, payload.nodeData());
            case UPDATE -> handleUpdate(player, world, payload.nodeData());
            case DELETE -> handleDelete(player, world, payload.nodeIdForDelete());
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
            case CREATE -> handleAreaCreate(player, world, payload.areaData());
            case UPDATE -> handleAreaUpdate(player, world, payload.areaData());
            case DELETE -> handleAreaDelete(player, world, payload.areaIdForDelete());
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

    private static void handleFollowerConfigPayload(FollowerConfigPayload payload, ServerPlayNetworking.Context context) {
        latestFollowerConfigJson = payload.configJson();
        ServerPlayer sender = context.player();
        // Broadcast to all other connected players
        for (ServerPlayer player : PlayerLookup.all(context.server())) {
            if (player != sender) {
                ServerPlayNetworking.send(player, payload);
            }
        }
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
