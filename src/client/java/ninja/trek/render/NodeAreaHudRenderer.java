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
import ninja.trek.nodes.NodeManager.PlayerStateSnapshot;
import ninja.trek.nodes.model.Area;
import ninja.trek.nodes.model.AreaInstance;
import ninja.trek.nodes.model.AreaShape;

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
        if (nodeManager.getAreas().isEmpty()) return;

        Vec3d playerPos = client.player.getEyePos();
        TextRenderer textRenderer = client.textRenderer;
        int screenHeight = client.getWindow().getScaledHeight();

        // Collect active areas with their influences
        List<AreaInfluenceInfo> activeAreas = new ArrayList<>();
        AreaInstance selectedArea = nodeManager.getSelectedArea();
        PlayerStateSnapshot snapshot = nodeManager.collectPlayerStates(client);

        for (AreaInstance area : nodeManager.getAreas()) {
            if (!NodeManager.areaMatchesPlayerStates(area, snapshot)) continue;

            double influence = calculateInfluence(playerPos, area);
            if (influence > 0.001) {
                double displayInfluence = area.easing != null ? area.easing.apply(influence) : influence;
                activeAreas.add(new AreaInfluenceInfo(area, displayInfluence, selectedArea != null && selectedArea.id.equals(area.id)));
            }
        }

        // Render the list from bottom to top
        int y = screenHeight - MARGIN_BOTTOM;

        for (int i = activeAreas.size() - 1; i >= 0; i--) {
            AreaInfluenceInfo info = activeAreas.get(i);
            y -= LINE_HEIGHT;

            String areaName = info.area.name != null && !info.area.name.isBlank() ? info.area.name : "Area";
            String text = String.format("%s: %.1f%%", areaName, info.influence * 100.0);

            int color = info.selected ? 0xFF66FFAA : 0xFF4CB3FF;

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
        final AreaInstance area;
        final double influence;
        final boolean selected;

        AreaInfluenceInfo(AreaInstance area, double influence, boolean selected) {
            this.area = area;
            this.influence = influence;
            this.selected = selected;
        }
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
        if (area.advanced && area.insideRadii != null && area.outsideRadii != null) {
            // Ellipsoids defined by insideRadii and outsideRadii
            double distAtInside = Math.sqrt(
                    (d.x * d.x) / (area.insideRadii.x * area.insideRadii.x)
                            + (d.y * d.y) / (area.insideRadii.y * area.insideRadii.y)
                            + (d.z * d.z) / (area.insideRadii.z * area.insideRadii.z));
            double distAtOutside = Math.sqrt(
                    (d.x * d.x) / (area.outsideRadii.x * area.outsideRadii.x)
                            + (d.y * d.y) / (area.outsideRadii.y * area.outsideRadii.y)
                            + (d.z * d.z) / (area.outsideRadii.z * area.outsideRadii.z));

            if (distAtInside <= 1.0) return 1.0;
            if (distAtOutside >= 1.0) return 0.0;
            double t = (distAtOutside - 1.0) / (distAtOutside - distAtInside);
            return MathHelper.clamp(t, 0.0, 1.0);
        } else {
            double dist = pos.distanceTo(area.center);
            if (dist <= area.insideRadius) return 1.0;
            if (dist >= area.outsideRadius) return 0.0;
            return 1.0 - ((dist - area.insideRadius) / (area.outsideRadius - area.insideRadius));
        }
    }

    private static double influenceForBoxArea(Vec3d pos, Area area) {
        Vec3d d = pos.subtract(area.center);
        if (area.advanced && area.insideRadii != null && area.outsideRadii != null) {
            double ax = Math.abs(d.x), ay = Math.abs(d.y), az = Math.abs(d.z);
            if (ax <= area.insideRadii.x && ay <= area.insideRadii.y && az <= area.insideRadii.z) return 1.0;
            if (ax >= area.outsideRadii.x || ay >= area.outsideRadii.y || az >= area.outsideRadii.z) return 0.0;

            double rx = (area.outsideRadii.x - area.insideRadii.x);
            double ry = (area.outsideRadii.y - area.insideRadii.y);
            double rz = (area.outsideRadii.z - area.insideRadii.z);
            double nx = rx > 1e-6 ? Math.max(0.0, (ax - area.insideRadii.x) / rx) : 1.0;
            double ny = ry > 1e-6 ? Math.max(0.0, (ay - area.insideRadii.y) / ry) : 1.0;
            double nz = rz > 1e-6 ? Math.max(0.0, (az - area.insideRadii.z) / rz) : 1.0;
            double t = 1.0 - Math.max(nx, Math.max(ny, nz));
            return MathHelper.clamp(t, 0.0, 1.0);
        } else {
            double dist = cubeDistance(pos, area.center, area.outsideRadius);
            if (dist <= 0.0) return 1.0;
            double inner = cubeDistance(pos, area.center, area.insideRadius);
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
