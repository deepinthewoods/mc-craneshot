package ninja.trek.nodes.network;

import net.minecraft.util.Identifier;
import ninja.trek.Craneshot;

public final class NodeNetworkConstants {
    public static final int PROTOCOL_VERSION = 2;

    public static final Identifier HANDSHAKE = Identifier.of(Craneshot.MOD_ID, "handshake");
    public static final Identifier CHUNK_NODES = Identifier.of(Craneshot.MOD_ID, "chunk_nodes");
    public static final Identifier NODES_DELTA = Identifier.of(Craneshot.MOD_ID, "nodes_delta");
    public static final Identifier EDIT_REQUEST = Identifier.of(Craneshot.MOD_ID, "edit_request");
    public static final Identifier AREAS_SNAPSHOT = Identifier.of(Craneshot.MOD_ID, "areas_snapshot");
    public static final Identifier AREAS_DELTA = Identifier.of(Craneshot.MOD_ID, "areas_delta");
    public static final Identifier AREA_EDIT_REQUEST = Identifier.of(Craneshot.MOD_ID, "area_edit_request");

    private NodeNetworkConstants() {}
}
