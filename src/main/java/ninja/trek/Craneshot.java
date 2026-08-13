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
import ninja.trek.nodes.network.payload.FollowerConfigPayload;

public class Craneshot implements ModInitializer {
    public static final String MOD_ID = "craneshot";
    public static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // Register payload types for networking
        registerPayloads();

        // Register server networking handlers
        ServerNodeNetworking.register();

        String followerProp = System.getProperty("craneshot.follower");
        if (followerProp != null) {
            LOGGER.info("Craneshot mod initialized! Follower mode enabled, index={}", followerProp);
        } else {
            LOGGER.info("Craneshot mod initialized! Follower mode disabled");
        }
    }

    private void registerPayloads() {
        // Server-to-Client payloads
        PayloadTypeRegistry.clientboundPlay().register(ChunkNodesPayload.ID, ChunkNodesPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(NodesDeltaPayload.ID, NodesDeltaPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(HandshakePayload.ID, HandshakePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AreasSnapshotPayload.ID, AreasSnapshotPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AreasDeltaPayload.ID, AreasDeltaPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FollowerConfigPayload.ID, FollowerConfigPayload.CODEC);

        // Client-to-Server payloads
        PayloadTypeRegistry.serverboundPlay().register(EditRequestPayload.ID, EditRequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(HandshakePayload.ID, HandshakePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(AreaEditRequestPayload.ID, AreaEditRequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(FollowerConfigPayload.ID, FollowerConfigPayload.CODEC);
    }
}
