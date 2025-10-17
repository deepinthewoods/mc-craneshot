Lines and Block Outlines Rendering (Fabric 1.21.10)

Overview
- Goal: render 3D lines and per-block wireframe outlines in world space, over terrain, using the new ordered world-render pipeline in Minecraft 1.21.10 (Fabric API 0.135.0, Loader 0.17.3, Yarn 1.21.10+build.2).
- Approach: inject into `WorldRenderer.pushEntityRenders`, and submit custom vertices via `OrderedRenderCommandQueue` using the `RenderLayer.getSecondaryBlockOutline()` layer so lines/outlines appear clearly on top of blocks.
- Data model: the client keeps a small, thread‑safe store of line endpoints per entity id, plus an optional anchor for preview paths.

Key Pieces
- Injection point: `WorldRenderer.pushEntityRenders` (client mixin) so our draw occurs during world rendering with access to camera, matrices, and the ordered command queue.
- Layer: `RenderLayer.getSecondaryBlockOutline()` gives an outline-like line layer that renders visibly over terrain.
- Submission: use `queue.getBatchingQueue(priority).submitCustom(matrices, layer, (entry, vc) -> { … })` to push pairs of vertices per line segment to the layer’s `VertexConsumer`.
- Coordinates: emit vertices in camera-relative space: subtract `cameraPos` from world coordinates; use the provided `MatrixStack.Entry` for transforms, normals, and light.
- Lighting: use a full-bright packed light like `0x00F000F0` for consistent line visibility.

Files in This Project
- Client state store: `src/client/java/ninja/trek/mc/goldgolem/client/state/ClientState.java`
- Renderer mixin: `src/client/java/ninja/trek/mc/goldgolem/mixin/client/WorldRendererMixin.java`
- Mixin registration: `src/client/resources/gold-golem.client.mixins.json`

Hooking Into World Rendering
- Mixin target: `WorldRenderer.pushEntityRenders(MatrixStack, WorldRenderState, OrderedRenderCommandQueue)`.
- In our injection we:
  - Early‑out unless a simple debug condition is met (e.g., player holding a gold nugget) so the overlay is opt‑in during development.
  - Snapshot `ClientState` line data (entityId → list of endpoints and optional anchor).
  - Cache camera position (`renderStates.cameraRenderState.pos`) to convert world space → camera space.

Submitting Lines
- For each logical segment (pair of endpoints), we optionally expand it into a “stepped” polyline that hugs terrain (see “Stepped Path on Terrain”), then submit each adjacent pair as a small line segment.
- We batch per segment/path using `queue.getBatchingQueue(1000)` and `submitCustom` with `RenderLayer.getSecondaryBlockOutline()`.
- Each segment submits exactly two vertices with color, normal, and packed light.

Minimal Pattern (pseudocode)
1) Compute camera-relative endpoints:
   - `vx = x - camX`, `vy = y - camY`, `vz = z - camZ`.
2) Choose the layer:
   - `RenderLayer layer = RenderLayer.getSecondaryBlockOutline();`
3) Submit inside `pushEntityRenders` using the ordered queue:
   - `var bq = queue.getBatchingQueue(1000);`
   - `bq.submitCustom(matrices, layer, (entry, vc) -> {`
     - `int light = 0x00F000F0;`
     - `vc.vertex(entry, ax, ay, az).color(r,g,b,a).normal(entry, 0,1,0).light(light);`
     - `vc.vertex(entry, bx, by, bz).color(r,g,b,a).normal(entry, 0,1,0).light(light);`
   - `});`

Stepped Path on Terrain
- Purpose: instead of straight lines through space, produce polylines that “walk” across the tops of solid blocks, inserting vertical segments where height changes.
- Surface detection: for a given column `(bx, bz)` and a reference `y0`, scan up/down a small window to find the topmost solid full-cube block and treat the surface as `groundY + 1.0`.
- Grid traversal: walk from `A` to `B` in XZ using a 2D DDA over grid boundaries, sampling columns as we go. For each new column, compute its surface Y:
  - If the surface exists and differs from the last vertex Y, insert a vertical segment to that Y; otherwise continue.
- Endpoint fixup: ensure the final vertex lands on the destination column’s surface.

Block Outlines Across Path Width
- For the “current” segment, we draw a full block-outline footprint across the configured path width (odd width, clamped range).
- Supercover cells: compute Bresenham cells between segment endpoints in XZ; for each visited cell, expand sideways by `half = (width - 1)/2` to cover the full width.
- Per-cell outline: for each center (and width offsets), find the surface Y and emit the 12 edge segments of a unit cube wireframe between `[x, y-1, z]` and `[x+1, y, z+1]` using the same outline layer.

Coloring & Preview
- Coloring: queued segments use a dark orange; the nearest segment to the entity ("current") uses a dark green; preview paths use a gray that fades toward white near the player for visual affordance.
- Preview: if the path has fewer than 2 points, a temporary path is built from the last point or anchor toward the player position using the same stepped-path algorithm and submitted with a per-vertex fade.

Performance & Ordering Notes
- `RenderLayer.getSecondaryBlockOutline()` is lightweight and designed for line rendering similar to block selection outlines. It renders late enough that lines remain visible over terrain.
- Using the ordered command queue (`OrderedRenderCommandQueue`) is future‑proof with the modern render pipeline and avoids direct immediate-mode or deprecated buffers.
- Keep submissions minimal: two vertices per line segment, reuse batching queues per frame/segment, and early‑out when not needed (e.g., when the overlay is disabled).

How To Reuse This in Another Mod
- Client setup
  - Create a client‑only mixin targeting `WorldRenderer.pushEntityRenders`.
  - Register your mixin in your client mixin JSON.
  - Maintain a simple client-side store of line endpoints (optionally keyed by entity or feature id).
- Rendering
  - In your injection, obtain camera position from `WorldRenderState`.
  - Convert world → camera space per vertex.
  - Choose `RenderLayer.getSecondaryBlockOutline()` for clean line rendering.
  - Submit pairs of vertices per segment using `queue.getBatchingQueue(priority).submitCustom`.
  - For block footprints, iterate target columns, compute surface Y, and emit a cube wireframe by submitting its 12 edges as 12 line segments.
- Optional UX
  - Gate drawing on a held item or toggle to keep visuals opt-in.
  - Add preview paths with simple fades near the player for clarity.

Relevant APIs (Fabric 0.135.0 / Loader 0.17.3 / Yarn 1.21.10)
- Client world rendering
  - `net.minecraft.client.render.WorldRenderer`
  - `net.minecraft.client.render.state.WorldRenderState`
  - `net.minecraft.client.render.command.OrderedRenderCommandQueue`
  - `net.minecraft.client.util.math.MatrixStack`
- Layers & vertices
  - `net.minecraft.client.render.RenderLayer#getSecondaryBlockOutline()`
  - `net.minecraft.client.render.VertexConsumer`
- Math & positions
  - `net.minecraft.util.math.Vec3d`
  - `net.minecraft.util.math.BlockPos`
  - `net.minecraft.util.math.MathHelper`

Notes
- The technique intentionally avoids deprecated immediate buffers and raw Tessellator use in favor of the ordered render queue and layer system.
- If you need true 3D lines with varying thickness, consider a custom `RenderLayer` and expanded quads; for selection/guide visuals, the outline layer is often sufficient and integrates well with depth/ordering.

