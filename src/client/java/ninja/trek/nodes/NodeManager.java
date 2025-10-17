package ninja.trek.nodes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import ninja.trek.Craneshot;
import ninja.trek.cameramovements.CameraTarget;
import ninja.trek.nodes.io.NodeStorage;
import ninja.trek.nodes.model.*;

import java.util.*;

public class NodeManager {
    private static final NodeManager INSTANCE = new NodeManager();
    public static NodeManager get() { return INSTANCE; }

    private final List<CameraNode> nodes = new ArrayList<>();
    private UUID selectedNodeId = null;
    private boolean editing = false;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // Edit-mode rotation override (used when a Screen is open)
    private boolean hasEditRotation = false;
    private float editYaw = 0f;
    private float editPitch = 0f;

    public void load() {
        nodes.clear();
        nodes.addAll(NodeStorage.load());
    }

    public void save() { NodeStorage.save(nodes); }

    public boolean isEditing() { return editing; }
    public void setEditing(boolean e) { this.editing = e; }

    public void setEditRotation(float yaw, float pitch) {
        this.editYaw = yaw;
        this.editPitch = pitch;
        this.hasEditRotation = true;
    }
    public boolean hasEditRotation() { return hasEditRotation; }
    public float getEditYaw() { return editYaw; }
    public float getEditPitch() { return editPitch; }
    public void clearEditRotation() { this.hasEditRotation = false; }

    public List<CameraNode> getNodes() { return Collections.unmodifiableList(nodes); }

    public CameraNode addNode(Vec3d position) {
        CameraNode node = new CameraNode();
        node.position = position;
        nodes.add(node);
        save();
        return node;
    }

    public void removeSelected() {
        if (selectedNodeId == null) return;
        nodes.removeIf(n -> n.id.equals(selectedNodeId));
        selectedNodeId = null;
        save();
    }

    public void setSelected(UUID id) { this.selectedNodeId = id; }
    public CameraNode getSelected() {
        if (selectedNodeId == null) return null;
        for (var n : nodes) if (n.id.equals(selectedNodeId)) return n;
        return null;
    }

    public CameraNode selectNearestToScreen(double mouseX, double mouseY, int screenW, int screenH, Camera camera) {
        // Phase 1: approximate by nearest 3D distance to camera ray through center direction
        // TODO (phase 2): true screen-space projection selection.
        Vec3d camPos = camera.getPos();
        double best = Double.MAX_VALUE;
        CameraNode bestNode = null;
        for (var n : nodes) {
            double d = n.position.squaredDistanceTo(camPos);
            if (d < best) { best = d; bestNode = n; }
        }
        if (bestNode != null) {
            selectedNodeId = bestNode.id;
        }
        return bestNode;
    }

    public Area addAreaTo(CameraNode node, Vec3d center) {
        Area a = new Area(AreaShape.CUBE, center, 8.0, 16.0);
        node.areas.add(a);
        save();
        return a;
    }

    public void setLookAt(CameraNode node, Vec3d lookAt) { node.lookAt = lookAt; save(); }
    public void unsetLookAt(CameraNode node) { node.lookAt = null; save(); }
    public void removeArea(CameraNode node, Area area) { if (node != null && area != null) { node.areas.remove(area); save(); } }

    // Influence computation
    public CameraTarget applyInfluence(CameraTarget base, boolean skipInfluence) {
        if (skipInfluence || nodes.isEmpty() || base == null) return base;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return base;
        Vec3d playerPos = mc.player.getEyePos();

        double totalWeight = 0.0;
        Vec3d accumPos = Vec3d.ZERO;
        float yawBase = base.getYaw();
        float pitchBase = base.getPitch();
        float accumYaw = 0f;
        float accumPitch = 0f;
        boolean anyLookAt = false;

        for (var node : nodes) {
            double w = 0.0;
            for (var area : node.areas) {
                double dist = switch (area.shape) {
                    case SPHERE -> playerPos.distanceTo(area.center);
                    case CUBE -> cubeDistance(playerPos, area.center, area.maxRadius);
                };
                double t = 0.0;
                if (dist <= area.minRadius) t = 1.0;
                else if (dist >= area.maxRadius) t = 0.0;
                else t = 1.0 - ((dist - area.minRadius) / (area.maxRadius - area.minRadius));
                w = Math.max(w, MathHelper.clamp(t, 0.0, 1.0));
            }
            if (w <= 0) continue;

            Vec3d nodeTarget = node.position;
            if (node.type == NodeType.DRONE_SHOT) {
                long timeMs = System.currentTimeMillis();
                double angleDeg = node.droneStartAngleDeg + (timeMs / 1000.0) * node.droneSpeedDegPerSec;
                double rad = Math.toRadians(angleDeg % 360.0);
                nodeTarget = new Vec3d(
                    node.position.x + Math.cos(rad) * node.droneRadius,
                    node.position.y,
                    node.position.z + Math.sin(rad) * node.droneRadius
                );
            }

            accumPos = accumPos.add(nodeTarget.multiply(w));
            totalWeight += w;

            if (node.lookAt != null) {
                Vec3d dir = node.lookAt.subtract(base.getPosition()).normalize();
                float nYaw = (float)(Math.toDegrees(Math.atan2(dir.x, dir.z)));
                float nPitch = (float)(-Math.toDegrees(Math.asin(dir.y)));
                // blend angles by weight in linear space for phase 1
                accumYaw += nYaw * w;
                accumPitch += nPitch * w;
                anyLookAt = true;
            }
        }

        if (totalWeight <= 1e-6) return base;
        if (totalWeight > 1.0) {
            accumPos = accumPos.multiply(1.0 / totalWeight);
            accumYaw /= totalWeight;
            accumPitch /= totalWeight;
            totalWeight = 1.0;
        }

        Vec3d blendedPos = base.getPosition().multiply(1.0 - totalWeight).add(accumPos);
        float outYaw = yawBase;
        float outPitch = pitchBase;
        if (anyLookAt) {
            outYaw = (float)((yawBase * (1.0 - totalWeight)) + (accumYaw * totalWeight));
            outPitch = (float)((pitchBase * (1.0 - totalWeight)) + (accumPitch * totalWeight));
        }

        return new CameraTarget(blendedPos, outYaw, outPitch, base.getFovMultiplier(), base.getOrthoFactor());
    }

    private double cubeDistance(Vec3d p, Vec3d c, double r) {
        // approximate distance to cube by max of axis distances
        double dx = Math.max(Math.abs(p.x - c.x) - r, 0);
        double dy = Math.max(Math.abs(p.y - c.y) - r, 0);
        double dz = Math.max(Math.abs(p.z - c.z) - r, 0);
        return Math.sqrt(dx*dx + dy*dy + dz*dz);
    }
}
