Nodes: Phase 3 — Server Storage and Sync

Scope
- Make nodes authoritative on the server when present.
- Persist nodes per-world and organize them by chunk so they stream to clients only when relevant chunks are loaded.
- Keep client-only mode working (singleplayer/lan-without-mod or pure client joining vanilla server) using the current client storage.

High‑Level Architecture
- Data ownership
  - If server mod is present: server is the source of truth. Clients cannot persist server nodes to local world files.
  - If server mod absent: client behaves as today (per‑world or config save on the client).
- Storage granularity
  - Nodes are logically attached to chunks: each node belongs to exactly one chunk (computed from its `position`), and travels with that chunk for streaming to clients.
  - Areas and optional `lookAt` are properties of a node and are sent atomically with it.
- Dimensions
  - Maintain independent registries per dimension (`RegistryKey<World>`). Nodes are not shared across dimensions.

Server Persistence
- Backing store: PersistentState per world
  - `CameraNodesState extends PersistentState` holding a map: `dimension -> (chunkPos -> list<CameraNodeDTO>)`.
  - Saves under the world’s save path in a single file (e.g., `data/craneshot_nodes.dat`).
  - Rationale: simpler than writing into chunk NBT, while we still attach nodes to chunks at the logic layer and stream per chunk.
- DTO format
  - Keep a clear, stable, versioned schema suitable for NBT/JSON encode:
    - `version` (int)
    - `uuid` (string)
    - `name` (string)
    - `type` (enum)
    - `pos` (double[3])
    - `color` (int ARGB)
    - `lookAt` (nullable double[3])
    - `areas` (list): each with
      - `shape` (enum)
      - `center` (double[3])
      - `minRadius`/`maxRadius` (double) [legacy]
      - `advanced` (bool)
      - `minRadii`/`maxRadii` (nullable double[3])
      - `filters` (bitfield or explicit booleans)
      - `easing` (enum)
- Schema evolution: retain `version`, and on load, migrate old fields (e.g., missing `advanced` defaults false; missing per‑axis radii null).

Networking (Fabric Networking API - CustomPayload System)

**Key Changes in 1.21.10:**
- The networking API has been completely rewritten to use `CustomPayload` interface (vanilla Minecraft)
- `PacketByteBuf` is replaced by `PacketCodec` for serialization
- Payload classes must be Java Records implementing `CustomPayload`
- Payloads must be registered using `PayloadTypeRegistry` before use
- `ServerPlayNetworking.send()` now takes `CustomPayload` objects instead of `(Identifier, PacketByteBuf)`
- `ServerPlayNetworking.registerGlobalReceiver()` now takes `CustomPayload.Id<T>` and `PlayPayloadHandler<T>`

**Payload Classes (Java Records)**
Create payload record classes for each packet type. Each payload must:
- Be a Java Record implementing `CustomPayload`
- Have a static `CustomPayload.Id<T>` field identifying the payload type
- Have a static `PacketCodec<RegistryByteBuf, T>` field for serialization
- Override `getId()` to return the payload ID

Example payload structure:
```java
public record ChunkNodesPayload(
    Identifier dimensionId,
    int chunkX,
    int chunkZ,
    List<CameraNodeDTO> nodes
) implements CustomPayload {
    public static final CustomPayload.Id<ChunkNodesPayload> ID = 
        new CustomPayload.Id<>(Identifier.of("craneshot", "chunk_nodes"));
    
    public static final PacketCodec<RegistryByteBuf, ChunkNodesPayload> CODEC = 
        PacketCodec.tuple(
            Identifier.PACKET_CODEC, ChunkNodesPayload::dimensionId,
            PacketCodecs.INTEGER, ChunkNodesPayload::chunkX,
            PacketCodecs.INTEGER, ChunkNodesPayload::chunkZ,
            CameraNodeDTO.PACKET_CODEC.collect(PacketCodecs.toList()), ChunkNodesPayload::nodes,
            ChunkNodesPayload::new
        );
    
    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
```

**Required Payload Types:**
- `ChunkNodesPayload` — S2C: full set of nodes for a chunk
- `NodesDeltaPayload` — S2C: add/update/remove nodes in a chunk
- `EditRequestPayload` — C2S: client requests to add/update/remove nodes
- `HandshakePayload` — Bidirectional: capability/version exchange on join

**PacketCodec Utilities:**
Use built-in codecs from `PacketCodecs` class:
- Primitives: `INTEGER`, `FLOAT`, `DOUBLE`, `BOOLEAN`, `STRING`
- UUID: `Uuids.PACKET_CODEC`
- BlockPos: `BlockPos.PACKET_CODEC`
- Identifier: `Identifier.PACKET_CODEC`
- Lists: `.collect(PacketCodecs.toList())`
- Optional: `.collect(PacketCodecs::optional)`
- Custom objects: Create `PacketCodec.tuple()` or use `PacketCodec.of((buf, obj) -> {...}, buf -> {...})`

For complex nested DTOs like `CameraNodeDTO` and `AreaDTO`, create static `PACKET_CODEC` fields:
```java
public record CameraNodeDTO(...) {
    public static final PacketCodec<RegistryByteBuf, CameraNodeDTO> PACKET_CODEC = 
        PacketCodec.tuple(
            Uuids.PACKET_CODEC, CameraNodeDTO::uuid,
            PacketCodecs.STRING, CameraNodeDTO::name,
            // ... other fields
            CameraNodeDTO::new
        );
}
```

**Registration**
Register all payloads in the common mod initializer using `PayloadTypeRegistry`:
```java
// In ModInitializer.onInitialize():
// Server-to-Client payloads
PayloadTypeRegistry.playS2C().register(ChunkNodesPayload.ID, ChunkNodesPayload.CODEC);
PayloadTypeRegistry.playS2C().register(NodesDeltaPayload.ID, NodesDeltaPayload.CODEC);
PayloadTypeRegistry.playS2C().register(HandshakePayload.ID, HandshakePayload.CODEC);

// Client-to-Server payloads
PayloadTypeRegistry.playC2S().register(EditRequestPayload.ID, EditRequestPayload.CODEC);
PayloadTypeRegistry.playC2S().register(HandshakePayload.ID, HandshakePayload.CODEC);
```

**Receiving Packets**
Register receivers using `ServerPlayNetworking.registerGlobalReceiver()` and `ClientPlayNetworking.registerGlobalReceiver()`:
```java
// Server-side (in ModInitializer):
ServerPlayNetworking.registerGlobalReceiver(EditRequestPayload.ID, (payload, context) -> {
    // context.player() gives ServerPlayerEntity
    // Handler runs on server thread - no need for execute()
    ServerPlayerEntity player = context.player();
    // Validate and process edit request
});

// Client-side (in ClientModInitializer):
ClientPlayNetworking.registerGlobalReceiver(ChunkNodesPayload.ID, (payload, context) -> {
    // context.client() gives MinecraftClient
    // Handler runs on netty thread - use execute() for client modifications
    context.client().execute(() -> {
        // Update client cache with chunk nodes
    });
});
```

**Sending Packets**
Send payloads using `ServerPlayNetworking.send()` and `ClientPlayNetworking.send()`:
```java
// Server to client:
ChunkNodesPayload payload = new ChunkNodesPayload(dimensionId, chunkX, chunkZ, nodes);
ServerPlayNetworking.send(player, payload);

// Client to server:
EditRequestPayload payload = new EditRequestPayload(...);
ClientPlayNetworking.send(payload);
```

**Payload Design Considerations:**
- Use `RegistryByteBuf` instead of `PacketByteBuf` for access to registry manager
- Keep payloads small: batch operations but avoid huge lists
- Version field in payloads for future compatibility
- Consider using optional fields with `PacketCodecs::optional`
- Validate all data in receivers (ranges, permissions, rate limits)

- Ordering
  - On player join or dimension change, server sends `chunk_nodes` for all chunks the player starts tracking.
  - On chunk load: send `chunk_nodes` to players tracking that chunk.
  - On node edits: send `nodes_delta` to all tracking players immediately.
  - On chunk unload: client implicitly drops nodes when it stops tracking (client keeps an index keyed by chunk pos); we can also send an explicit empty `chunk_nodes` if needed.

Chunk Tracking
- Use Fabric helpers and events:
  - `ServerChunkEvents.CHUNK_LOAD/UNLOAD` to know when chunks become available.
  - `PlayerLookup.tracking(WorldChunk)` to obtain recipients for packet broadcasting.
  - `ServerPlayConnectionEvents.JOIN` for handling player join and sending initial data.
  - `ServerPlayConnectionEvents.DISCONNECT` for cleanup if needed.
- On chunk load: create `ChunkNodesPayload` and send to all tracking players.
- On chunk unload: optionally send empty payload or rely on client implicit cleanup.
- Client maintains: `Map<ChunkPos, List<CameraNode>>` per dimension for rendering/influence. Drop entries when chunks unload or when empty payload received. Drop entries on unload.

Authority and IDs
- UUIDs are assigned by the server on create.
- Client edit requests include a temporary client UUID when creating; server responds with authoritative UUID mapping in the delta.
- Server validates all updates against authoritative UUIDs; rejects unknown IDs.

Permissions and Security
- Default policy: editing allowed for operators (permission level ≥ 2) and original creator. Configurable via server config (e.g., `craneshot-server.json`).
- Optional allowlist/denylist of players.
- Server validates:
  - Value ranges (radii ≥ 0; per‑axis consistency `max ≥ min`).
  - Node stays within reasonable bounds (e.g., |pos| < 3e7 to match MC limits).
  - Rate limit: cap requests per tick per player to avoid spam.
- Audit logging:
  - Log to server log: player, action, node UUID, chunk, before/after summary.
  - Optional daily rotating file `logs/craneshot_audit.log`.

Editing UX (Client with Server present)
- Detect server capability via `ServerPlayNetworking.canSend(player, HandshakePayload.ID)` or handshake exchange.
- If server mod present:
  - Disable client-side save for the active world; NodeManager becomes a client cache populated from server packets.
  - NodeEditorScreen actions create and send `EditRequestPayload` instances. Local UI can update optimistically, but final state follows server echo via `NodesDeltaPayload`.
  - Export/Import in the client editor remains local convenience; importing sends a batch of `EditRequestPayload` packets chunk-by-chunk (with server validation).

Operations
- Create node: Client creates `EditRequestPayload` with operation type CREATE, sends to server. Server validates, assigns UUID, adds to chunk bucket, persists state, creates `NodesDeltaPayload` and broadcasts to tracking players.
- Update node: Client creates `EditRequestPayload` with operation type UPDATE and changed fields. If node moved across chunk boundary, server removes from old bucket and inserts into new; broadcasts `NodesDeltaPayload` for both chunks if needed.
- Delete node: Client creates `EditRequestPayload` with operation type DELETE. Server removes from bucket and broadcasts `NodesDeltaPayload`.
- Bulk operations: Import maps to repeated `EditRequestPayload` instances; consider a batch mode or multiple operations in a single payload to reduce packet overhead.

Client Cache and Rendering
- On receive `ChunkNodesPayload`: Replace the client's cache for that chunk (idempotent, handles re-send). Handler should use `context.client().execute()` to modify client state on the render thread.
- On `NodesDeltaPayload`: Apply precise mutations to the cached nodes. Use `context.client().execute()` for thread-safe updates.
- Rendering already culls by chunk render distance; cache keyed by chunk aligns with streaming model.

Singleplayer/Integrated Server
- Same server path applies; integrated server hosts the authoritative registry and networking loopback to the client.
- Persistence via PersistentState in the save.

Failure Modes and Recovery
- If client misses packets (e.g., disconnect mid-stream), next chunk load or a periodic reconciliation can resend `ChunkNodesPayload` for tracked chunks.
- On version mismatch: Check payload versions in handshake. If incompatible, client falls back to client-only mode.
- Missing channel detection: Use `ServerPlayNetworking.canSend(player, PayloadID)` to check if client can receive specific payloads. If server mod absent, client behaves as today (per-world or config save on the client) and shows a status indicator in the editor.

Data Migration
- On first server install, attempt to import any existing client per‑world file into the server state (admin command).
- Provide `/craneshot import|export` commands for operators to manage data from disk JSON.

Commands (optional)
- `/craneshot give <player> editor` — grant edit permission.
- `/craneshot revoke <player> editor`
- `/craneshot import [path]` — load nodes from a JSON; distribute to chunks.
- `/craneshot export [path]` — write current server nodes to a JSON.
- `/craneshot clear [radius|all]` — remove nodes in a radius or all (with confirmation).

Packet Size and Batching
- Typical chunk should carry only a handful of nodes; still batch multiple node operations into single payloads per tick and per chunk to avoid flooding the network.
- Clamp area counts per node (soft limits) to keep payloads small; enforce server-side validation.
- Use efficient `PacketCodec` implementations - avoid unnecessary string serialization, prefer primitive types.
- Consider compression for large lists (e.g., if a chunk has many nodes, could use NBT compression via `PacketCodecs.nbt()`).

Testing Plan (dev)
- Singleplayer: create/edit/delete nodes and ensure save/load round‑trips.
- Multiplayer (server with mod): observe chunk‑based streaming as players move; permission enforcement; audit logs.
- Migration: import from client JSON; verify chunk grouping and persistence.

Migration Notes from Pre-1.21 Networking
**Breaking Changes Summary:**
- No more direct `PacketByteBuf` manipulation in `ServerPlayNetworking.send()` or `.registerGlobalReceiver()`
- Must create Java Record classes implementing `CustomPayload` for all packet types
- Payloads must be registered with `PayloadTypeRegistry` before sending/receiving
- Receivers now get typed payload objects instead of `PacketByteBuf`
- `ServerPlayNetworking.send()` signature: `send(ServerPlayerEntity, CustomPayload)` instead of `send(ServerPlayerEntity, Identifier, PacketByteBuf)`
- `registerGlobalReceiver()` signature: `registerGlobalReceiver(CustomPayload.Id<T>, PlayPayloadHandler<T>)` instead of `registerGlobalReceiver(Identifier, PlayChannelHandler)`

**Migration Steps:**
1. Create payload record classes for each packet type
2. Define `PacketCodec` for each payload using `PacketCodec.tuple()` or custom codec
3. Register payloads in mod initializer with `PayloadTypeRegistry`
4. Update all `ServerPlayNetworking.send()` calls to use payload objects
5. Update all receiver registrations to use typed handlers
6. Update DTOs to include `PacketCodec` fields if they're serialized over network
7. Replace any manual `PacketByteBuf` read/write code with codec-based serialization

**Thread Safety Notes:**
- `ServerPlayNetworking.PlayPayloadHandler` runs on the **server thread** - direct modifications are safe
- `ClientPlayNetworking.PlayPayloadHandler` runs on the **netty thread** - must use `context.client().execute()` for client state modifications
- This differs from pre-1.20.5 where all handlers ran on netty thread

**Testing Checklist:**
- Verify payloads are registered before any networking occurs
- Test S2C packets with multiple clients to ensure all tracking players receive updates
- Test C2S packets with permission validation
- Test chunk loading/unloading with player movement
- Test reconnection and chunk re-sync
- Verify thread safety of client handlers (use `execute()`)

Open Questions (to finalize in implementation)
- Exact server config surface: permission model, limits, audit verbosity.
- Whether to send explicit unload messages vs relying on client to drop on chunk unload events.
- Optimal structure for `EditRequestPayload` - single operation per payload vs batch operations.
- Command surface breadth vs minimal first pass.

## Reference Implementation Examples

### Example: Complete Payload Class
```java
public record NodesDeltaPayload(
    Identifier dimensionId,
    int chunkX,
    int chunkZ,
    List<NodeOperation> operations
) implements CustomPayload {
    
    public static final CustomPayload.Id<NodesDeltaPayload> ID = 
        new CustomPayload.Id<>(Identifier.of("craneshot", "nodes_delta"));
    
    public static final PacketCodec<RegistryByteBuf, NodesDeltaPayload> CODEC = 
        PacketCodec.tuple(
            Identifier.PACKET_CODEC, NodesDeltaPayload::dimensionId,
            PacketCodecs.INTEGER, NodesDeltaPayload::chunkX,
            PacketCodecs.INTEGER, NodesDeltaPayload::chunkZ,
            NodeOperation.PACKET_CODEC.collect(PacketCodecs.toList()), NodesDeltaPayload::operations,
            NodesDeltaPayload::new
        );
    
    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
    
    public enum OperationType {
        ADD, UPDATE, REMOVE
    }
    
    public record NodeOperation(
        OperationType type,
        UUID nodeId,
        Optional<CameraNodeDTO> nodeData
    ) {
        public static final PacketCodec<RegistryByteBuf, NodeOperation> PACKET_CODEC = 
            PacketCodec.tuple(
                PacketCodecs.indexed(OperationType::values, OperationType::ordinal), NodeOperation::type,
                Uuids.PACKET_CODEC, NodeOperation::nodeId,
                CameraNodeDTO.PACKET_CODEC.collect(PacketCodecs::optional), NodeOperation::nodeData,
                NodeOperation::new
            );
    }
}
```

### Example: Custom PacketCodec for Complex Objects
```java
public record Vec3d(double x, double y, double z) {
    public static final PacketCodec<RegistryByteBuf, Vec3d> PACKET_CODEC = 
        PacketCodec.of(
            (buf, vec) -> {
                buf.writeDouble(vec.x);
                buf.writeDouble(vec.y);
                buf.writeDouble(vec.z);
            },
            buf -> new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble())
        );
}
```

### Example: Server-Side Receiver with Validation
```java
ServerPlayNetworking.registerGlobalReceiver(EditRequestPayload.ID, (payload, context) -> {
    ServerPlayerEntity player = context.player();
    
    // Validate permissions
    if (!hasEditPermission(player, payload.nodeId())) {
        LOGGER.warn("Player {} attempted unauthorized edit", player.getName().getString());
        return;
    }
    
    // Validate data ranges
    if (!isValidPosition(payload.position())) {
        LOGGER.warn("Player {} sent invalid position", player.getName().getString());
        return;
    }
    
    // Process edit
    CameraNodesState state = CameraNodesState.getOrCreate(player.getServerWorld());
    state.applyEdit(payload);
    
    // Broadcast to tracking players
    ChunkPos chunkPos = new ChunkPos(new BlockPos(payload.position()));
    WorldChunk chunk = player.getServerWorld().getChunk(chunkPos.x, chunkPos.z);
    NodesDeltaPayload deltaPayload = createDeltaPayload(payload);
    
    for (ServerPlayerEntity trackingPlayer : PlayerLookup.tracking(chunk)) {
        ServerPlayNetworking.send(trackingPlayer, deltaPayload);
    }
});
```

### Example: Client-Side Receiver with Thread Safety
```java
ClientPlayNetworking.registerGlobalReceiver(ChunkNodesPayload.ID, (payload, context) -> {
    // This runs on netty thread - must use execute() for client modifications
    context.client().execute(() -> {
        ClientNodeManager nodeManager = ClientNodeManager.getInstance();
        ChunkPos chunkPos = new ChunkPos(payload.chunkX(), payload.chunkZ());
        
        // Update cache
        nodeManager.setChunkNodes(payload.dimensionId(), chunkPos, payload.nodes());
        
        // Trigger any necessary re-renders
        MinecraftClient.getInstance().worldRenderer.scheduleTerrainUpdate();
    });
});
```

## Additional Resources
- **Fabric Networking Documentation:** https://docs.fabricmc.net/develop/networking
- **PacketCodec Tutorial:** https://wiki.fabricmc.net/tutorial:codec
- **Fabric 1.20.5 Migration Guide:** https://fabricmc.net/2024/04/19/1205.html (CustomPayload changes)
- **Fabric 1.21.9/1.21.10 Changes:** https://fabricmc.net/2025/09/23/1219.html (Entity API changes)
- **Fabric Wiki Networking:** https://wiki.fabricmc.net/tutorial:networking

