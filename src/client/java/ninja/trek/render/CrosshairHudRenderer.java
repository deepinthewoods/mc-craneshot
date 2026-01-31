package ninja.trek.render;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
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

    private static void onHudRender(GuiGraphics ctx, DeltaTracker tickCounter) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) return;
        Player player = client.player;
        if (player == null) return;

        // Respect settings
        if (!ninja.trek.config.GeneralMenuSettings.isShowCameraCrosshair()) return;

        // Raycast from the PLAYER HEAD orientation (decoupled from camera)
        float tickProgress = tickCounter.getGameTimeDeltaPartialTick(false);
        Vec3 lerpedPos = player.getPosition(tickProgress);
        Vec3 eye = new Vec3(lerpedPos.x, lerpedPos.y + player.getEyeHeight(), lerpedPos.z);
        float yaw = player.getYRot(tickProgress);
        float pitch = player.getXRot(tickProgress);
        Vec3 headDir = Vec3.directionFromRotation(pitch, yaw);
        double maxDistance = 128.0;
        Vec3 end = eye.add(headDir.scale(maxDistance));

        BlockHitResult hit = client.level.clip(new ClipContext(
                eye,
                end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player
        ));
        if (hit == null || hit.getType() == HitResult.Type.MISS) return;

        // Project 3D point to screen space using camera basis/FOV
        var camera = client.gameRenderer.getMainCamera();
        Vec3 camPos = camera.position();
        Vec3 world = hit.getLocation();
        Vec3 v = world.subtract(camPos);

        Quaternionf rot = camera.rotation();
        // Camera axes in world space
        Vector3f rV = new Vector3f(1f, 0f, 0f).rotate(rot);
        Vector3f uV = new Vector3f(0f, 1f, 0f).rotate(rot);
        Vector3f fV = new Vector3f(0f, 0f, -1f).rotate(rot);

        Vec3 right = new Vec3(rV.x, rV.y, rV.z);
        Vec3 up = new Vec3(uV.x, uV.y, uV.z);
        Vec3 forward = new Vec3(fV.x, fV.y, fV.z);

        double xCam = v.dot(right);
        double yCam = v.dot(up);
        double zCam = v.dot(forward);
        if (zCam <= 0.0) return; // behind camera or at eye

        // Effective FOV
        int baseFov = client.options.fov().get();
        float fovMul = ((GameRendererFovAccessor) client.gameRenderer).getFovMultiplier();
        double fovYDeg = Math.max(1.0, baseFov * fovMul);
        double fovY = Math.toRadians(fovYDeg);

        // Aspect and per-axis tangents
        double w = client.getWindow().getGuiScaledWidth();
        double h = client.getWindow().getGuiScaledHeight();
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
