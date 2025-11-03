package ninja.trek.nodes.network;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import ninja.trek.Craneshot;
import ninja.trek.nodes.NodeManager;
import ninja.trek.nodes.model.AreaInstanceDTO;
import ninja.trek.nodes.model.CameraNodeDTO;
import ninja.trek.nodes.network.payload.ChunkNodesPayload;
import ninja.trek.nodes.network.payload.HandshakePayload;
import ninja.trek.nodes.network.payload.NodesDeltaPayload;
import ninja.trek.nodes.network.payload.AreaEditRequestPayload;
import ninja.trek.nodes.network.payload.AreasDeltaPayload;
import ninja.trek.nodes.network.payload.AreasSnapshotPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ClientNodeNetworking {
    private ClientNodeNetworking() {}

    public static void register() {
        // Register CustomPayload receivers
        ClientPlayNetworking.registerGlobalReceiver(HandshakePayload.ID, ClientNodeNetworking::handleHandshakePayload);
        ClientPlayNetworking.registerGlobalReceiver(ChunkNodesPayload.ID, ClientNodeNetworking::handleChunkNodesPayload);
        ClientPlayNetworking.registerGlobalReceiver(NodesDeltaPayload.ID, ClientNodeNetworking::handleNodesDeltaPayload);
        ClientPlayNetworking.registerGlobalReceiver(AreasSnapshotPayload.ID, ClientNodeNetworking::handleAreasSnapshotPayload);
        ClientPlayNetworking.registerGlobalReceiver(AreasDeltaPayload.ID, ClientNodeNetworking::handleAreasDeltaPayload);

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> NodeManager.get().onDisconnected());
        ClientChunkEvents.CHUNK_UNLOAD.register(ClientNodeNetworking::onChunkUnload);
    }

    private static void handleHandshakePayload(HandshakePayload payload, ClientPlayNetworking.Context context) {
        // stage 0 is server->client offer, stage 1 is client->server ack
        if (payload.stage() != 0) return;
        if (payload.protocol() != NodeNetworkConstants.PROTOCOL_VERSION || !payload.serverAuthoritative()) {
            Craneshot.LOGGER.warn("Server handshake mismatch (protocol {}, authoritative {}). Staying in client storage mode.",
                payload.protocol(), payload.serverAuthoritative());
            return;
        }

        // Client handlers run on netty thread - must use execute() for client modifications
        context.client().execute(() -> NodeManager.get().enterServerMode(payload.canEdit()));

        // Send acknowledgment back to server
        HandshakePayload reply = new HandshakePayload(
            1, // stage 1: client ack
            NodeNetworkConstants.PROTOCOL_VERSION
        );
        ClientPlayNetworking.send(reply);
    }

    private static void handleChunkNodesPayload(ChunkNodesPayload payload, ClientPlayNetworking.Context context) {
        // Client handlers run on netty thread - must use execute() for client modifications
        context.client().execute(() ->
            NodeManager.get().applyChunkSnapshot(payload.dimension(), payload.chunk(), payload.nodes())
        );
    }

    private static void handleNodesDeltaPayload(NodesDeltaPayload payload, ClientPlayNetworking.Context context) {
        // Build tasks from operations
        List<Runnable> tasks = new ArrayList<>(payload.operations().size());
        for (NodesDeltaPayload.NodeOperation operation : payload.operations()) {
            switch (operation.type()) {
                case ADD -> {
                    if (operation.nodeData().isPresent()) {
                        CameraNodeDTO dto = operation.nodeData().get();
                        tasks.add(() -> NodeManager.get().applyDeltaAdd(payload.dimension(), payload.chunk(), dto));
                    }
                }
                case UPDATE -> {
                    if (operation.nodeData().isPresent()) {
                        CameraNodeDTO dto = operation.nodeData().get();
                        tasks.add(() -> NodeManager.get().applyDeltaUpdate(payload.dimension(), payload.chunk(), dto));
                    }
                }
                case REMOVE -> {
                    UUID id = operation.nodeId();
                    tasks.add(() -> NodeManager.get().applyDeltaRemove(payload.dimension(), payload.chunk(), id));
                }
            }
        }
        // Client handlers run on netty thread - must use execute() for client modifications
        context.client().execute(() -> tasks.forEach(Runnable::run));
    }

    private static void handleAreasSnapshotPayload(AreasSnapshotPayload payload, ClientPlayNetworking.Context context) {
        context.client().execute(() ->
            NodeManager.get().applyAreasSnapshot(payload.dimension(), payload.areas())
        );
    }

    private static void handleAreasDeltaPayload(AreasDeltaPayload payload, ClientPlayNetworking.Context context) {
        List<Runnable> tasks = new ArrayList<>(payload.operations().size());
        for (AreasDeltaPayload.AreaOperation operation : payload.operations()) {
            switch (operation.type()) {
                case ADD -> operation.areaData().ifPresent(dto ->
                        tasks.add(() -> NodeManager.get().applyAreaDeltaAdd(payload.dimension(), dto)));
                case UPDATE -> operation.areaData().ifPresent(dto ->
                        tasks.add(() -> NodeManager.get().applyAreaDeltaUpdate(payload.dimension(), dto)));
                case REMOVE -> {
                    UUID id = operation.areaId();
                    tasks.add(() -> NodeManager.get().applyAreaDeltaRemove(payload.dimension(), id));
                }
            }
        }
        context.client().execute(() -> tasks.forEach(Runnable::run));
    }

    private static void onChunkUnload(ClientWorld world, net.minecraft.world.chunk.WorldChunk chunk) {
        NodeManager.get().handleChunkUnload(world.getRegistryKey(), chunk.getPos());
    }

    public static void sendAreaCreate(RegistryKey<World> dimension, AreaInstanceDTO dto) {
        ClientPlayNetworking.send(AreaEditRequestPayload.create(dimension, dto));
    }

    public static void sendAreaUpdate(RegistryKey<World> dimension, AreaInstanceDTO dto) {
        ClientPlayNetworking.send(AreaEditRequestPayload.update(dimension, dto));
    }

    public static void sendAreaDelete(RegistryKey<World> dimension, UUID areaId) {
        ClientPlayNetworking.send(AreaEditRequestPayload.delete(dimension, areaId));
    }
}
