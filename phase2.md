## Phase 2 Follow-Ups

### 1. Runtime State Filters for Area Movements
- **Goal:** Make `AreaMovementConfig.stateFilters` meaningful so the movement resolver only activates when the player matches the configured conditions.
- **Work:** Define a canonical set of player state keys (e.g. `walking`, `sneaking`, `elytra`, `swimming`, `boat`, `minecart`, `riding_other`, `crawling_1_block`). Extend `NodeManager.collectPlayerStates()` (new helper) to translate the live player flags we already evaluate in `isAreaEligibleForPlayer` into this vocabulary.
- **Integration:** Replace the `passesStateFilters` stub with logic that compares the active state set to the filters list (all/any matching – confirm UX). Update `AreaSettingsModal` so each movement exposes checkboxes for these filters, storing the chosen states in `AreaMovementConfig.stateFilters`.
- **Validation:** Add a guarded debug log or HUD hint to confirm when filters suppress/allow movements. Ensure serialization/export/import already handles the string lists (Gson can persist them as-is).

### 2. Multiplayer Synchronisation of Areas
- **Goal:** Carry the new top-level areas (and their movement configs) through server storage and networking so dedicated/lan players stay in sync.
- **Server Storage:** Introduce a persisted data structure parallel to `CameraNodesState` that records `AreaInstance` data keyed by world/dimension. Alternatively, extend the existing state file to store areas alongside nodes – but adjust rate limiting and validation (radius ranges, movement type allowlist, node reference checks).
- **Networking:** Define DTOs and packet flows for CRUD operations (create/update/delete areas) similar to node messages. Ensure movement configs reference node UUIDs that exist on both ends; guard against invalid UUIDs before applying.
- **Client Hooks:** When server mode is active, NodeManager should delegate area mutations to networking handlers instead of writing local storage. On disconnect, reload local data to avoid stale shared state bleeding into singleplayer sessions.
- **Security/Permissions:** Mirror the node permission checks (owner vs. operator) to areas. Consider per-area owners for edit/delete rights.
- **Validation:** Add logging when area packets are dropped due to invalid references; consider a `/craneshot debug sync` command to dump area counts per dimension. Use manual smoketest: create areas on server, reconnect client, verify persistence after restart.
