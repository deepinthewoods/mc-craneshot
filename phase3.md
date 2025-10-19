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

Networking (Fabric ServerPlayNetworking)
- Channels
  - `craneshot:chunk_nodes` — full set of nodes for a single chunk (server→client).
  - `craneshot:nodes_delta` — add/update/remove one or more nodes in a chunk (server→client).
  - `craneshot:edit_request` — client→server to add/update/remove; server validates perms and echoes via delta.
  - `craneshot:handshake` — optional capability/version exchange on join.
- Payloads (PacketByteBuf)
  - Chunk header: `dimension_id` (Identifier or raw RegistryKey), `chunk_x` (int), `chunk_z` (int).
  - For each node: fields per DTO. Avoid GSON; write primitives directly for efficiency.
- Ordering
  - On player join or dimension change, server sends `chunk_nodes` for all chunks the player starts tracking.
  - On chunk load: send `chunk_nodes` to players tracking that chunk.
  - On node edits: send `nodes_delta` to all tracking players immediately.
  - On chunk unload: client implicitly drops nodes when it stops tracking (client keeps an index keyed by chunk pos); we can also send an explicit empty `chunk_nodes` if needed.

Chunk Tracking
- Use Fabric helpers:
  - `ServerChunkEvents.CHUNK_LOAD/UNLOAD` to know when chunks become available.
  - `PlayerLookup.tracking(WorldChunk)` to obtain recipients.
  - Also listen to player start/stop tracking events if needed (e.g., `ServerPlayConnectionEvents.JOIN`, `PlayerBlockBreakEvents` not required) — or rely on periodic reconciliation.
- Client maintains: `Map<ChunkPos, List<CameraNode>>` per dimension for rendering/influence. Drop entries on unload.

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
- Detect server capability via handshake; if present:
  - Disable client‐side save for the active world; NodeManager becomes a client cache populated from server packets.
  - NodeEditorScreen actions send `edit_request` packets. Local UI updates optimistically, but final state follows server echo.
  - Export/Import in the client editor remains local convenience; importing sends a batch of create requests chunk‑by‑chunk (with server validation).

Operations
- Create node: client→server (pos, fields, areas). Server puts node in chunk bucket, persists state, and broadcasts delta.
- Update node: client→server (uuid + changed fields). If node moved across a chunk boundary, server removes from old bucket and inserts into new; broadcast both deltas.
- Delete node: client→server (uuid). Server removes and broadcasts.
- Bulk operations: import maps to repeated creates; consider a `bulk` flag to reduce packet overhead.

Client Cache and Rendering
- On receive `chunk_nodes`: replace the client’s cache for that chunk (idempotent, handles re‑send).
- On `nodes_delta`: apply precise mutations.
- Rendering already culls by chunk render distance; cache keyed by chunk aligns with streaming model.

Singleplayer/Integrated Server
- Same server path applies; integrated server hosts the authoritative registry and networking loopback to the client.
- Persistence via PersistentState in the save.

Failure Modes and Recovery
- If client misses packets (e.g., disconnect mid‑stream), next chunk load or a periodic reconciliation can resend `chunk_nodes` for tracked chunks.
- On version mismatch or missing channel: client falls back to client‑only mode (no server edits) and shows a small status in the editor.

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
- Typical chunk should carry only a handful of nodes; still batch deltas per tick and per chunk to avoid floods.
- Clamp area counts per node (soft limits) to keep payloads small; enforce server‑side.

Testing Plan (dev)
- Singleplayer: create/edit/delete nodes and ensure save/load round‑trips.
- Multiplayer (server with mod): observe chunk‑based streaming as players move; permission enforcement; audit logs.
- Migration: import from client JSON; verify chunk grouping and persistence.

Open Questions (to finalize in implementation)
- Exact server config surface: permission model, limits, audit verbosity.
- Whether to send explicit unload messages vs relying on client to drop on chunk unload events.
- Command surface breadth vs minimal first pass.

