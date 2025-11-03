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
        }

        AreaInstance selectedArea = NodeManager.get().getSelectedArea();
        for (var area : NodeManager.get().getAreas()) {
            boolean isSelected = selectedArea != null && selectedArea.id.equals(area.id);
            int color = isSelected ? 0xFF66FFAA : 0xFF4CB3FF;
            float a = 0.65f;
            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;
            float rr = r * 0.6f;
            float gg = g * 0.6f;
            float bb = b * 0.6f;

            Vec3d rel = area.center.subtract(cam);
            if (area.shape == AreaShape.CUBE) {
                if (area.advanced && area.outsideRadii != null) {
                    drawBoxOutline(queue, matrices, rel, area.outsideRadii, r, g, b, a, fullbright, true, dashPhase);
                } else {
                    drawCubeOutline(queue, matrices, rel, area.outsideRadius, r, g, b, a, fullbright);
                }

                if (area.advanced && area.insideRadii != null) {
                    drawBoxOutline(queue, matrices, rel, area.insideRadii, rr, gg, bb, a, fullbright, true, 0f);
                } else {
                    Vec3d inner = new Vec3d(area.insideRadius, area.insideRadius, area.insideRadius);
                    drawBoxOutline(queue, matrices, rel, inner, rr, gg, bb, a, fullbright, true, 0f);
                }
            } else {
                if (area.advanced && area.outsideRadii != null) {
                    drawEllipsoidApprox(queue, matrices, rel, area.outsideRadii, r, g, b, a, fullbright, true, dashPhase);
                } else {
                    drawDashedEllipse(queue, matrices, rel, area.outsideRadius, area.outsideRadius, r, g, b, a, fullbright, dashPhase);
                }

                if (area.advanced && area.insideRadii != null) {
                    drawEllipsoidApprox(queue, matrices, rel, area.insideRadii, rr, gg, bb, a, fullbright, true, 0f);
                } else {
                    drawDashedEllipse(queue, matrices, rel, area.insideRadius, area.insideRadius, rr, gg, bb, a, fullbright, 0f);
                }
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

    private static void drawBillboardCircle(OrderedRenderCommandQueue queue, MatrixStack matrices, Vec3d c,
                                            double r, float cr, float cg, float cb, float ca, int light) {
        // Get camera-aligned vectors for billboarding
        var cam = MinecraftClient.getInstance().gameRenderer.getCamera();
        var rot = cam.getRotation();
        org.joml.Vector3f rv = new org.joml.Vector3f(1,0,0).rotate(rot);
        org.joml.Vector3f uv = new org.joml.Vector3f(0,1,0).rotate(rot);

        int seg = 24;
        double prevx = c.x + rv.x * r;
        double prevy = c.y + rv.y * r;
        double prevz = c.z + rv.z * r;

        for (int i=1;i<=seg;i++) {
            double ang = (i * 2 * Math.PI)/seg;
            double x = c.x + (Math.cos(ang) * rv.x + Math.sin(ang) * uv.x) * r;
            double y = c.y + (Math.cos(ang) * rv.y + Math.sin(ang) * uv.y) * r;
            double z = c.z + (Math.cos(ang) * rv.z + Math.sin(ang) * uv.z) * r;
            submitLine(queue, matrices, cr,cg,cb,ca, light, prevx, prevy, prevz, x, y, z);
            prevx = x; prevy = y; prevz = z;
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
        int count = 12;
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double len = Math.sqrt(dx*dx + dy*dy + dz*dz);
        if (len < 1e-3) return;
        double ux = dx/len, uy = dy/len, uz = dz/len;
        double step = len / (count + 1);
        double size = Math.min(0.4, step * 0.3);

        // Get camera-aligned side vector for billboarding
        var cam = MinecraftClient.getInstance().gameRenderer.getCamera();
        var rot = cam.getRotation();
        org.joml.Vector3f rv = new org.joml.Vector3f(1,0,0).rotate(rot);
        double sx = rv.x, sy = rv.y, sz = rv.z;

        for (int i=1;i<=count;i++) {
            double t = (i/(double)(count+1) + phase) % 1.0;
            double cx = ax + dx * t;
            double cy = ay + dy * t;
            double cz = az + dz * t;
            // Reverse direction: point AWAY from lookAt (toward node)
            double ex = cx + ux * size; double ey = cy + uy * size; double ez = cz + uz * size;
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
