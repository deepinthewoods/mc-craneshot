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
import ninja.trek.nodes.model.CameraNodeDTO;
import ninja.trek.nodes.network.payload.ChunkNodesPayload;
import ninja.trek.nodes.network.payload.HandshakePayload;
import ninja.trek.nodes.network.payload.NodesDeltaPayload;

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

    private static void onChunkUnload(ClientWorld world, net.minecraft.world.chunk.WorldChunk chunk) {
        NodeManager.get().handleChunkUnload(world.getRegistryKey(), chunk.getPos());
    }
}
