package ninja.trek;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import ninja.trek.nodes.network.ServerNodeNetworking;
import ninja.trek.nodes.network.payload.ChunkNodesPayload;
import ninja.trek.nodes.network.payload.EditRequestPayload;
import ninja.trek.nodes.network.payload.HandshakePayload;
import ninja.trek.nodes.network.payload.NodesDeltaPayload;
import ninja.trek.nodes.network.payload.AreaEditRequestPayload;
import ninja.trek.nodes.network.payload.AreasDeltaPayload;
import ninja.trek.nodes.network.payload.AreasSnapshotPayload;

public class Craneshot implements ModInitializer {
    public static final String MOD_ID = "craneshot";
    public static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // Register payload types for networking
        registerPayloads();

        // Register server networking handlers
        ServerNodeNetworking.register();

        LOGGER.info("Craneshot mod initialized!");
    }

    private void registerPayloads() {
        // Server-to-Client payloads
        PayloadTypeRegistry.playS2C().register(ChunkNodesPayload.ID, ChunkNodesPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(NodesDeltaPayload.ID, NodesDeltaPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HandshakePayload.ID, HandshakePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AreasSnapshotPayload.ID, AreasSnapshotPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AreasDeltaPayload.ID, AreasDeltaPayload.CODEC);

        // Client-to-Server payloads
        PayloadTypeRegistry.playC2S().register(EditRequestPayload.ID, EditRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(HandshakePayload.ID, HandshakePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AreaEditRequestPayload.ID, AreaEditRequestPayload.CODEC);
    }
}
