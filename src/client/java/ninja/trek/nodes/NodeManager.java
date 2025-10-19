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
        // Phase 2: project node positions to screen using camera basis + FOV; pick nearest in 2D
        if (screenW <= 0 || screenH <= 0 || camera == null) return null;
        Vec3d camPos = camera.getPos();
        var rot = camera.getRotation();
        org.joml.Vector3f rV = new org.joml.Vector3f(1f, 0f, 0f).rotate(rot);
        org.joml.Vector3f uV = new org.joml.Vector3f(0f, 1f, 0f).rotate(rot);
        org.joml.Vector3f fV = new org.joml.Vector3f(0f, 0f, -1f).rotate(rot);
        Vec3d right = new Vec3d(rV.x, rV.y, rV.z);
        Vec3d up = new Vec3d(uV.x, uV.y, uV.z);
        Vec3d forward = new Vec3d(fV.x, fV.y, fV.z);

        int baseFov = net.minecraft.client.MinecraftClient.getInstance().options.getFov().getValue();
        float fovMul = ((ninja.trek.mixin.client.GameRendererFovAccessor) net.minecraft.client.MinecraftClient.getInstance().gameRenderer).getFovMultiplier();
        double fovY = Math.toRadians(Math.max(1.0, baseFov * fovMul));
        double aspect = (double)screenW / (double)screenH;
        double tanHalfY = Math.tan(fovY * 0.5);
        double tanHalfX = tanHalfY * aspect;

        double best = Double.MAX_VALUE;
        CameraNode bestNode = null;
        for (var n : nodes) {
            Vec3d v = n.position.subtract(camPos);
            double xCam = v.dotProduct(right);
            double yCam = v.dotProduct(up);
            double zCam = v.dotProduct(forward);
            if (zCam <= 0.0) continue; // behind camera
            double nx = xCam / (zCam * tanHalfX);
            double ny = yCam / (zCam * tanHalfY);
            double sx = (nx + 1.0) * 0.5 * screenW;
            double sy = (1.0 - (ny + 1.0) * 0.5) * screenH;
            double dx = sx - mouseX;
            double dy = sy - mouseY;
            double d2 = dx*dx + dy*dy;
            if (d2 < best) { best = d2; bestNode = n; }
        }
        if (bestNode != null) selectedNodeId = bestNode.id;
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
                if (!isAreaEligibleForPlayer(area)) continue;

                double t = 0.0;
                if (area.shape == AreaShape.SPHERE) {
                    t = influenceForSphereArea(playerPos, area);
                } else {
                    t = influenceForBoxArea(playerPos, area);
                }
                // apply easing curve per-area
                t = (area.easing != null ? area.easing.apply(t) : t);
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

    private boolean isAreaEligibleForPlayer(Area area) {
        var mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return true;
        var pl = mc.player;
        boolean isElytra = pl.isFallFlying();
        boolean isSwimming = pl.isSwimming();
        boolean isSneaking = pl.isSneaking();
        var vehicle = pl.getVehicle();
        boolean isBoat = vehicle instanceof net.minecraft.entity.vehicle.BoatEntity;
        boolean isMinecart = vehicle instanceof net.minecraft.entity.vehicle.AbstractMinecartEntity;
        boolean isRidingGhast = vehicle instanceof net.minecraft.entity.mob.GhastEntity;
        boolean isRidingOther = vehicle != null && !isBoat && !isMinecart && !isRidingGhast;
        // crawling: player in swimming pose but not actually swimming in fluid
        boolean isCrawling1Block = pl.isInSwimmingPose() && !pl.isTouchingWater() && !isSwimming;
        boolean isWalking = !isElytra && !isSwimming && vehicle == null; // includes sprint/sneak; handled by sneaking flag separately

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

    private double influenceForSphereArea(Vec3d pos, Area area) {
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
            // Linear interpolation between surfaces in normalized radius space
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

    private double influenceForBoxArea(Vec3d pos, Area area) {
        Vec3d d = pos.subtract(area.center);
        if (area.advanced && area.minRadii != null && area.maxRadii != null) {
            double ax = Math.abs(d.x), ay = Math.abs(d.y), az = Math.abs(d.z);
            // Inside inner box => full weight
            if (ax <= area.minRadii.x && ay <= area.minRadii.y && az <= area.minRadii.z) return 1.0;
            // Outside outer box => zero
            if (ax >= area.maxRadii.x || ay >= area.maxRadii.y || az >= area.maxRadii.z) return 0.0;
            // Between: compute normalized expansion beyond inner toward outer per axis
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
            if (dist <= 0.0) return 1.0; // inside outer cube, approximate mapping
            // fall back to scalar mapping relative to inner/outer
            double inner = cubeDistance(pos, area.center, area.minRadius);
            if (inner <= 0.0) return 1.0;
            // approximate fraction
            double denom = (inner);
            double t = 1.0 - Math.min(1.0, dist / Math.max(1e-6, denom));
            return MathHelper.clamp(t, 0.0, 1.0);
        }
    }
}
