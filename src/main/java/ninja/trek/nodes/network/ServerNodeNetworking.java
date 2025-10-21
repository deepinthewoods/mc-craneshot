package ninja.trek.nodes.network;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import ninja.trek.Craneshot;
import ninja.trek.nodes.model.CameraNodeDTO;
import ninja.trek.nodes.network.ServerNodeNetworking.NodeDelta.Type;
import ninja.trek.nodes.network.payload.ChunkNodesPayload;
import ninja.trek.nodes.network.payload.EditRequestPayload;
import ninja.trek.nodes.network.payload.HandshakePayload;
import ninja.trek.nodes.network.payload.NodesDeltaPayload;
import ninja.trek.nodes.server.ServerNodeManager;

import java.util.*;

public final class ServerNodeNetworking {
    private ServerNodeNetworking() {}

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register(ServerNodeNetworking::onPlayerJoin);
        ServerPlayConnectionEvents.DISCONNECT.register(ServerNodeNetworking::onPlayerDisconnect);

        // Register CustomPayload receivers
        ServerPlayNetworking.registerGlobalReceiver(HandshakePayload.ID, ServerNodeNetworking::handleHandshakePayload);
        ServerPlayNetworking.registerGlobalReceiver(EditRequestPayload.ID, ServerNodeNetworking::handleEditRequestPayload);

        ServerChunkEvents.CHUNK_LOAD.register(ServerNodeNetworking::onChunkLoad);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ServerNodeManager.get().resetRateLimiter();
            for (ServerWorld world : server.getWorlds()) {
                for (ServerPlayerEntity player : world.getPlayers()) {
                    if (!ServerNodeManager.get().isHandshakeComplete(player)) continue;
                    syncTrackedChunks(player, world);
                }
            }
        });
    }

    private static void onPlayerJoin(ServerPlayNetworkHandler handler, net.fabricmc.fabric.api.networking.v1.PacketSender sender, MinecraftServer server) {
        ServerPlayerEntity player = handler.player;
        boolean canEdit = ServerNodeManager.get().canEditOnServer(player);
        sendHandshakeOffer(player, canEdit);
        ServerNodeManager.get().markHandshakeSent(player, canEdit);
    }

    private static void onPlayerDisconnect(ServerPlayNetworkHandler handler, MinecraftServer server) {
        ServerNodeManager.get().onPlayerDisconnected(handler.player);
    }

    private static void handleHandshakePayload(HandshakePayload payload, ServerPlayNetworking.Context context) {
        ServerPlayerEntity player = context.player();
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
            for (ServerWorld world : context.server().getWorlds()) {
                if (world.getPlayers().contains(player)) {
                    sendInitialChunks(player, world);
                    break;
                }
            }
            Craneshot.LOGGER.info("Player {} completed craneshot handshake (edit={})", player.getName().getString(), canEdit);
        }
    }

    private static void handleEditRequestPayload(EditRequestPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayerEntity player = context.player();
        if (!ServerNodeManager.get().isHandshakeComplete(player)) {
            Craneshot.LOGGER.debug("Ignoring edit request from {} before handshake completion", player.getName().getString());
            return;
        }
        if (!ServerNodeManager.get().consumeRequest(player)) {
            player.sendMessage(Text.literal("[Craneshot] Too many edit requests; slow down."), false);
            return;
        }

        ServerWorld world = ServerNodeManager.resolveWorld(context.server(), payload.dimension());
        if (world == null) {
            Craneshot.LOGGER.warn("Received edit request for unknown dimension {}", payload.dimension().getValue());
            return;
        }

        switch (payload.operation()) {
            case CREATE -> handleCreate(player, world, payload.nodeData());
            case UPDATE -> handleUpdate(player, world, payload.nodeData());
            case DELETE -> handleDelete(player, world, payload.nodeIdForDelete());
        }
    }

    private static void handleCreate(ServerPlayerEntity player, ServerWorld world, CameraNodeDTO incoming) {
        if (!ServerNodeManager.get().hasCreatePermission(player)) {
            player.sendMessage(Text.literal("[Craneshot] You do not have permission to create nodes on this server."), false);
            return;
        }
        String error = ServerNodeManager.get().validateNodePayload(incoming);
        if (error != null) {
            player.sendMessage(Text.literal("[Craneshot] Invalid node: " + error), false);
            return;
        }

        UUID tempId = incoming.uuid;
        incoming.uuid = UUID.randomUUID();
        incoming.owner = player.getUuid();
        incoming.clientRequestId = null;

        ChunkPos chunk = ServerNodeManager.chunkPosFromNode(incoming);
        ServerNodeManager.get().upsertNode(world, chunk, incoming);

        CameraNodeDTO packetDto = incoming.copy();
        packetDto.clientRequestId = tempId;

        NodeDelta delta = NodeDelta.add(world.getRegistryKey(), chunk, packetDto);
        broadcastDeltas(world, List.of(delta));
        Craneshot.LOGGER.info("Player {} created node {} in chunk {} {}", player.getName().getString(), incoming.uuid, chunk.x, chunk.z);
    }

    private static void handleUpdate(ServerPlayerEntity player, ServerWorld world, CameraNodeDTO incoming) {
        CameraNodeDTO existing = ServerNodeManager.get().getNode(world, incoming.uuid);
        if (existing == null) {
            player.sendMessage(Text.literal("[Craneshot] Node was not found on the server."), false);
            return;
        }
        if (!ServerNodeManager.get().hasEditPermission(player, existing)) {
            player.sendMessage(Text.literal("[Craneshot] You do not have permission to edit this node."), false);
            return;
        }
        incoming.owner = existing.owner;
        String error = ServerNodeManager.get().validateNodePayload(incoming);
        if (error != null) {
            player.sendMessage(Text.literal("[Craneshot] Invalid update: " + error), false);
            return;
        }

        ChunkPos oldChunk = ServerNodeManager.chunkPosFromNode(existing);
        ChunkPos newChunk = ServerNodeManager.chunkPosFromNode(incoming);

        if (!oldChunk.equals(newChunk)) {
            ServerNodeManager.get().removeNode(world, existing.uuid);
            ServerNodeManager.get().upsertNode(world, newChunk, incoming);
            NodeDelta remove = NodeDelta.remove(world.getRegistryKey(), oldChunk, existing.uuid);
            NodeDelta add = NodeDelta.add(world.getRegistryKey(), newChunk, incoming);
            broadcastDeltas(world, List.of(remove, add));
        } else {
            ServerNodeManager.get().upsertNode(world, newChunk, incoming);
            NodeDelta update = NodeDelta.update(world.getRegistryKey(), newChunk, incoming);
            broadcastDeltas(world, List.of(update));
        }
        Craneshot.LOGGER.info("Player {} updated node {}", player.getName().getString(), incoming.uuid);
    }

    private static void handleDelete(ServerPlayerEntity player, ServerWorld world, UUID nodeId) {
        CameraNodeDTO existing = ServerNodeManager.get().getNode(world, nodeId);
        if (existing == null) return;
        if (!ServerNodeManager.get().hasEditPermission(player, existing)) {
            player.sendMessage(Text.literal("[Craneshot] You do not have permission to delete this node."), false);
            return;
        }
        ChunkPos chunk = ServerNodeManager.chunkPosFromNode(existing);
        if (ServerNodeManager.get().removeNode(world, nodeId)) {
            NodeDelta delta = NodeDelta.remove(world.getRegistryKey(), chunk, nodeId);
            broadcastDeltas(world, List.of(delta));
            Craneshot.LOGGER.info("Player {} removed node {}", player.getName().getString(), nodeId);
        }
    }

    private static void onChunkLoad(ServerWorld world, WorldChunk chunk) {
        ChunkPos pos = chunk.getPos();
        Iterable<ServerPlayerEntity> players = PlayerLookup.tracking(world, pos);
        for (ServerPlayerEntity player : players) {
            if (!ServerNodeManager.get().isHandshakeComplete(player)) continue;
            boolean fresh = ServerNodeManager.get().markChunkStreamed(player, world.getRegistryKey(), pos);
            if (fresh) {
                sendChunkSnapshot(player, world, pos);
            }
        }
    }

    private static void sendHandshakeOffer(ServerPlayerEntity player, boolean canEdit) {
        HandshakePayload payload = new HandshakePayload(
            0, // stage 0: server -> client offer
            NodeNetworkConstants.PROTOCOL_VERSION,
            true, // server authoritative
            canEdit
        );
        ServerPlayNetworking.send(player, payload);
    }

    private static void sendInitialChunks(ServerPlayerEntity player, ServerWorld world) {
        syncTrackedChunks(player, world);
    }

    private static void syncTrackedChunks(ServerPlayerEntity player, ServerWorld world) {
        RegistryKey<World> dimension = world.getRegistryKey();
        ChunkPos center = player.getChunkPos();
        MinecraftServer server = world.getServer();
        int viewDistance = Math.max(2, server != null ? server.getPlayerManager().getViewDistance() : 10);
        Set<Long> keep = new HashSet<>();
        for (int dx = -viewDistance; dx <= viewDistance; dx++) {
            for (int dz = -viewDistance; dz <= viewDistance; dz++) {
                int cx = center.x + dx;
                int cz = center.z + dz;
                if (world.getChunkManager().isChunkLoaded(cx, cz)) {
                    ChunkPos pos = new ChunkPos(cx, cz);
                    long key = pos.toLong();
                    keep.add(key);
                    if (ServerNodeManager.get().markChunkStreamed(player, dimension, pos)) {
                        sendChunkSnapshot(player, world, pos);
                    }
                }
            }
        }
        ServerNodeManager.get().retainStreamedChunks(player, dimension, keep);
    }

    private static void sendChunkSnapshot(ServerPlayerEntity player, ServerWorld world, ChunkPos pos) {
        List<CameraNodeDTO> nodes = ServerNodeManager.get().getChunkNodes(world, pos);
        // Clear client request IDs for chunk snapshots
        List<CameraNodeDTO> cleanNodes = new ArrayList<>(nodes.size());
        for (CameraNodeDTO dto : nodes) {
            CameraNodeDTO clean = dto.copy();
            clean.clientRequestId = null;
            cleanNodes.add(clean);
        }
        ChunkNodesPayload payload = new ChunkNodesPayload(world.getRegistryKey(), pos, cleanNodes);
        ServerPlayNetworking.send(player, payload);
    }

    private static void broadcastDeltas(ServerWorld world, List<NodeDelta> deltas) {
        if (deltas.isEmpty()) return;
        Map<ChunkGroupKey, List<NodeDelta>> grouped = new HashMap<>();
        for (NodeDelta delta : deltas) {
            ChunkGroupKey key = new ChunkGroupKey(delta.dimension(), delta.chunk());
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(delta);
        }

        for (var entry : grouped.entrySet()) {
            ChunkPos chunk = entry.getKey().chunk();
            RegistryKey<World> dimension = entry.getKey().dimension();
            Iterable<ServerPlayerEntity> players = PlayerLookup.tracking(world, chunk);

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

            for (ServerPlayerEntity player : players) {
                if (!ServerNodeManager.get().isHandshakeComplete(player)) continue;
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

    private record ChunkGroupKey(RegistryKey<World> dimension, ChunkPos chunk) {}

    public record NodeDelta(Type type, RegistryKey<World> dimension, ChunkPos chunk, CameraNodeDTO node, UUID removedId, UUID clientRequestId) {
        static NodeDelta add(RegistryKey<World> dimension, ChunkPos chunk, CameraNodeDTO node) {
            return new NodeDelta(Type.ADD, dimension, chunk, node, null, node.clientRequestId);
        }

        static NodeDelta update(RegistryKey<World> dimension, ChunkPos chunk, CameraNodeDTO node) {
            node.clientRequestId = null;
            return new NodeDelta(Type.UPDATE, dimension, chunk, node, null, null);
        }

        static NodeDelta remove(RegistryKey<World> dimension, ChunkPos chunk, UUID removedId) {
            return new NodeDelta(Type.REMOVE, dimension, chunk, null, removedId, null);
        }

        public enum Type {
            ADD, UPDATE, REMOVE
        }
    }
}
