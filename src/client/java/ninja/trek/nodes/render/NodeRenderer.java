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
        boolean showOutsideEdit = ninja.trek.config.GeneralMenuSettings.isShowNodesOutsideEdit();
        if (!NodeManager.get().isEditing() && !showOutsideEdit) return;
        var list = NodeManager.get().getNodes();
        if (list.isEmpty()) return;

        Vec3d cam = net.minecraft.client.MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
        // Cull outside render distance (chunk-based)
        int viewDist = Math.max(2, MinecraftClient.getInstance().options.getViewDistance().getValue());
        int camCX = (int)Math.floor(cam.x) >> 4;
        int camCZ = (int)Math.floor(cam.z) >> 4;
        var bq = queue.getBatchingQueue(1000);
        int fullbright = 0x00F000F0;

        long nowMs = System.currentTimeMillis();
        // Slow animated dash motion by 8x
        float dashPhase = (nowMs % (1000L * 8)) / (1000f * 8); // 0..1 over 8 seconds

        for (var node : list) {
            int nCX = (int)Math.floor(node.position.x) >> 4;
            int nCZ = (int)Math.floor(node.position.z) >> 4;
            if (Math.abs(nCX - camCX) > viewDist || Math.abs(nCZ - camCZ) > viewDist) continue;
            int color = node.colorARGB != null ? node.colorARGB : 0xFFFFFFFF;
            float a = ((color >> 24) & 0xFF) / 255f;
            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;

            // Draw node marker as a small filled billboarded quad with white outline if selected
            Vec3d p = node.position;
            float size = 0.2f;
            drawBillboardQuad(queue, matrices, p.subtract(cam), size, r, g, b, a, fullbright);
            CameraNode sel = NodeManager.get().getSelected();
            if (sel != null && sel.id.equals(node.id)) {
                drawBillboardOutline(queue, matrices, p.subtract(cam), size * 1.1f, 1f,1f,1f, a, fullbright);
            }

            // Lines to area centers
            for (var area : node.areas) {
                // Make dash segments 8x smaller for animated link
                submitDashedLine(queue, matrices, r, g, b, a, fullbright,
                        p.x - cam.x, p.y - cam.y, p.z - cam.z,
                        area.center.x - cam.x, area.center.y - cam.y, area.center.z - cam.z,
                        0.5f/8f, 0.3f/8f, dashPhase);
                // Draw area outline (simple)
                float rr = r * 0.6f, gg = g * 0.6f, bb = b * 0.6f;
                if (area.shape == AreaShape.CUBE) {
                    // Outer (max): keep existing style (advanced dashed animated, simple solid)
                    if (area.advanced && area.maxRadii != null) {
                        drawBoxOutline(queue, matrices, area.center.subtract(cam), area.maxRadii, r,g,b,a, fullbright, true, dashPhase);
                    } else {
                        drawCubeOutline(queue, matrices, area.center.subtract(cam), area.maxRadius, r, g, b, a, fullbright);
                    }
                    // Inner (min): dashed, non-animated
                    if (area.advanced && area.minRadii != null) {
                        drawBoxOutline(queue, matrices, area.center.subtract(cam), area.minRadii, rr,gg,bb,a, fullbright, true, 0f);
                    } else {
                        // Approximate cube with dashed box using uniform radii
                        drawBoxOutline(queue, matrices, area.center.subtract(cam), new Vec3d(area.minRadius, area.minRadius, area.minRadius), rr,gg,bb,a, fullbright, true, 0f);
                    }
                } else {
                    // Ellipse/Sphere: Outer (max) remains dashed animated in XZ; inner (min) dashed non-animated in XZ
                    if (area.advanced && area.maxRadii != null) {
                        drawEllipsoidApprox(queue, matrices, area.center.subtract(cam), area.maxRadii, r,g,b,a, fullbright, true, dashPhase);
                    } else {
                        drawDashedEllipse(queue, matrices, area.center.subtract(cam), area.maxRadius, area.maxRadius, r,g,b,a, fullbright, dashPhase);
                    }
                    if (area.advanced && area.minRadii != null) {
                        drawEllipsoidApprox(queue, matrices, area.center.subtract(cam), area.minRadii, rr,gg,bb,a, fullbright, true, 0f);
                    } else {
                        drawDashedEllipse(queue, matrices, area.center.subtract(cam), area.minRadius, area.minRadius, rr,gg,bb,a, fullbright, 0f);
                    }
                }
            }

            // LookAt marker
            if (node.lookAt != null) {
                Vec3d la = node.lookAt.subtract(cam);
                float s = 0.3f;
                drawCircleApprox(queue, matrices, la, s, r, g, b, a, fullbright);
                drawCircleApprox(queue, matrices, la, s*0.6f, r, g, b, a, fullbright);
                // chevrons along link
                drawChevrons(queue, matrices,
                        p.x - cam.x, p.y - cam.y, p.z - cam.z,
                        la.x, la.y, la.z, r,g,b,a, fullbright, dashPhase);
            }
        }
    }

    private static void submitLine(OrderedRenderCommandQueue queue, MatrixStack matrices,
                                   float r, float g, float b, float a, int light,
                                   double ax, double ay, double az, double bx, double by, double bz) {
        RenderLayer layer = RenderLayer.getLines();
        var bq = queue.getBatchingQueue(1000);
        var cam = MinecraftClient.getInstance().gameRenderer.getCamera();
        var rot = cam.getRotation();
        org.joml.Vector3f fV = new org.joml.Vector3f(0f, 0f, -1f).rotate(rot);
        final float nx = fV.x, ny = fV.y, nz = fV.z;

        bq.submitCustom(matrices, layer, (entry, vc) -> {
            vc.vertex(entry, (float)ax, (float)ay, (float)az).color(r,g,b,a).normal(entry, nx, ny, nz);
            vc.vertex(entry, (float)bx, (float)by, (float)bz).color(r,g,b,a).normal(entry, nx, ny, nz);
        });
    }

    private static void drawBillboardQuad(OrderedRenderCommandQueue queue, MatrixStack matrices, Vec3d c,
                                          float size, float r, float g, float b, float a, int light) {
        var cam = MinecraftClient.getInstance().gameRenderer.getCamera();
        var rot = cam.getRotation();
        org.joml.Vector3f rv = new org.joml.Vector3f(1,0,0).rotate(rot);
        org.joml.Vector3f uv = new org.joml.Vector3f(0,1,0).rotate(rot);
        Vec3d right = new Vec3d(rv.x, rv.y, rv.z).multiply(size);
        Vec3d up = new Vec3d(uv.x, uv.y, uv.z).multiply(size);
        Vec3d p0 = c.subtract(right).subtract(up);
        Vec3d p1 = c.add(right).subtract(up);
        Vec3d p2 = c.add(right).add(up);
        Vec3d p3 = c.subtract(right).add(up);
        // approximate filled look by drawing border and diagonals
        submitLine(queue, matrices, r,g,b,a, light, p0.x,p0.y,p0.z, p1.x,p1.y,p1.z);
        submitLine(queue, matrices, r,g,b,a, light, p1.x,p1.y,p1.z, p2.x,p2.y,p2.z);
        submitLine(queue, matrices, r,g,b,a, light, p2.x,p2.y,p2.z, p3.x,p3.y,p3.z);
        submitLine(queue, matrices, r,g,b,a, light, p3.x,p3.y,p3.z, p0.x,p0.y,p0.z);
        // diagonals
        submitLine(queue, matrices, r,g,b,a, light, p0.x,p0.y,p0.z, p2.x,p2.y,p2.z);
        submitLine(queue, matrices, r,g,b,a, light, p1.x,p1.y,p1.z, p3.x,p3.y,p3.z);
    }

    private static void drawBillboardOutline(OrderedRenderCommandQueue queue, MatrixStack matrices, Vec3d c,
                                             float size, float r, float g, float b, float a, int light) {
        var cam = MinecraftClient.getInstance().gameRenderer.getCamera();
        var rot = cam.getRotation();
        org.joml.Vector3f rv = new org.joml.Vector3f(1,0,0).rotate(rot);
        org.joml.Vector3f uv = new org.joml.Vector3f(0,1,0).rotate(rot);
        Vec3d right = new Vec3d(rv.x, rv.y, rv.z).multiply(size);
        Vec3d up = new Vec3d(uv.x, uv.y, uv.z).multiply(size);
        Vec3d p0 = c.subtract(right).subtract(up);
        Vec3d p1 = c.add(right).subtract(up);
        Vec3d p2 = c.add(right).add(up);
        Vec3d p3 = c.subtract(right).add(up);
        submitLine(queue, matrices, r,g,b,a, light, p0.x,p0.y,p0.z, p1.x,p1.y,p1.z);
        submitLine(queue, matrices, r,g,b,a, light, p1.x,p1.y,p1.z, p2.x,p2.y,p2.z);
        submitLine(queue, matrices, r,g,b,a, light, p2.x,p2.y,p2.z, p3.x,p3.y,p3.z);
        submitLine(queue, matrices, r,g,b,a, light, p3.x,p3.y,p3.z, p0.x,p0.y,p0.z);
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

    private static void drawEllipseApprox(OrderedRenderCommandQueue queue, MatrixStack matrices, Vec3d c,
                                          double rx, double rz, float cr, float cg, float cb, float ca, int light) {
        int seg = 28;
        double prevx = c.x + rx, prevy = c.y, prevz = c.z;
        for (int i=1;i<=seg;i++) {
            double ang = (i * 2 * Math.PI)/seg;
            double x = c.x + Math.cos(ang) * rx;
            double z = c.z + Math.sin(ang) * rz;
            submitLine(queue, matrices, cr,cg,cb,ca, light, prevx, prevy, prevz, x, c.y, z);
            prevx = x; prevy = c.y; prevz = z;
        }
    }

    private static void drawEllipsoidApprox(OrderedRenderCommandQueue queue, MatrixStack matrices, Vec3d center,
                                            Vec3d radii, float cr, float cg, float cb, float ca, int light,
                                            boolean dashed, float phase) {
        // Approximate with 3 orthogonal ellipses
        // XZ plane (around Y)
        if (!dashed) drawEllipseApprox(queue, matrices, center, radii.x, radii.z, cr,cg,cb,ca, light);
        else drawDashedEllipse(queue, matrices, center, radii.x, radii.z, cr,cg,cb,ca, light, phase);
        // XY plane (around Z)
        if (!dashed) drawEllipseApproxVertical(queue, matrices, center, radii.x, radii.y, cr,cg,cb,ca, light, 0);
        else drawEllipseApproxVertical(queue, matrices, center, radii.x, radii.y, cr,cg,cb,ca, light, phase);
        // YZ plane (around X)
        if (!dashed) drawEllipseApproxVerticalYZ(queue, matrices, center, radii.y, radii.z, cr,cg,cb,ca, light, 0);
        else drawEllipseApproxVerticalYZ(queue, matrices, center, radii.y, radii.z, cr,cg,cb,ca, light, phase);
    }

    private static void drawEllipseApproxVertical(OrderedRenderCommandQueue queue, MatrixStack matrices, Vec3d c,
                                                  double rx, double ry, float cr, float cg, float cb, float ca, int light, float phase) {
        int seg = 28;
        double prevx = c.x + rx, prevy = c.y, prevz = c.z;
        for (int i=1;i<=seg;i++) {
            double ang = (i * 2 * Math.PI)/seg;
            double x = c.x + Math.cos(ang) * rx;
            double y = c.y + Math.sin(ang) * ry;
            submitLine(queue, matrices, cr,cg,cb,ca, light, prevx, prevy, prevz, x, y, c.z);
            prevx = x; prevy = y; prevz = c.z;
        }
    }

    private static void drawEllipseApproxVerticalYZ(OrderedRenderCommandQueue queue, MatrixStack matrices, Vec3d c,
                                                    double ry, double rz, float cr, float cg, float cb, float ca, int light, float phase) {
        int seg = 28;
        double prevx = c.x, prevy = c.y + ry, prevz = c.z;
        for (int i=1;i<=seg;i++) {
            double ang = (i * 2 * Math.PI)/seg;
            double y = c.y + Math.cos(ang) * ry;
            double z = c.z + Math.sin(ang) * rz;
            submitLine(queue, matrices, cr,cg,cb,ca, light, prevx, prevy, prevz, c.x, y, z);
            prevx = c.x; prevy = y; prevz = z;
        }
    }

    private static void drawDashedEllipse(OrderedRenderCommandQueue queue, MatrixStack matrices, Vec3d c,
                                          double rx, double rz, float cr, float cg, float cb, float ca, int light, float phase) {
        int seg = 64;
        double prevx = c.x + rx, prevy = c.y, prevz = c.z;
        for (int i=1;i<=seg;i++) {
            double t = (i / (double)seg);
            double ang = t * 2 * Math.PI;
            double x = c.x + Math.cos(ang) * rx;
            double z = c.z + Math.sin(ang) * rz;
            if (isDashVisible(t, 0.5f/8f, 0.3f/8f, phase)) {
                submitLine(queue, matrices, cr,cg,cb,ca, light, prevx, prevy, prevz, x, c.y, z);
            }
            prevx = x; prevy = c.y; prevz = z;
        }
    }

    private static boolean isDashVisible(double t, float dashLength, float gapLength, float phase) {
        double cycle = dashLength + gapLength;
        double u = (t + phase) % 1.0;
        double p = (u % cycle);
        return p < dashLength;
    }

    private static void submitDashedLine(OrderedRenderCommandQueue queue, MatrixStack matrices,
                                         float r, float g, float b, float a, int light,
                                         double ax, double ay, double az, double bx, double by, double bz,
                                         float dashLength, float gapLength, float phase) {
        int seg = 32;
        double px = ax, py = ay, pz = az;
        for (int i=1;i<=seg;i++) {
            double t = i/(double)seg;
            double x = ax + (bx-ax)*t;
            double y = ay + (by-ay)*t;
            double z = az + (bz-az)*t;
            if (isDashVisible(t, dashLength, gapLength, phase)) {
                submitLine(queue, matrices, r,g,b,a, light, px,py,pz, x,y,z);
            }
            px = x; py = y; pz = z;
        }
    }

    private static void drawChevrons(OrderedRenderCommandQueue queue, MatrixStack matrices,
                                     double ax, double ay, double az, double bx, double by, double bz,
                                     float r, float g, float b, float a, int light, float phase) {
        int count = 6;
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double len = Math.sqrt(dx*dx + dy*dy + dz*dz);
        if (len < 1e-3) return;
        double ux = dx/len, uy = dy/len, uz = dz/len;
        double step = len / (count + 1);
        double size = Math.min(0.4, step * 0.3);
        for (int i=1;i<=count;i++) {
            double t = (i/(double)(count+1) + phase) % 1.0;
            double cx = ax + dx * t;
            double cy = ay + dy * t;
            double cz = az + dz * t;
            // make a small V shape oriented along the line (using a simple perpendicular in XZ plane)
            // compute a rough side vector in XZ
            double sx = -uz, sz = ux; double sy = 0;
            double sLen = Math.sqrt(sx*sx + sz*sz); if (sLen < 1e-6) { sx = 0; sz = 1; sLen = 1; }
            sx /= sLen; sz /= sLen;
            double ex = cx - ux * size; double ey = cy - uy * size; double ez = cz - uz * size;
            submitLine(queue, matrices, r,g,b,a, light, ex,ey,ez, cx + sx*size*0.6, cy + sy*size*0.6, cz + sz*size*0.6);
            submitLine(queue, matrices, r,g,b,a, light, ex,ey,ez, cx - sx*size*0.6, cy - sy*size*0.6, cz - sz*size*0.6);
        }
    }

    private static void drawBoxOutline(OrderedRenderCommandQueue queue, MatrixStack matrices, Vec3d center,
                                       Vec3d r, float cr, float cg, float cb, float ca, int light, boolean dashed, float phase) {
        double x0 = center.x - r.x, x1 = center.x + r.x;
        double y0 = center.y - r.y, y1 = center.y + r.y;
        double z0 = center.z - r.z, z1 = center.z + r.z;
        // 12 edges
        if (dashed) submitDashedLine(queue, matrices, cr,cg,cb,ca, light, x0,y0,z0, x1,y0,z0, 0.5f/8f,0.3f/8f, phase); else submitLine(queue, matrices, cr,cg,cb,ca, light, x0,y0,z0, x1,y0,z0);
        if (dashed) submitDashedLine(queue, matrices, cr,cg,cb,ca, light, x0,y0,z0, x0,y1,z0, 0.5f/8f,0.3f/8f, phase); else submitLine(queue, matrices, cr,cg,cb,ca, light, x0,y0,z0, x0,y1,z0);
        if (dashed) submitDashedLine(queue, matrices, cr,cg,cb,ca, light, x0,y0,z0, x0,y0,z1, 0.5f/8f,0.3f/8f, phase); else submitLine(queue, matrices, cr,cg,cb,ca, light, x0,y0,z0, x0,y0,z1);

        if (dashed) submitDashedLine(queue, matrices, cr,cg,cb,ca, light, x1,y1,z1, x0,y1,z1, 0.5f/8f,0.3f/8f, phase); else submitLine(queue, matrices, cr,cg,cb,ca, light, x1,y1,z1, x0,y1,z1);
        if (dashed) submitDashedLine(queue, matrices, cr,cg,cb,ca, light, x1,y1,z1, x1,y0,z1, 0.5f/8f,0.3f/8f, phase); else submitLine(queue, matrices, cr,cg,cb,ca, light, x1,y1,z1, x1,y0,z1);
        if (dashed) submitDashedLine(queue, matrices, cr,cg,cb,ca, light, x1,y1,z1, x1,y1,z0, 0.5f/8f,0.3f/8f, phase); else submitLine(queue, matrices, cr,cg,cb,ca, light, x1,y1,z1, x1,y1,z0);

        if (dashed) submitDashedLine(queue, matrices, cr,cg,cb,ca, light, x1,y0,z0, x1,y1,z0, 0.5f/8f,0.3f/8f, phase); else submitLine(queue, matrices, cr,cg,cb,ca, light, x1,y0,z0, x1,y1,z0);
        if (dashed) submitDashedLine(queue, matrices, cr,cg,cb,ca, light, x1,y0,z0, x1,y0,z1, 0.5f/8f,0.3f/8f, phase); else submitLine(queue, matrices, cr,cg,cb,ca, light, x1,y0,z0, x1,y0,z1);
        if (dashed) submitDashedLine(queue, matrices, cr,cg,cb,ca, light, x0,y1,z0, x1,y1,z0, 0.5f/8f,0.3f/8f, phase); else submitLine(queue, matrices, cr,cg,cb,ca, light, x0,y1,z0, x1,y1,z0);
        if (dashed) submitDashedLine(queue, matrices, cr,cg,cb,ca, light, x0,y1,z0, x0,y1,z1, 0.5f/8f,0.3f/8f, phase); else submitLine(queue, matrices, cr,cg,cb,ca, light, x0,y1,z0, x0,y1,z1);
        if (dashed) submitDashedLine(queue, matrices, cr,cg,cb,ca, light, x0,y0,z1, x1,y0,z1, 0.5f/8f,0.3f/8f, phase); else submitLine(queue, matrices, cr,cg,cb,ca, light, x0,y0,z1, x1,y0,z1);
        if (dashed) submitDashedLine(queue, matrices, cr,cg,cb,ca, light, x0,y0,z1, x0,y1,z1, 0.5f/8f,0.3f/8f, phase); else submitLine(queue, matrices, cr,cg,cb,ca, light, x0,y0,z1, x0,y1,z1);
    }
}
