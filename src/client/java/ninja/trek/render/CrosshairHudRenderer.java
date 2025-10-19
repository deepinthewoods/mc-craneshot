package ninja.trek.render;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import ninja.trek.mixin.client.GameRendererFovAccessor;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class CrosshairHudRenderer {
    // Simple smoothing to reduce subpixel jitter after projection
    private static double smoothedSx = Double.NaN;
    private static double smoothedSy = Double.NaN;
    private static final double SMOOTH_ALPHA = 1; // 0..1, higher = snappier
    public static void register() {
        HudRenderCallback.EVENT.register(CrosshairHudRenderer::onHudRender);
    }

    private static void onHudRender(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) return;
        PlayerEntity player = client.player;
        if (player == null) return;

        // Respect settings
        if (!ninja.trek.config.GeneralMenuSettings.isShowCameraCrosshair()) return;

        // Raycast from the PLAYER HEAD orientation (decoupled from camera)
        float tickProgress = tickCounter.getTickProgress(false);
        Vec3d lerpedPos = player.getLerpedPos(tickProgress);
        Vec3d eye = new Vec3d(lerpedPos.x, lerpedPos.y + player.getStandingEyeHeight(), lerpedPos.z);
        float yaw = player.getLerpedYaw(tickProgress);
        float pitch = player.getLerpedPitch(tickProgress);
        Vec3d headDir = Vec3d.fromPolar(pitch, yaw);
        double maxDistance = 128.0;
        Vec3d end = eye.add(headDir.multiply(maxDistance));

        BlockHitResult hit = client.world.raycast(new RaycastContext(
                eye,
                end,
                RaycastContext.ShapeType.VISUAL,
                RaycastContext.FluidHandling.NONE,
                player
        ));
        if (hit == null || hit.getType() == HitResult.Type.MISS) return;

        // Project 3D point to screen space using camera basis/FOV
        var camera = client.gameRenderer.getCamera();
        Vec3d camPos = camera.getPos();
        Vec3d world = hit.getPos();
        Vec3d v = world.subtract(camPos);

        Quaternionf rot = camera.getRotation();
        // Camera axes in world space
        Vector3f rV = new Vector3f(1f, 0f, 0f).rotate(rot);
        Vector3f uV = new Vector3f(0f, 1f, 0f).rotate(rot);
        Vector3f fV = new Vector3f(0f, 0f, -1f).rotate(rot);

        Vec3d right = new Vec3d(rV.x, rV.y, rV.z);
        Vec3d up = new Vec3d(uV.x, uV.y, uV.z);
        Vec3d forward = new Vec3d(fV.x, fV.y, fV.z);

        double xCam = v.dotProduct(right);
        double yCam = v.dotProduct(up);
        double zCam = v.dotProduct(forward);
        if (zCam <= 0.0) return; // behind camera or at eye

        // Effective FOV
        int baseFov = client.options.getFov().getValue();
        float fovMul = ((GameRendererFovAccessor) client.gameRenderer).getFovMultiplier();
        double fovYDeg = Math.max(1.0, baseFov * fovMul);
        double fovY = Math.toRadians(fovYDeg);

        // Aspect and per-axis tangents
        double w = client.getWindow().getScaledWidth();
        double h = client.getWindow().getScaledHeight();
        if (w <= 0 || h <= 0) return;
        double aspect = w / h;
        double tanHalfY = Math.tan(fovY * 0.5);
        double tanHalfX = tanHalfY * aspect;

        double nx = xCam / (zCam * tanHalfX);
        double ny = yCam / (zCam * tanHalfY);

        // Clip if far outside view (optional margins)
        if (nx < -2.0 || nx > 2.0 || ny < -2.0 || ny > 2.0) return;

        double sxF = (nx + 1.0) * 0.5 * w;
        double syF = (1.0 - (ny + 1.0) * 0.5) * h;

        if (Double.isNaN(smoothedSx)) {
            smoothedSx = sxF;
            smoothedSy = syF;
        } else {
            smoothedSx += (sxF - smoothedSx) * SMOOTH_ALPHA;
            smoothedSy += (syF - smoothedSy) * SMOOTH_ALPHA;
        }

        int sx = (int) Math.round(smoothedSx);
        int sy = (int) Math.round(smoothedSy);

        // Draw crosshair per settings
        int color = 0xFFFFFFFF; // white, full alpha
        int size = Math.max(1, ninja.trek.config.GeneralMenuSettings.getCameraCrosshairSize());
        boolean square = ninja.trek.config.GeneralMenuSettings.isCameraCrosshairSquare();
        if (square) {
            // Interpret size as full side length (diameter), not radius
            int side = Math.max(1, size);
            int halfFloor = side / 2; // integer division
            int left = sx - halfFloor;
            int top = sy - halfFloor;
            int x2 = left + side;   // exclusive bound
            int y2 = top + side;    // exclusive bound
            ctx.fill(left, top, x2, y2, color);
        } else {
            // cross: 1px thick arms of length +/- size
            ctx.fill(sx - size, sy, sx + size + 1, sy + 1, color);
            ctx.fill(sx, sy - size, sx + 1, sy + size + 1, color);
        }
    }
}
