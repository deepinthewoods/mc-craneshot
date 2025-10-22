package ninja.trek.render;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import ninja.trek.nodes.NodeManager;
import ninja.trek.nodes.model.Area;
import ninja.trek.nodes.model.AreaShape;
import ninja.trek.nodes.model.CameraNode;

import java.util.ArrayList;
import java.util.List;

public class NodeAreaHudRenderer {
    private static final int MARGIN_LEFT = 10;
    private static final int MARGIN_BOTTOM = 10;
    private static final int LINE_HEIGHT = 12;

    public static void register() {
        HudRenderCallback.EVENT.register(NodeAreaHudRenderer::onHudRender);
    }

    private static void onHudRender(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.player == null) return;

        NodeManager nodeManager = NodeManager.get();
        if (nodeManager.getNodes().isEmpty()) return;

        Vec3d playerPos = client.player.getEyePos();
        TextRenderer textRenderer = client.textRenderer;
        int screenHeight = client.getWindow().getScaledHeight();

        // Collect active areas with their influences
        List<AreaInfluenceInfo> activeAreas = new ArrayList<>();

        for (CameraNode node : nodeManager.getNodes()) {
            for (int i = 0; i < node.areas.size(); i++) {
                Area area = node.areas.get(i);

                // Check if area is eligible for current player state
                if (!isAreaEligibleForPlayer(area, client)) continue;

                // Calculate influence
                double influence = calculateInfluence(playerPos, area);

                if (influence > 0.001) { // Only show areas with >0% influence
                    // Apply easing curve if present
                    double displayInfluence = area.easing != null ? area.easing.apply(influence) : influence;
                    activeAreas.add(new AreaInfluenceInfo(node, i, displayInfluence));
                }
            }
        }

        // Render the list from bottom to top
        int y = screenHeight - MARGIN_BOTTOM;

        for (int i = activeAreas.size() - 1; i >= 0; i--) {
            AreaInfluenceInfo info = activeAreas.get(i);
            y -= LINE_HEIGHT;

            // Format: "NodeName [Area 1]: 45.2%"
            String nodeName = info.node.name != null && !info.node.name.isEmpty()
                ? info.node.name
                : "Node";
            int areaIndex = info.areaIndex + 1; // 1-based for display
            String text = String.format("%s [Area %d]: %.1f%%", nodeName, areaIndex, info.influence * 100.0);

            // Use node color if available, otherwise white
            int color = info.node.colorARGB != null ? info.node.colorARGB : 0xFFFFFFFF;

            // Ensure alpha is set (in case stored color has 0 alpha)
            if ((color & 0xFF000000) == 0) {
                color |= 0xFF000000;
            }

            ctx.drawTextWithShadow(
                textRenderer,
                Text.literal(text),
                MARGIN_LEFT,
                y,
                color
            );
        }
    }

    // Helper class to store area influence information
    private static class AreaInfluenceInfo {
        final CameraNode node;
        final int areaIndex;
        final double influence;

        AreaInfluenceInfo(CameraNode node, int areaIndex, double influence) {
            this.node = node;
            this.areaIndex = areaIndex;
            this.influence = influence;
        }
    }

    // Copied from NodeManager - checks if area matches current player movement state
    private static boolean isAreaEligibleForPlayer(Area area, MinecraftClient mc) {
        if (mc.player == null) return true;
        var pl = mc.player;

        boolean isElytra = false;
        try {
            Object pose = pl.getPose();
            if (pose != null) {
                String n = pose.toString();
                isElytra = "FALL_FLYING".equals(n) || "GLIDING".equals(n);
            }
        } catch (Throwable ignored) {}

        boolean isSwimming = pl.isSwimming();
        boolean isSneaking = pl.isSneaking();
        var vehicle = pl.getVehicle();
        boolean isBoat = vehicle instanceof net.minecraft.entity.vehicle.BoatEntity;
        boolean isMinecart = vehicle instanceof net.minecraft.entity.vehicle.AbstractMinecartEntity;
        boolean isRidingGhast = vehicle instanceof net.minecraft.entity.mob.GhastEntity;
        boolean isRidingOther = vehicle != null && !isBoat && !isMinecart && !isRidingGhast;
        boolean isCrawling1Block = pl.isInSwimmingPose() && !pl.isTouchingWater() && !isSwimming;
        boolean isWalking = !isElytra && !isSwimming && vehicle == null;

        boolean ok = false;
        if (area.filterWalking && isWalking && !isSneaking) ok = true;
        if (area.filterSneaking && isWalking && isSneaking) ok = true;
        if (area.filterElytra && isElytra) ok = true;
        if (area.filterBoat && isBoat) ok = true;
        if (area.filterMinecart && isMinecart) ok = true;
        if (area.filterRidingGhast && isRidingGhast) ok = true;
        if (area.filterRidingOther && isRidingOther) ok = true;
        if (area.filterSwimming && isSwimming) ok = true;
        if (area.filterCrawling1Block && isCrawling1Block) ok = true;
        return ok;
    }

    // Copied from NodeManager - calculates influence for an area
    private static double calculateInfluence(Vec3d pos, Area area) {
        if (area.shape == AreaShape.SPHERE) {
            return influenceForSphereArea(pos, area);
        } else {
            return influenceForBoxArea(pos, area);
        }
    }

    private static double influenceForSphereArea(Vec3d pos, Area area) {
        Vec3d d = pos.subtract(area.center);
        if (area.advanced && area.minRadii != null && area.maxRadii != null) {
            // Ellipsoids defined by minRadii and maxRadii
            double lOuter = Math.sqrt(
                    (d.x * d.x) / (area.maxRadii.x * area.maxRadii.x)
                            + (d.y * d.y) / (area.maxRadii.y * area.maxRadii.y)
                            + (d.z * d.z) / (area.maxRadii.z * area.maxRadii.z));
            double lInner = Math.sqrt(
                    (d.x * d.x) / (area.minRadii.x * area.minRadii.x)
                            + (d.y * d.y) / (area.minRadii.y * area.minRadii.y)
                            + (d.z * d.z) / (area.minRadii.z * area.minRadii.z));

            if (lInner <= 1.0) return 1.0;
            if (lOuter >= 1.0) return 0.0;
            double denom = (lInner - 1.0);
            if (denom <= 1e-6) return 1.0;
            double t = 1.0 - ((lOuter - 1.0) / denom);
            return MathHelper.clamp(t, 0.0, 1.0);
        } else {
            double dist = pos.distanceTo(area.center);
            if (dist <= area.minRadius) return 1.0;
            if (dist >= area.maxRadius) return 0.0;
            return 1.0 - ((dist - area.minRadius) / (area.maxRadius - area.minRadius));
        }
    }

    private static double influenceForBoxArea(Vec3d pos, Area area) {
        Vec3d d = pos.subtract(area.center);
        if (area.advanced && area.minRadii != null && area.maxRadii != null) {
            double ax = Math.abs(d.x), ay = Math.abs(d.y), az = Math.abs(d.z);
            if (ax <= area.minRadii.x && ay <= area.minRadii.y && az <= area.minRadii.z) return 1.0;
            if (ax >= area.maxRadii.x || ay >= area.maxRadii.y || az >= area.maxRadii.z) return 0.0;

            double rx = (area.maxRadii.x - area.minRadii.x);
            double ry = (area.maxRadii.y - area.minRadii.y);
            double rz = (area.maxRadii.z - area.minRadii.z);
            double nx = rx > 1e-6 ? Math.max(0.0, (ax - area.minRadii.x) / rx) : 1.0;
            double ny = ry > 1e-6 ? Math.max(0.0, (ay - area.minRadii.y) / ry) : 1.0;
            double nz = rz > 1e-6 ? Math.max(0.0, (az - area.minRadii.z) / rz) : 1.0;
            double t = 1.0 - Math.max(nx, Math.max(ny, nz));
            return MathHelper.clamp(t, 0.0, 1.0);
        } else {
            double dist = cubeDistance(pos, area.center, area.maxRadius);
            if (dist <= 0.0) return 1.0;
            double inner = cubeDistance(pos, area.center, area.minRadius);
            if (inner <= 0.0) return 1.0;
            double denom = (inner);
            double t = 1.0 - Math.min(1.0, dist / Math.max(1e-6, denom));
            return MathHelper.clamp(t, 0.0, 1.0);
        }
    }

    private static double cubeDistance(Vec3d p, Vec3d c, double r) {
        double dx = Math.max(Math.abs(p.x - c.x) - r, 0);
        double dy = Math.max(Math.abs(p.y - c.y) - r, 0);
        double dz = Math.max(Math.abs(p.z - c.z) - r, 0);
        return Math.sqrt(dx*dx + dy*dy + dz*dz);
    }
}
