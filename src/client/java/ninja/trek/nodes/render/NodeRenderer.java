package ninja.trek.nodes.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import ninja.trek.nodes.NodeManager;
import ninja.trek.nodes.model.*;

public class NodeRenderer {
    public static void render(MatrixStack matrices, WorldRenderState state, OrderedRenderCommandQueue queue) {
        if (!NodeManager.get().isEditing()) {
            // Draw overlays both in and out of edit? Phase 1: only when editing.
            return;
        }
        var list = NodeManager.get().getNodes();
        if (list.isEmpty()) return;

        Vec3d cam = net.minecraft.client.MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
        var bq = queue.getBatchingQueue(1000);
        int fullbright = 0x00F000F0;

        for (var node : list) {
            int color = node.colorARGB != null ? node.colorARGB : 0xFFFFFFFF;
            float a = ((color >> 24) & 0xFF) / 255f;
            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;

            // Draw node marker as a small cross (outline)
            Vec3d p = node.position;
            float size = 0.2f;
            submitLine(queue, matrices, r, g, b, a, fullbright,
                    p.x - cam.x - size, p.y - cam.y, p.z - cam.z,
                    p.x - cam.x + size, p.y - cam.y, p.z - cam.z);
            submitLine(queue, matrices, r, g, b, a, fullbright,
                    p.x - cam.x, p.y - cam.y - size, p.z - cam.z,
                    p.x - cam.x, p.y - cam.y + size, p.z - cam.z);
            submitLine(queue, matrices, r, g, b, a, fullbright,
                    p.x - cam.x, p.y - cam.y, p.z - cam.z - size,
                    p.x - cam.x, p.y - cam.y, p.z - cam.z + size);

            // Lines to area centers
            for (var area : node.areas) {
                submitLine(queue, matrices, r, g, b, a, fullbright,
                        p.x - cam.x, p.y - cam.y, p.z - cam.z,
                        area.center.x - cam.x, area.center.y - cam.y, area.center.z - cam.z);
                // Draw area outline (simple)
                if (area.shape == AreaShape.CUBE) drawCubeOutline(queue, matrices, area.center.subtract(cam), area.maxRadius, r, g, b, a, fullbright);
                else drawSphereApprox(queue, matrices, area.center.subtract(cam), area.maxRadius, r, g, b, a, fullbright);
                // inner min radius as dashed? Phase 1: solid with darker color
                float rr = r * 0.6f, gg = g * 0.6f, bb = b * 0.6f;
                if (area.shape == AreaShape.CUBE) drawCubeOutline(queue, matrices, area.center.subtract(cam), area.minRadius, rr, gg, bb, a, fullbright);
                else drawSphereApprox(queue, matrices, area.center.subtract(cam), area.minRadius, rr, gg, bb, a, fullbright);
            }

            // LookAt marker
            if (node.lookAt != null) {
                Vec3d la = node.lookAt.subtract(cam);
                float s = 0.3f;
                drawCircleApprox(queue, matrices, la, s, r, g, b, a, fullbright);
                drawCircleApprox(queue, matrices, la, s*0.6f, r, g, b, a, fullbright);
            }
        }
    }

    private static void submitLine(OrderedRenderCommandQueue queue, MatrixStack matrices,
                                   float r, float g, float b, float a, int light,
                                   double ax, double ay, double az, double bx, double by, double bz) {
        RenderLayer layer = RenderLayer.getSecondaryBlockOutline();
        var bq = queue.getBatchingQueue(1000);
        bq.submitCustom(matrices, layer, (entry, vc) -> {
            vc.vertex(entry, (float)ax, (float)ay, (float)az).color(r,g,b,a).normal(entry,0,1,0).light(light);
            vc.vertex(entry, (float)bx, (float)by, (float)bz).color(r,g,b,a).normal(entry,0,1,0).light(light);
        });
    }

    private static void drawCubeOutline(OrderedRenderCommandQueue queue, MatrixStack matrices, Vec3d center,
                                        double r, float cr, float cg, float cb, float ca, int light) {
        double x0 = center.x - r, x1 = center.x + r;
        double y0 = center.y - r, y1 = center.y + r;
        double z0 = center.z - r, z1 = center.z + r;
        // 12 edges
        submitLine(queue, matrices, cr,cg,cb,ca, light, x0,y0,z0, x1,y0,z0);
        submitLine(queue, matrices, cr,cg,cb,ca, light, x0,y0,z0, x0,y1,z0);
        submitLine(queue, matrices, cr,cg,cb,ca, light, x0,y0,z0, x0,y0,z1);

        submitLine(queue, matrices, cr,cg,cb,ca, light, x1,y1,z1, x0,y1,z1);
        submitLine(queue, matrices, cr,cg,cb,ca, light, x1,y1,z1, x1,y0,z1);
        submitLine(queue, matrices, cr,cg,cb,ca, light, x1,y1,z1, x1,y1,z0);

        submitLine(queue, matrices, cr,cg,cb,ca, light, x1,y0,z0, x1,y1,z0);
        submitLine(queue, matrices, cr,cg,cb,ca, light, x1,y0,z0, x1,y0,z1);
        submitLine(queue, matrices, cr,cg,cb,ca, light, x0,y1,z0, x1,y1,z0);
        submitLine(queue, matrices, cr,cg,cb,ca, light, x0,y1,z0, x0,y1,z1);
        submitLine(queue, matrices, cr,cg,cb,ca, light, x0,y0,z1, x1,y0,z1);
        submitLine(queue, matrices, cr,cg,cb,ca, light, x0,y0,z1, x0,y1,z1);
    }

    private static void drawSphereApprox(OrderedRenderCommandQueue queue, MatrixStack matrices, Vec3d center,
                                         double r, float cr, float cg, float cb, float ca, int light) {
        // Draw 3 great circles: XY, XZ, YZ
        drawCircleApprox(queue, matrices, center, r, cr, cg, cb, ca, light);
        // tilt circles: for simplicity reuse same circle at different planes
    }

    private static void drawCircleApprox(OrderedRenderCommandQueue queue, MatrixStack matrices, Vec3d c,
                                         double r, float cr, float cg, float cb, float ca, int light) {
        int seg = 24;
        double prevx = c.x + r, prevy = c.y, prevz = c.z;
        for (int i=1;i<=seg;i++) {
            double ang = (i * 2 * Math.PI)/seg;
            double x = c.x + Math.cos(ang) * r;
            double z = c.z + Math.sin(ang) * r;
            submitLine(queue, matrices, cr,cg,cb,ca, light, prevx, prevy, prevz, x, c.y, z);
            prevx = x; prevy = c.y; prevz = z;
        }
    }
}
