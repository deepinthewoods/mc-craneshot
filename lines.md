# Line Rendering in Craneshot

This document summarizes how line rendering is implemented in the Craneshot mod for Minecraft.

## Overview

The mod renders 3D lines in the world to visualize camera nodes and area boundaries. Lines are rendered using Minecraft's modern rendering pipeline with proper depth testing and transparency support.

## Architecture

### 1. Render Pipeline Integration

**File**: `src/client/java/ninja/trek/mixin/client/WorldRendererMixin.java`

The mod hooks into Minecraft's world rendering using a Mixin:

```java
@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
    @Inject(
        method = "pushEntityRenders(...)",
        at = @At("TAIL")
    )
    private void craneshot$renderNodes(MatrixStack matrices,
                                       WorldRenderState renderStates,
                                       OrderedRenderCommandQueue queue,
                                       CallbackInfo ci) {
        NodeRenderer.render(matrices, renderStates, queue);
    }
}
```

**Key Points:**
- Injects at the **TAIL** of `pushEntityRenders` method
- Receives `MatrixStack` for transformations
- Receives `OrderedRenderCommandQueue` for submitting render commands
- Receives `WorldRenderState` for accessing render state

### 2. Core Line Rendering

**File**: `src/client/java/ninja/trek/nodes/render/NodeRenderer.java`

#### Key Method: `submitLine()`

```java
private static void submitLine(OrderedRenderCommandQueue queue,
                               MatrixStack matrices,
                               float r, float g, float b, float a,
                               int light,
                               double ax, double ay, double az,
                               double bx, double by, double bz) {
    RenderLayer layer = RenderLayer.getLines();
    var bq = queue.getBatchingQueue(1000);
    var cam = MinecraftClient.getInstance().gameRenderer.getCamera();
    var rot = cam.getRotation();
    Vector3f forward = new Vector3f(0f, 0f, -1f).rotate(rot);
    final float nx = forward.x, ny = forward.y, nz = forward.z;

    bq.submitCustom(matrices, layer, (entry, vc) -> {
        vc.vertex(entry, (float)ax, (float)ay, (float)az)
          .color(r, g, b, a)
          .normal(entry, nx, ny, nz);
        vc.vertex(entry, (float)bx, (float)by, (float)bz)
          .color(r, g, b, a)
          .normal(entry, nx, ny, nz);
    });
}
```

**Key Concepts:**

1. **RenderLayer**: Uses `RenderLayer.getLines()` for proper line rendering
2. **BatchingQueue**: Gets batching queue with capacity of 1000
3. **Camera-relative normals**: Calculates forward vector from camera rotation
4. **Vertex submission**: Each line consists of 2 vertices with:
   - Position (x, y, z)
   - Color (r, g, b, a)
   - Normal (camera forward direction)

#### Coordinate System

- All positions are **camera-relative** (subtract camera position)
- Coordinates in `submitLine()` are doubles for precision
- Camera position obtained via: `MinecraftClient.getInstance().gameRenderer.getCamera().getPos()`

## Common Rendering Patterns

### 3D Box/Cube Outline

```java
private static void drawCubeOutline(OrderedRenderCommandQueue queue,
                                    MatrixStack matrices,
                                    Vec3d center, double radius,
                                    float r, float g, float b, float a,
                                    int light) {
    double x0 = center.x - radius, x1 = center.x + radius;
    double y0 = center.y - radius, y1 = center.y + radius;
    double z0 = center.z - radius, z1 = center.z + radius;

    // 12 edges of cube
    submitLine(queue, matrices, r, g, b, a, light, x0, y0, z0, x1, y0, z0);
    submitLine(queue, matrices, r, g, b, a, light, x0, y0, z0, x0, y1, z0);
    // ... 10 more edges
}
```

### Billboard Quad (Camera-facing)

```java
private static void drawBillboardQuad(OrderedRenderCommandQueue queue,
                                      MatrixStack matrices,
                                      Vec3d c, float size,
                                      float r, float g, float b, float a,
                                      int light) {
    var cam = MinecraftClient.getInstance().gameRenderer.getCamera();
    var rot = cam.getRotation();

    // Calculate camera-facing right and up vectors
    Vector3f rv = new Vector3f(1, 0, 0).rotate(rot);
    Vector3f uv = new Vector3f(0, 1, 0).rotate(rot);
    Vec3d right = new Vec3d(rv.x, rv.y, rv.z).multiply(size);
    Vec3d up = new Vec3d(uv.x, uv.y, uv.z).multiply(size);

    // Calculate 4 corners
    Vec3d p0 = c.subtract(right).subtract(up);
    Vec3d p1 = c.add(right).subtract(up);
    Vec3d p2 = c.add(right).add(up);
    Vec3d p3 = c.subtract(right).add(up);

    // Draw square outline + diagonals (6 lines total)
    submitLine(queue, matrices, r, g, b, a, light, p0.x, p0.y, p0.z, p1.x, p1.y, p1.z);
    submitLine(queue, matrices, r, g, b, a, light, p1.x, p1.y, p1.z, p2.x, p2.y, p2.z);
    submitLine(queue, matrices, r, g, b, a, light, p2.x, p2.y, p2.z, p3.x, p3.y, p3.z);
    submitLine(queue, matrices, r, g, b, a, light, p3.x, p3.y, p3.z, p0.x, p0.y, p0.z);
    submitLine(queue, matrices, r, g, b, a, light, p0.x, p0.y, p0.z, p2.x, p2.y, p2.z);
    submitLine(queue, matrices, r, g, b, a, light, p1.x, p1.y, p1.z, p3.x, p3.y, p3.z);
}
```

### Ellipse Approximation

```java
private static void drawEllipseApprox(OrderedRenderCommandQueue queue,
                                      MatrixStack matrices,
                                      Vec3d center, double rx, double rz,
                                      float r, float g, float b, float a,
                                      int light) {
    int seg = 28; // 28 segments for smooth circle
    double prevx = center.x + rx, prevz = center.z;
    double prevy = center.y;

    for (int i = 1; i <= seg; i++) {
        double ang = (i * 2 * Math.PI) / seg;
        double x = center.x + Math.cos(ang) * rx;
        double z = center.z + Math.sin(ang) * rz;
        submitLine(queue, matrices, r, g, b, a, light,
                   prevx, prevy, prevz, x, center.y, z);
        prevx = x;
        prevz = z;
    }
}
```

### Dashed Lines

```java
private static final float DASH_LENGTH = 0.5f / 8f;
private static final float GAP_LENGTH = 0.3f / 8f;

private static boolean isDashVisible(double t, float phase) {
    double cycle = DASH_LENGTH + GAP_LENGTH;
    double u = (t + phase) % 1.0;
    double p = (u % cycle);
    return p < DASH_LENGTH;
}

private static void drawBoxEdge(..., boolean dashed, float phase) {
    if (!dashed) {
        submitLine(queue, matrices, r, g, b, a, light, ax, ay, az, bx, by, bz);
        return;
    }

    int seg = 32;
    double px = ax, py = ay, pz = az;
    for (int i = 1; i <= seg; i++) {
        double t = i / (double) seg;
        double x = ax + (bx - ax) * t;
        double y = ay + (by - ay) * t;
        double z = az + (bz - az) * t;
        if (isDashVisible(t, phase)) {
            submitLine(queue, matrices, r, g, b, a, light, px, py, pz, x, y, z);
        }
        px = x; py = y; pz = z;
    }
}
```

**Animated Dashes:**
```java
long nowMs = System.currentTimeMillis();
float dashPhase = (nowMs % (1000L * 8)) / (1000f * 8); // 0..1 over 8 seconds
```

## Performance Optimizations

### View Distance Culling

```java
Vec3d camPos = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
int viewDist = Math.max(2, MinecraftClient.getInstance().options.getViewDistance().getValue());
int camCX = (int)Math.floor(camPos.x) >> 4;
int camCZ = (int)Math.floor(camPos.z) >> 4;

for (CameraNode node : manager.getNodes()) {
    int nCX = (int)Math.floor(node.position.x) >> 4;
    int nCZ = (int)Math.floor(node.position.z) >> 4;
    if (Math.abs(nCX - camCX) > viewDist || Math.abs(nCZ - camCZ) > viewDist)
        continue; // Skip rendering
    // ... render node
}
```

### Lighting

```java
int fullbright = 0x00F000F0; // Full brightness for UI elements
```

This constant is passed as the `light` parameter to `submitLine()`. Format: `0x00LLBBLL` where `LL` is light level (0xF0 = full brightness).

## Color Handling

```java
int color = 0xFFFFFFFF; // ARGB format
float a = ((color >> 24) & 0xFF) / 255f;
float r = ((color >> 16) & 0xFF) / 255f;
float g = ((color >> 8) & 0xFF) / 255f;
float b = (color & 0xFF) / 255f;
```

## Complete Usage Example

```java
public static void renderMyLines(MatrixStack matrices,
                                 WorldRenderState state,
                                 OrderedRenderCommandQueue queue) {
    // Get camera position
    Vec3d camPos = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();

    // Define world position
    Vec3d worldPos = new Vec3d(100, 64, 100);

    // Convert to camera-relative coordinates
    Vec3d relPos = worldPos.subtract(camPos);

    // Define start and end points (camera-relative)
    Vec3d start = relPos;
    Vec3d end = relPos.add(0, 10, 0); // 10 blocks up

    // Define color (red, 50% transparent)
    float r = 1.0f, g = 0.0f, b = 0.0f, a = 0.5f;
    int light = 0x00F000F0; // Full brightness

    // Submit the line
    submitLine(queue, matrices, r, g, b, a, light,
               start.x, start.y, start.z,
               end.x, end.y, end.z);
}
```

## Key Takeaways

1. **Hook into rendering**: Use Mixin to inject into `WorldRenderer.pushEntityRenders()`
2. **Use RenderLayer.getLines()**: Proper layer for line rendering
3. **Camera-relative coordinates**: Always subtract camera position from world coordinates
4. **OrderedRenderCommandQueue**: Modern Minecraft rendering API
5. **Camera-facing normals**: Use camera forward vector for normals
6. **Batching queue**: Use `getBatchingQueue(1000)` for efficiency
7. **Two vertices per line**: Each line needs exactly 2 vertices with position, color, and normal
8. **Full brightness for UI**: Use `0x00F000F0` for always-visible lines
9. **Chunk culling**: Skip rendering objects outside view distance for performance
10. **Segmentation for curves**: Use 28-64 segments for smooth circles and ellipses

## Additional Notes

- The `MatrixStack` parameter is passed through but not explicitly used for simple line rendering (identity matrix is implied)
- For complex transformations, you can push/pop matrices on the `MatrixStack` before calling `submitLine()`
- The `WorldRenderState` parameter provides access to rendering state but isn't used in basic line rendering
- Dashed lines require breaking edges into multiple segments and conditionally rendering based on parametric position
- Note that the light parameter in the vertex submission is NOT used in the current implementation (lines 108-109 in NodeRenderer.java don't call `.light()`)

## Relevant Files

- **Main renderer**: `src/client/java/ninja/trek/nodes/render/NodeRenderer.java`
- **Mixin hook**: `src/client/java/ninja/trek/mixin/client/WorldRendererMixin.java`
- **Mixin registration**: `src/client/resources/craneshot.client.mixins.json`

## Minecraft Version

This implementation is for Minecraft 1.21.x using Fabric with the modern rendering pipeline (`OrderedRenderCommandQueue` system).
