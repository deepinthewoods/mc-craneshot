package ninja.trek.nodes.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import ninja.trek.config.GeneralMenuSettings;
import ninja.trek.nodes.NodeManager;
import ninja.trek.nodes.model.AreaInstance;
import ninja.trek.nodes.model.AreaShape;
import ninja.trek.nodes.model.CameraNode;
import org.joml.Vector3f;

public class NodeRenderer {
    private static final float DASH_LENGTH = 0.5f / 8f;
    private static final float GAP_LENGTH = 0.3f / 8f;

    public static void render(MatrixStack matrices, WorldRenderState state, OrderedRenderCommandQueue queue) {
        NodeManager manager = NodeManager.get();
        boolean showOutsideEdit = GeneralMenuSettings.isShowNodesOutsideEdit();
        if (!manager.isEditing() && !showOutsideEdit) return;
        if (manager.getNodes().isEmpty() && manager.getAreas().isEmpty()) return;

        Vec3d camPos = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
        int viewDist = Math.max(2, MinecraftClient.getInstance().options.getViewDistance().getValue());
        int camCX = (int)Math.floor(camPos.x) >> 4;
        int camCZ = (int)Math.floor(camPos.z) >> 4;
        int fullbright = 0x00F000F0;

        long nowMs = System.currentTimeMillis();
        float dashPhase = (nowMs % (1000L * 8)) / (1000f * 8); // 0..1 over 8 seconds

        for (CameraNode node : manager.getNodes()) {
            int nCX = (int)Math.floor(node.position.x) >> 4;
            int nCZ = (int)Math.floor(node.position.z) >> 4;
            if (Math.abs(nCX - camCX) > viewDist || Math.abs(nCZ - camCZ) > viewDist) continue;
            int color = node.colorARGB != null ? node.colorARGB : 0xFFFFFFFF;
            float a = ((color >> 24) & 0xFF) / 255f;
            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;

            Vec3d rel = node.position.subtract(camPos);
            float size = 0.2f;
            drawBillboardQuad(queue, matrices, rel, size, r, g, b, a, fullbright);
            CameraNode sel = manager.getSelected();
            if (sel != null && sel.id.equals(node.id)) {
                drawBillboardOutline(queue, matrices, rel, size * 1.1f, 1f, 1f, 1f, a, fullbright);
            }
        }

        AreaInstance selectedArea = manager.getSelectedArea();
        for (AreaInstance area : manager.getAreas()) {
            boolean isSelected = selectedArea != null && selectedArea.id.equals(area.id);
            int color = isSelected ? 0xFF66FFAA : 0xFF4CB3FF;
            float a = 0.65f;
            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;
            float rr = r * 0.6f;
            float gg = g * 0.6f;
            float bb = b * 0.6f;

            Vec3d worldCenter = area.center != null ? area.center : Vec3d.ZERO;
            Vec3d relCenter = worldCenter.subtract(camPos);
            if (area.shape == AreaShape.CUBE) {
                if (area.advanced && area.outsideRadii != null) {
                    drawBoxOutline(queue, matrices, relCenter, area.outsideRadii, r, g, b, a, fullbright, true, dashPhase);
                } else {
                    drawCubeOutline(queue, matrices, relCenter, area.outsideRadius, r, g, b, a, fullbright);
                }

                if (area.advanced && area.insideRadii != null) {
                    drawBoxOutline(queue, matrices, relCenter, area.insideRadii, rr, gg, bb, a, fullbright, true, 0f);
                } else {
                    Vec3d inner = new Vec3d(area.insideRadius, area.insideRadius, area.insideRadius);
                    drawBoxOutline(queue, matrices, relCenter, inner, rr, gg, bb, a, fullbright, true, 0f);
                }
            } else {
                if (area.advanced && area.outsideRadii != null) {
                    drawEllipsoidApprox(queue, matrices, relCenter, area.outsideRadii, r, g, b, a, fullbright, true, dashPhase);
                } else {
                    drawDashedEllipse(queue, matrices, relCenter, area.outsideRadius, area.outsideRadius, r, g, b, a, fullbright, dashPhase);
                }

                if (area.advanced && area.insideRadii != null) {
                    drawEllipsoidApprox(queue, matrices, relCenter, area.insideRadii, rr, gg, bb, a, fullbright, true, 0f);
                } else {
                    drawDashedEllipse(queue, matrices, relCenter, area.insideRadius, area.insideRadius, rr, gg, bb, a, fullbright, 0f);
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
        Vector3f forward = new Vector3f(0f, 0f, -1f).rotate(rot);
        final float nx = forward.x, ny = forward.y, nz = forward.z;

        bq.submitCustom(matrices, layer, (entry, vc) -> {
            vc.vertex(entry, (float)ax, (float)ay, (float)az).color(r, g, b, a).normal(entry, nx, ny, nz);
            vc.vertex(entry, (float)bx, (float)by, (float)bz).color(r, g, b, a).normal(entry, nx, ny, nz);
        });
    }

    private static void drawBillboardQuad(OrderedRenderCommandQueue queue, MatrixStack matrices, Vec3d c,
                                          float size, float r, float g, float b, float a, int light) {
        var cam = MinecraftClient.getInstance().gameRenderer.getCamera();
        var rot = cam.getRotation();
        Vector3f rv = new Vector3f(1, 0, 0).rotate(rot);
        Vector3f uv = new Vector3f(0, 1, 0).rotate(rot);
        Vec3d right = new Vec3d(rv.x, rv.y, rv.z).multiply(size);
        Vec3d up = new Vec3d(uv.x, uv.y, uv.z).multiply(size);
        Vec3d p0 = c.subtract(right).subtract(up);
        Vec3d p1 = c.add(right).subtract(up);
        Vec3d p2 = c.add(right).add(up);
        Vec3d p3 = c.subtract(right).add(up);
        submitLine(queue, matrices, r, g, b, a, light, p0.x, p0.y, p0.z, p1.x, p1.y, p1.z);
        submitLine(queue, matrices, r, g, b, a, light, p1.x, p1.y, p1.z, p2.x, p2.y, p2.z);
        submitLine(queue, matrices, r, g, b, a, light, p2.x, p2.y, p2.z, p3.x, p3.y, p3.z);
        submitLine(queue, matrices, r, g, b, a, light, p3.x, p3.y, p3.z, p0.x, p0.y, p0.z);
        submitLine(queue, matrices, r, g, b, a, light, p0.x, p0.y, p0.z, p2.x, p2.y, p2.z);
        submitLine(queue, matrices, r, g, b, a, light, p1.x, p1.y, p1.z, p3.x, p3.y, p3.z);
    }

    private static void drawBillboardOutline(OrderedRenderCommandQueue queue, MatrixStack matrices, Vec3d c,
                                             float size, float r, float g, float b, float a, int light) {
        var cam = MinecraftClient.getInstance().gameRenderer.getCamera();
        var rot = cam.getRotation();
        Vector3f rv = new Vector3f(1, 0, 0).rotate(rot);
        Vector3f uv = new Vector3f(0, 1, 0).rotate(rot);
        Vec3d right = new Vec3d(rv.x, rv.y, rv.z).multiply(size);
        Vec3d up = new Vec3d(uv.x, uv.y, uv.z).multiply(size);
        Vec3d p0 = c.subtract(right).subtract(up);
        Vec3d p1 = c.add(right).subtract(up);
        Vec3d p2 = c.add(right).add(up);
        Vec3d p3 = c.subtract(right).add(up);
        submitLine(queue, matrices, r, g, b, a, light, p0.x, p0.y, p0.z, p1.x, p1.y, p1.z);
        submitLine(queue, matrices, r, g, b, a, light, p1.x, p1.y, p1.z, p2.x, p2.y, p2.z);
        submitLine(queue, matrices, r, g, b, a, light, p2.x, p2.y, p2.z, p3.x, p3.y, p3.z);
        submitLine(queue, matrices, r, g, b, a, light, p3.x, p3.y, p3.z, p0.x, p0.y, p0.z);
    }

    private static void drawCubeOutline(OrderedRenderCommandQueue queue, MatrixStack matrices, Vec3d center,
                                        double radius, float r, float g, float b, float a, int light) {
        double x0 = center.x - radius, x1 = center.x + radius;
        double y0 = center.y - radius, y1 = center.y + radius;
        double z0 = center.z - radius, z1 = center.z + radius;

        submitLine(queue, matrices, r, g, b, a, light, x0, y0, z0, x1, y0, z0);
        submitLine(queue, matrices, r, g, b, a, light, x0, y0, z0, x0, y1, z0);
        submitLine(queue, matrices, r, g, b, a, light, x0, y0, z0, x0, y0, z1);

        submitLine(queue, matrices, r, g, b, a, light, x1, y1, z1, x0, y1, z1);
        submitLine(queue, matrices, r, g, b, a, light, x1, y1, z1, x1, y0, z1);
        submitLine(queue, matrices, r, g, b, a, light, x1, y1, z1, x1, y1, z0);

        submitLine(queue, matrices, r, g, b, a, light, x1, y0, z0, x1, y1, z0);
        submitLine(queue, matrices, r, g, b, a, light, x1, y0, z0, x1, y0, z1);
        submitLine(queue, matrices, r, g, b, a, light, x0, y1, z0, x1, y1, z0);
        submitLine(queue, matrices, r, g, b, a, light, x0, y1, z0, x0, y1, z1);
        submitLine(queue, matrices, r, g, b, a, light, x0, y0, z1, x1, y0, z1);
        submitLine(queue, matrices, r, g, b, a, light, x0, y0, z1, x0, y1, z1);
    }

    private static void drawEllipsoidApprox(OrderedRenderCommandQueue queue, MatrixStack matrices, Vec3d center,
                                            Vec3d radii, float r, float g, float b, float a, int light,
                                            boolean dashed, float phase) {
        if (!dashed) {
            drawEllipseApprox(queue, matrices, center, radii.x, radii.z, r, g, b, a, light);
            drawEllipseApproxVertical(queue, matrices, center, radii.x, radii.y, r, g, b, a, light);
            drawEllipseApproxVerticalYZ(queue, matrices, center, radii.y, radii.z, r, g, b, a, light);
        } else {
            drawDashedEllipse(queue, matrices, center, radii.x, radii.z, r, g, b, a, light, phase);
            drawDashedEllipseVertical(queue, matrices, center, radii.x, radii.y, r, g, b, a, light, phase);
            drawDashedEllipseVerticalYZ(queue, matrices, center, radii.y, radii.z, r, g, b, a, light, phase);
        }
    }

    private static void drawEllipseApprox(OrderedRenderCommandQueue queue, MatrixStack matrices, Vec3d center,
                                          double rx, double rz, float r, float g, float b, float a, int light) {
        int seg = 28;
        double prevx = center.x + rx, prevz = center.z;
        double prevy = center.y;
        for (int i = 1; i <= seg; i++) {
            double ang = (i * 2 * Math.PI) / seg;
            double x = center.x + Math.cos(ang) * rx;
            double z = center.z + Math.sin(ang) * rz;
            submitLine(queue, matrices, r, g, b, a, light, prevx, prevy, prevz, x, center.y, z);
            prevx = x;
            prevz = z;
        }
    }

    private static void drawEllipseApproxVertical(OrderedRenderCommandQueue queue, MatrixStack matrices, Vec3d center,
                                                  double rx, double ry, float r, float g, float b, float a, int light) {
        int seg = 28;
        double prevx = center.x + rx, prevy = center.y, prevz = center.z;
        for (int i = 1; i <= seg; i++) {
            double ang = (i * 2 * Math.PI) / seg;
            double x = center.x + Math.cos(ang) * rx;
            double y = center.y + Math.sin(ang) * ry;
            submitLine(queue, matrices, r, g, b, a, light, prevx, prevy, prevz, x, y, center.z);
            prevx = x;
            prevy = y;
        }
    }

    private static void drawEllipseApproxVerticalYZ(OrderedRenderCommandQueue queue, MatrixStack matrices, Vec3d center,
                                                    double ry, double rz, float r, float g, float b, float a, int light) {
        int seg = 28;
        double prevy = center.y + ry, prevz = center.z;
        double prevx = center.x;
        for (int i = 1; i <= seg; i++) {
            double ang = (i * 2 * Math.PI) / seg;
            double y = center.y + Math.cos(ang) * ry;
            double z = center.z + Math.sin(ang) * rz;
            submitLine(queue, matrices, r, g, b, a, light, prevx, prevy, prevz, center.x, y, z);
            prevy = y;
            prevz = z;
        }
    }

    private static void drawDashedEllipse(OrderedRenderCommandQueue queue, MatrixStack matrices, Vec3d center,
                                          double rx, double rz, float r, float g, float b, float a, int light,
                                          float phase) {
        int seg = 64;
        double prevx = center.x + rx, prevz = center.z;
        double prevy = center.y;
        for (int i = 1; i <= seg; i++) {
            double t = i / (double) seg;
            double ang = t * 2 * Math.PI;
            double x = center.x + Math.cos(ang) * rx;
            double z = center.z + Math.sin(ang) * rz;
            if (isDashVisible(t, phase)) {
                submitLine(queue, matrices, r, g, b, a, light, prevx, prevy, prevz, x, center.y, z);
            }
            prevx = x;
            prevz = z;
        }
    }

    private static void drawDashedEllipseVertical(OrderedRenderCommandQueue queue, MatrixStack matrices, Vec3d center,
                                                  double rx, double ry, float r, float g, float b, float a, int light,
                                                  float phase) {
        int seg = 64;
        double prevx = center.x + rx, prevy = center.y, prevz = center.z;
        for (int i = 1; i <= seg; i++) {
            double t = i / (double) seg;
            double ang = t * 2 * Math.PI;
            double x = center.x + Math.cos(ang) * rx;
            double y = center.y + Math.sin(ang) * ry;
            if (isDashVisible(t, phase)) {
                submitLine(queue, matrices, r, g, b, a, light, prevx, prevy, prevz, x, y, center.z);
            }
            prevx = x;
            prevy = y;
        }
    }

    private static void drawDashedEllipseVerticalYZ(OrderedRenderCommandQueue queue, MatrixStack matrices, Vec3d center,
                                                    double ry, double rz, float r, float g, float b, float a, int light,
                                                    float phase) {
        int seg = 64;
        double prevy = center.y + ry, prevz = center.z;
        double prevx = center.x;
        for (int i = 1; i <= seg; i++) {
            double t = i / (double) seg;
            double ang = t * 2 * Math.PI;
            double y = center.y + Math.cos(ang) * ry;
            double z = center.z + Math.sin(ang) * rz;
            if (isDashVisible(t, phase)) {
                submitLine(queue, matrices, r, g, b, a, light, prevx, prevy, prevz, center.x, y, z);
            }
            prevy = y;
            prevz = z;
        }
    }

    private static void drawBoxOutline(OrderedRenderCommandQueue queue, MatrixStack matrices, Vec3d center, Vec3d radii,
                                       float r, float g, float b, float a, int light, boolean dashed, float phase) {
        double x0 = center.x - radii.x, x1 = center.x + radii.x;
        double y0 = center.y - radii.y, y1 = center.y + radii.y;
        double z0 = center.z - radii.z, z1 = center.z + radii.z;

        drawBoxEdge(queue, matrices, r, g, b, a, light, x0, y0, z0, x1, y0, z0, dashed, phase);
        drawBoxEdge(queue, matrices, r, g, b, a, light, x0, y0, z0, x0, y1, z0, dashed, phase);
        drawBoxEdge(queue, matrices, r, g, b, a, light, x0, y0, z0, x0, y0, z1, dashed, phase);

        drawBoxEdge(queue, matrices, r, g, b, a, light, x1, y1, z1, x0, y1, z1, dashed, phase);
        drawBoxEdge(queue, matrices, r, g, b, a, light, x1, y1, z1, x1, y0, z1, dashed, phase);
        drawBoxEdge(queue, matrices, r, g, b, a, light, x1, y1, z1, x1, y1, z0, dashed, phase);

        drawBoxEdge(queue, matrices, r, g, b, a, light, x1, y0, z0, x1, y1, z0, dashed, phase);
        drawBoxEdge(queue, matrices, r, g, b, a, light, x1, y0, z0, x1, y0, z1, dashed, phase);
        drawBoxEdge(queue, matrices, r, g, b, a, light, x0, y1, z0, x1, y1, z0, dashed, phase);
        drawBoxEdge(queue, matrices, r, g, b, a, light, x0, y1, z0, x0, y1, z1, dashed, phase);
        drawBoxEdge(queue, matrices, r, g, b, a, light, x0, y0, z1, x1, y0, z1, dashed, phase);
        drawBoxEdge(queue, matrices, r, g, b, a, light, x0, y0, z1, x0, y1, z1, dashed, phase);
    }

    private static void drawBoxEdge(OrderedRenderCommandQueue queue, MatrixStack matrices,
                                    float r, float g, float b, float a, int light,
                                    double ax, double ay, double az, double bx, double by, double bz,
                                    boolean dashed, float phase) {
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
            px = x;
            py = y;
            pz = z;
        }
    }

    private static boolean isDashVisible(double t, float phase) {
        double cycle = DASH_LENGTH + GAP_LENGTH;
        double u = (t + phase) % 1.0;
        double p = (u % cycle);
        return p < DASH_LENGTH;
    }
}
