package ninja.trek.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import ninja.trek.mixin.client.GameRendererFovAccessor;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ninja.trek.mixin.client.CameraAccessor;

public class CrosshairRenderer {
    // Temporary debug: set true to visualize axes at hit point
    private static final boolean DEBUG_AXES = false;
    public static void render(MatrixStack matrices, WorldRenderState state, OrderedRenderCommandQueue queue) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) return;
        PlayerEntity player = client.player;
        if (player == null) return;

        Vec3d eye = player.getEyePos();
        // Use head orientation to build a ray; avoid camera rotation
        float yaw = player.getYaw();
        float pitch = player.getPitch();
        Vec3d dir = Vec3d.fromPolar(pitch, yaw);

        double maxDistance = 128.0;
        Vec3d end = eye.add(dir.multiply(maxDistance));

        BlockHitResult hit = client.world.raycast(new RaycastContext(
                eye,
                end,
                RaycastContext.ShapeType.VISUAL,
                RaycastContext.FluidHandling.NONE,
                player
        ));

        if (hit == null || hit.getType() == HitResult.Type.MISS) return;

        Vec3d hitPos = hit.getPos();
        var camera = client.gameRenderer.getCamera();
        Vec3d cam = camera.getPos();

        // Build camera basis from camera rotation (screen axes in world space)
        Quaternionf rot = camera.getRotation();
        Vector3f rV = new Vector3f(1f, 0f, 0f).rotate(rot);
        Vector3f uV = new Vector3f(0f, 1f, 0f).rotate(rot);
        Vector3f fV = new Vector3f(0f, 0f, -1f).rotate(rot);
        Vec3d right = new Vec3d(rV.x, rV.y, rV.z).normalize();
        Vec3d up = new Vec3d(uV.x, uV.y, uV.z).normalize();
        Vec3d forward = new Vec3d(fV.x, fV.y, fV.z).normalize();

        // Keep apparent size roughly constant in screen space
        int screenH = client.getWindow().getScaledHeight();
        double distance = hitPos.distanceTo(cam);
        // Effective vertical FOV (degrees)
        int baseFov = client.options.getFov().getValue();
        float fovMul = ((GameRendererFovAccessor) client.gameRenderer).getFovMultiplier();
        double fovDeg = Math.max(1.0, baseFov * fovMul);
        double fovRad = Math.toRadians(fovDeg);
        // world_size_for_pixels = pixels * (2 * d * tan(fov/2)) / screenH
        double pixelLen = 0.5; // half-length in pixels per arm (1px across)
        double size = pixelLen * (2.0 * distance * Math.tan(fovRad * 0.5)) / Math.max(1.0, (double)screenH);
        // Avoid degenerate zero-length
        size = Math.max(0.001, size);

        // Nudge toward camera slightly to avoid z-fighting with the hit surface
        Vec3d center = hitPos.subtract(forward.multiply(0.01));

        // Draw a small camera-facing cross at the hit point
        float r = 1.0f, g = 1.0f, b = 1.0f, a = 1.0f;
        int fullbright = 0x00F000F0;

        Vec3d a1 = center.add(right.multiply(-size));
        Vec3d a2 = center.add(right.multiply(size));
        Vec3d b1 = center.add(up.multiply(-size));
        Vec3d b2 = center.add(up.multiply(size));

        // For consistent screen-facing thickness, use camera back vector as normal for both arms
        submitLine(queue, matrices, r,g,b,a, fullbright,
                a1.x - cam.x, a1.y - cam.y, a1.z - cam.z,
                a2.x - cam.x, a2.y - cam.y, a2.z - cam.z,
                (float)(-forward.x), (float)(-forward.y), (float)(-forward.z));
        submitLine(queue, matrices, r,g,b,a, fullbright,
                b1.x - cam.x, b1.y - cam.y, b1.z - cam.z,
                b2.x - cam.x, b2.y - cam.y, b2.z - cam.z,
                (float)(-forward.x), (float)(-forward.y), (float)(-forward.z));

        if (DEBUG_AXES) {
            // Draw colored axes: right=red, up=green, back toward camera=blue
            double debugPx = 8.0; // 8px length for visibility
            double debugLen = debugPx * (2.0 * distance * Math.tan(fovRad * 0.5)) / Math.max(1.0, (double)screenH);
            Vec3d c = center;
            // Right (red)
            Vec3d r1 = c.add(right.multiply(-debugLen));
            Vec3d r2 = c.add(right.multiply(debugLen));
            submitLine(queue, matrices, 1,0,0,1, fullbright,
                    r1.x - cam.x, r1.y - cam.y, r1.z - cam.z,
                    r2.x - cam.x, r2.y - cam.y, r2.z - cam.z,
                    (float)(-forward.x), (float)(-forward.y), (float)(-forward.z));
            // Up (green)
            Vec3d u1 = c.add(up.multiply(-debugLen));
            Vec3d u2 = c.add(up.multiply(debugLen));
            submitLine(queue, matrices, 0,1,0,1, fullbright,
                    u1.x - cam.x, u1.y - cam.y, u1.z - cam.z,
                    u2.x - cam.x, u2.y - cam.y, u2.z - cam.z,
                    (float)(-forward.x), (float)(-forward.y), (float)(-forward.z));
            // Back toward camera (blue)
            Vec3d back = forward.multiply(-1);
            Vec3d f1 = c.add(back.multiply(-debugLen));
            Vec3d f2 = c.add(back.multiply(debugLen));
            submitLine(queue, matrices, 0,0,1,1, fullbright,
                    f1.x - cam.x, f1.y - cam.y, f1.z - cam.z,
                    f2.x - cam.x, f2.y - cam.y, f2.z - cam.z,
                    (float)(-forward.x), (float)(-forward.y), (float)(-forward.z));
        }
    }

    private static void submitLine(OrderedRenderCommandQueue queue, MatrixStack matrices,
                                   float r, float g, float b, float a, int light,
                                   double ax, double ay, double az, double bx, double by, double bz,
                                   float nx, float ny, float nz) {
        RenderLayer layer = RenderLayer.getSecondaryBlockOutline();
        var bq = queue.getBatchingQueue(1000);
        bq.submitCustom(matrices, layer, (entry, vc) -> {
            vc.vertex(entry, (float)ax, (float)ay, (float)az).color(r,g,b,a).normal(entry,nx,ny,nz).light(light);
            vc.vertex(entry, (float)bx, (float)by, (float)bz).color(r,g,b,a).normal(entry,nx,ny,nz).light(light);
        });
    }
}
