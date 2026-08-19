package ninja.trek.render;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import ninja.trek.config.GeneralMenuSettings;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class CrosshairHudRenderer {
    private static final X11CrosshairOverlay X11_OVERLAY = new X11CrosshairOverlay();

    // Simple smoothing to reduce subpixel jitter after projection
    private static double smoothedSx = Double.NaN;
    private static double smoothedSy = Double.NaN;
    private static final double SMOOTH_ALPHA = 1; // 0..1, higher = snappier

    public static void register() {
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.CROSSHAIR,
                Identifier.fromNamespaceAndPath("craneshot", "camera_crosshair"),
                CrosshairHudRenderer::onHudRender);
        ClientTickEvents.END_CLIENT_TICK.register(CrosshairHudRenderer::onClientTick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> X11_OVERLAY.close());
    }

    private static void onHudRender(GuiGraphicsExtractor ctx, DeltaTracker tickCounter) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;

        boolean overlayRequested = isOverlayRequested();
        if (!overlayRequested) {
            X11_OVERLAY.hide();
        }

        if (client.level == null || !GeneralMenuSettings.isShowCameraCrosshair()) {
            X11_OVERLAY.hide();
            return;
        }
        Player player = client.player;
        if (player == null) {
            X11_OVERLAY.hide();
            return;
        }

        if (overlayRequested && !X11_OVERLAY.hasFailed() && !isOverlayContextEligible(client)) {
            X11_OVERLAY.hide();
            return;
        }

        ProjectedCrosshair projected = projectCrosshair(client, player, tickCounter);
        if (projected == null) {
            X11_OVERLAY.hide();
            return;
        }

        if (overlayRequested && !X11_OVERLAY.hasFailed()) {
            X11CrosshairOverlay.ShowResult result = X11_OVERLAY.show(
                    client,
                    projected.normalizedX(),
                    projected.normalizedY(),
                    GeneralMenuSettings.getCameraCrosshairSize());
            if (result != X11CrosshairOverlay.ShowResult.FAILED) {
                return;
            }
        }

        drawHudCrosshair(ctx, client, projected);
    }

    private static ProjectedCrosshair projectCrosshair(
            Minecraft client,
            Player player,
            DeltaTracker tickCounter) {

        // Raycast from the PLAYER HEAD orientation (decoupled from camera)
        float tickProgress = tickCounter.getGameTimeDeltaPartialTick(true);
        Vec3 eye = player.getEyePosition(tickProgress);
        float yaw = player.getViewYRot(tickProgress);
        float pitch = player.getViewXRot(tickProgress);
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
        if (hit == null || hit.getType() == HitResult.Type.MISS) return null;

        // Project 3D point to screen space using camera basis/FOV
        var camera = client.gameRenderer.mainCamera();
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
        if (zCam <= 0.0) return null; // behind camera or at eye

        // Camera#getFov is already interpolated for this render frame and includes
        // both Minecraft's dynamic FOV and Craneshot's composed multiplier.
        double fovY = Math.toRadians(Math.max(1.0, camera.getFov()));

        // Aspect and per-axis tangents
        double w = client.getWindow().getWidth();
        double h = client.getWindow().getHeight();
        if (w <= 0 || h <= 0) return null;
        double aspect = w / h;
        double tanHalfY = Math.tan(fovY * 0.5);
        double tanHalfX = tanHalfY * aspect;

        double nx = xCam / (zCam * tanHalfX);
        double ny = yCam / (zCam * tanHalfY);

        // Clip if far outside view (optional margins)
        if (nx < -2.0 || nx > 2.0 || ny < -2.0 || ny > 2.0) return null;

        return new ProjectedCrosshair(nx, ny);
    }

    private static void drawHudCrosshair(
            GuiGraphicsExtractor ctx,
            Minecraft client,
            ProjectedCrosshair projected) {
        double w = client.getWindow().getGuiScaledWidth();
        double h = client.getWindow().getGuiScaledHeight();
        if (w <= 0 || h <= 0) return;

        double sxF = (projected.normalizedX() + 1.0) * 0.5 * w;
        double syF = (1.0 - (projected.normalizedY() + 1.0) * 0.5) * h;

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
        int size = Math.max(1, GeneralMenuSettings.getCameraCrosshairSize());
        boolean square = GeneralMenuSettings.isCameraCrosshairSquare();
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

    private static void onClientTick(Minecraft client) {
        if (!X11_OVERLAY.isMapped()) {
            return;
        }

        if (!isOverlayRequested() || !isOverlayContextEligible(client)) {
            X11_OVERLAY.hide();
        }
    }

    private static boolean isOverlayRequested() {
        return GeneralMenuSettings.isUseX11CameraDot() && X11CrosshairOverlay.isSupported();
    }

    private static boolean isOverlayContextEligible(Minecraft client) {
        return GeneralMenuSettings.isShowCameraCrosshair()
                && client.level != null
                && client.player != null
                && client.gui.screen() == null
                && client.isWindowActive()
                && !client.gui.hud.isHidden()
                && client.options.getCameraType().isFirstPerson();
    }

    private record ProjectedCrosshair(double normalizedX, double normalizedY) {}
}
