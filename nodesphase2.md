Nodes: Phase 2 Plan and Deferred Items

Scope: follow-ups and stubs from camera nodes feature (phase 1 shipped minimal viable editor, rendering, blending, and DroneShot basics).

Deferred/Planned
- Screen-space selection:
  - Phase 1 picks nearest by 3D distance. Upgrade to true screen-space projection with frustum/project matrices or ray cast against billboard bounds.

- Dashed lines and chevrons:
  - Phase 1 renders solid lines/circles. Add dashed line patterns (animated) and >> chevrons along LookAt links.

- Node visuals (filled):
  - Phase 1 uses simple cross/outlines. Explore filled quads via custom layer if outline layer cannot fill.

- Area advanced settings modal:
  - Add per-axis radii and position labels (x/y/z) with left/right/Ctrl/Shift click increments.

- Movement filters:
  - Phase 1 treats all movement types as eligible. Add filters for walking, elytra, minecart, riding ghast, riding other, boat, swimming, crawling.

- UI polish:
  - Add list UI with clickable areas opening AreaSettingsModal; include delete buttons per area inline.
  - Add node rename, duplicate, and copy/paste color.
  - Improve color picker (sliders for H/S/V, preview swatch, numeric inputs).

- DroneShot enhancements:
  - Expose radius and speed controls in NodeEditorScreen.
  - Optional height oscillation and easing.
  - Start angle scrubber.

- Influence smoothing:
  - Phase 1 uses linear blending. Add per-node easing curves and temporal smoothing to avoid abrupt changes.

- Persistent per-world data and server sync:
  - Currently saves to client config `config/craneshot_nodes.json`.
  - Add per-world save (e.g., under `saves/<world>/craneshot_nodes.json`).
  - Server-side storage when mod present; packets to sync (add/update/remove nodes/areas; full sync on join).

- Security and permissions:
  - Server config for who can edit nodes; audit logging.

- Performance:
  - Batch render submissions; cull offscreen nodes/areas; reduce per-frame allocations.

- Testing/dev UX:
  - Toggle to show/hide overlays outside edit mode.
  - Export/import node sets.

