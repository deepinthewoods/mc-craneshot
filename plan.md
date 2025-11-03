# Craneshot Area/Node/Movement Refactor – Implementation Plan

## 1. Data Model & Persistence
- [ ] Introduce a top-level `Area` collection owned by `NodeManager` (e.g., `List<AreaInstance>`).
- [ ] Create a new model class (e.g., `AreaInstance`) encapsulating:
  - Area shape/size/easing/filter data (reuse existing `Area` or merge DTO responsibilities).
  - Collection of configured `CameraMovement` instances; each movement handles its own node references (e.g., `StaticMovement` stores position/look node UUIDs).
- [ ] Remove embedded `areas` list and `lookAt` vector from `CameraNode`; adjust DTOs (`CameraNode`, `CameraNodeDTO`, `AreaDTO`) accordingly.
- [ ] Update serialization:
  - Expand `NodeStorage` JSON format to persist separated areas (new file structure or extended object containing `nodes` + `areas`).
  - Adjust import/export to include areas.
  - Update network DTOs if multiplayer sync uses them (create `AreaInstanceDTO` if needed).
- [ ] Confirm no migration path is required (per instructions); assume clean saves/worlds.

## 2. Node Manager Core Logic
- [ ] Refactor `NodeManager` to:
  - Manage independent node and area lists (add CRUD helpers for areas).
  - Update selection logic to accommodate selecting nodes vs. areas if needed.
  - Rework `addAreaTo`/`removeArea` APIs to operate on top-level area list.
- [ ] Replace `applyInfluence` logic:
  - Areas now own camera movements; nodes become point references only.
  - Each area evaluates filters/influence and updates its assigned `CameraMovement` instances.
  - Determine interpolation using area-managed movements instead of direct node blending.
- [ ] Remove legacy look-at blending path; rely on `StaticMovement` orientation.
- [ ] Ensure `getTotalInfluence` or equivalent still reports influence levels for HUD/activation.

## 3. Camera Movement Integration
- [ ] Define new `StaticMovement` implementing `ICameraMovement`:
  - Stores UUID references to position/look nodes and resolves them through `NodeManager`.
  - Each tick: position camera at position node, orient toward look node (if present).
  - Provide configuration hooks for easing/blend weights if required.
- [ ] Update `CameraMovementRegistry` to register `StaticMovement`.
- [ ] Adjust `CameraMovementManager`:
  - Allow areas to supply a filtered list of movements by movement type.
  - Clarify how movement types map to enum/buttons; ensure slots pick up new movement class.
- [ ] Ensure `StaticMovement` cooperates with blending framework (weights, completion rules, `RaycastType` etc.).

## 4. Area-Driven Movement Pipeline
- [ ] Create structure for per-area movement assignments filtered by player state (e.g., map `MovementStateType -> List<ICameraMovement>`).
- [ ] On tick:
  - Evaluate player state filters (reuse existing logic).
  - Activate relevant movements; manage lifecycle (start/reset) per area.
  - Feed resulting `CameraTarget` into interpolation pipeline replacing node-position averaging.
- [ ] Decide blending order: combine outputs from all active movements (existing blending utilities?) and integrate with `CameraSystem`.

## 5. UI Updates
- [ ] Update `NodeEditorScreen`:
  - Remove “Set/Unset LookAt”; provide UI for managing areas separate from nodes.
  - Present global area list (since areas are top-level).
- [ ] Revamp `AreaSettingsModal`:
  - Use dedicated checkbox widgets for movement filters (new reusable UI component).
  - Add controls on movement editors (e.g., StaticMovement panel) to assign position/look nodes (dropdowns/pickers).
  - Manage per-area movement list editing (add/remove movement types, configure StaticMovement targets).
- [ ] Ensure HUD (`NodeAreaHudRenderer`) consumes new data structures (influence, names, colors).

## 6. Rendering & Editing Experience
- [ ] Update `NodeRenderer`:
  - Draw areas using top-level area list (lines from position node to area center if applicable).
  - Remove references to node `lookAt`.
- [ ] Allow editor interactions (selection, duplication, deletion) to handle independent areas.
- [ ] Ensure `NodeManager.isEditing()` workflows still gate rendering/UI toggles.

## 7. Miscellaneous & Validation
- [ ] Review mixins or external hooks touching nodes/areas to align with new APIs.
- [ ] Update documentation/config defaults if they reference look-at or node-owned areas.
- [ ] Manually validate:
  - Creating/deleting nodes & areas.
  - Assigning StaticMovement targets.
  - Verifying camera follows new pipeline during gameplay.
  - UI usability (checkboxes, selection widgets).
- [ ] Prepare follow-up notes for potential compatibility breaks (old saves unsupported).
