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

        // PHASE 1: Calculate position influences and weights
        double totalWeight = 0.0;
        Vec3d accumPos = Vec3d.ZERO;
        List<NodeInfluence> nodeInfluences = new ArrayList<>();

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

            // Store node influence for later rotation calculation
            nodeInfluences.add(new NodeInfluence(node, w));
        }

        if (totalWeight <= 1e-6) return base;

        // Normalize if needed
        if (totalWeight > 1.0) {
            accumPos = accumPos.multiply(1.0 / totalWeight);
            totalWeight = 1.0;
        }

        // Calculate final blended position
        Vec3d blendedPos = base.getPosition().multiply(1.0 - totalWeight).add(accumPos);

        // PHASE 2: Calculate rotation influences from the FINAL camera position
        float yawBase = base.getYaw();
        float pitchBase = base.getPitch();
        float accumYaw = 0f;
        float accumPitch = 0f;
        boolean anyLookAt = false;

        for (var influence : nodeInfluences) {
            if (influence.node.lookAt == null) continue;

            // Calculate direction from FINAL blended position to LookAt point
            Vec3d dir = influence.node.lookAt.subtract(blendedPos).normalize();
            float nYaw = (float)(Math.toDegrees(Math.atan2(dir.x, dir.z)));
            float nPitch = (float)(-Math.toDegrees(Math.asin(MathHelper.clamp(dir.y, -1.0, 1.0))));

            // Blend angles by weight, accounting for angle wrapping
            accumYaw += wrapAngleDelta(yawBase, nYaw) * influence.weight;
            accumPitch += nPitch * influence.weight;
            anyLookAt = true;
        }

        float outYaw = yawBase;
        float outPitch = pitchBase;
        if (anyLookAt) {
            // Normalize accumulated angles if totalWeight > 1.0
            if (totalWeight > 1.0) {
                accumYaw /= totalWeight;
                accumPitch /= totalWeight;
            }

            // Apply weighted rotation from base angles
            outYaw = normalizeAngle(yawBase + accumYaw * (float)totalWeight);
            outPitch = pitchBase * (1.0f - (float)totalWeight) + accumPitch * (float)totalWeight;
            outPitch = MathHelper.clamp(outPitch, -90f, 90f);
        }

        return new CameraTarget(blendedPos, outYaw, outPitch, base.getFovMultiplier(), base.getOrthoFactor());
    }

    // Helper class to store node influence data
    private static class NodeInfluence {
        final CameraNode node;
        final double weight;

        NodeInfluence(CameraNode node, double weight) {
            this.node = node;
            this.weight = weight;
        }
    }

    /**
     * Calculate the total influence weight from all nodes at the given position.
     * Returns 0.0 if no influence, up to 1.0+ if multiple nodes overlap.
     * This is normalized to max 1.0 in applyInfluence(), but raw total is useful for detection.
     */
    public double getTotalInfluence(Vec3d position) {
        if (nodes.isEmpty() || position == null) return 0.0;

        double totalWeight = 0.0;

        for (var node : nodes) {
            double w = 0.0;
            for (var area : node.areas) {
                if (!isAreaEligibleForPlayer(area)) continue;

                double t = 0.0;
                if (area.shape == AreaShape.SPHERE) {
                    t = influenceForSphereArea(position, area);
                } else {
                    t = influenceForBoxArea(position, area);
                }
                // apply easing curve per-area
                t = (area.easing != null ? area.easing.apply(t) : t);
                w = Math.max(w, MathHelper.clamp(t, 0.0, 1.0));
            }
            totalWeight += w;
        }

        return totalWeight;
    }

    // Normalize angle to [-180, 180] range
    private static float normalizeAngle(float angle) {
        angle = angle % 360f;
        if (angle > 180f) angle -= 360f;
        if (angle < -180f) angle += 360f;
        return angle;
    }

    // Calculate shortest angular delta from 'from' to 'to', accounting for wrapping
    private static float wrapAngleDelta(float from, float to) {
        float delta = to - from;
        delta = delta % 360f;
        if (delta > 180f) delta -= 360f;
        if (delta < -180f) delta += 360f;
        return delta;
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
        if (area.advanced && area.insideRadii != null && area.outsideRadii != null) {
            // insideRadii = inner ellipsoid (100% influence), outsideRadii = outer ellipsoid (0% influence)
            // Normalized distance at inside ellipsoid surface
            double distAtInside = Math.sqrt(
                    (d.x * d.x) / (area.insideRadii.x * area.insideRadii.x)
                            + (d.y * d.y) / (area.insideRadii.y * area.insideRadii.y)
                            + (d.z * d.z) / (area.insideRadii.z * area.insideRadii.z));
            // Normalized distance at outside ellipsoid surface
            double distAtOutside = Math.sqrt(
                    (d.x * d.x) / (area.outsideRadii.x * area.outsideRadii.x)
                            + (d.y * d.y) / (area.outsideRadii.y * area.outsideRadii.y)
                            + (d.z * d.z) / (area.outsideRadii.z * area.outsideRadii.z));

            // Inside inner ellipsoid => 100% influence
            if (distAtInside <= 1.0) return 1.0;
            // Outside outer ellipsoid => 0% influence
            if (distAtOutside >= 1.0) return 0.0;
            // Between: linear interpolation
            // distAtInside = 1.0 means on inner surface, distAtOutside = 1.0 means on outer surface
            double t = (distAtOutside - 1.0) / (distAtOutside - distAtInside);
            return MathHelper.clamp(t, 0.0, 1.0);
        } else {
            double dist = pos.distanceTo(area.center);
            // Inside insideRadius => 100% influence
            if (dist <= area.insideRadius) return 1.0;
            // Outside outsideRadius => 0% influence
            if (dist >= area.outsideRadius) return 0.0;
            // Between: linear interpolation from 100% to 0%
            return 1.0 - ((dist - area.insideRadius) / (area.outsideRadius - area.insideRadius));
        }
    }

    private double influenceForBoxArea(Vec3d pos, Area area) {
        Vec3d d = pos.subtract(area.center);
        if (area.advanced && area.insideRadii != null && area.outsideRadii != null) {
            double ax = Math.abs(d.x), ay = Math.abs(d.y), az = Math.abs(d.z);
            // Inside inner box => full weight
            if (ax <= area.insideRadii.x && ay <= area.insideRadii.y && az <= area.insideRadii.z) return 1.0;
            // Outside outer box => zero
            if (ax >= area.outsideRadii.x || ay >= area.outsideRadii.y || az >= area.outsideRadii.z) return 0.0;
            // Between: compute normalized expansion beyond inner toward outer per axis
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
            if (dist <= 0.0) return 1.0; // inside outer cube, approximate mapping
            // fall back to scalar mapping relative to inner/outer
            double inner = cubeDistance(pos, area.center, area.insideRadius);
            if (inner <= 0.0) return 1.0;
            // approximate fraction
            double denom = (inner);
            double t = 1.0 - Math.min(1.0, dist / Math.max(1e-6, denom));
            return MathHelper.clamp(t, 0.0, 1.0);
        }
    }

    // ========== Server Synchronization Methods ==========

    public void enterServerMode(boolean canEdit) {
        // TODO: Implement server mode - for now just log
        Craneshot.LOGGER.info("Entered server mode, canEdit={}", canEdit);
    }

    public void onDisconnected() {
        // TODO: Clean up server state
        Craneshot.LOGGER.info("Disconnected from server");
    }

    public void applyChunkSnapshot(net.minecraft.registry.RegistryKey<net.minecraft.world.World> dimension,
                                    net.minecraft.util.math.ChunkPos chunk,
                                    java.util.List<ninja.trek.nodes.model.CameraNodeDTO> nodeList) {
        // TODO: Apply chunk snapshot from server
        Craneshot.LOGGER.debug("Received chunk snapshot for {} at {},{} with {} nodes",
            dimension.getValue(), chunk.x, chunk.z, nodeList.size());
    }

    public void applyDeltaAdd(net.minecraft.registry.RegistryKey<net.minecraft.world.World> dimension,
                               net.minecraft.util.math.ChunkPos chunk,
                               ninja.trek.nodes.model.CameraNodeDTO node) {
        // TODO: Add node from server delta
        Craneshot.LOGGER.debug("Add node {} at chunk {},{}", node.uuid, chunk.x, chunk.z);
    }

    public void applyDeltaUpdate(net.minecraft.registry.RegistryKey<net.minecraft.world.World> dimension,
                                  net.minecraft.util.math.ChunkPos chunk,
                                  ninja.trek.nodes.model.CameraNodeDTO node) {
        // TODO: Update node from server delta
        Craneshot.LOGGER.debug("Update node {} at chunk {},{}", node.uuid, chunk.x, chunk.z);
    }

    public void applyDeltaRemove(net.minecraft.registry.RegistryKey<net.minecraft.world.World> dimension,
                                  net.minecraft.util.math.ChunkPos chunk,
                                  UUID nodeId) {
        // TODO: Remove node from server delta
        Craneshot.LOGGER.debug("Remove node {} at chunk {},{}", nodeId, chunk.x, chunk.z);
    }

    public void handleChunkUnload(net.minecraft.registry.RegistryKey<net.minecraft.world.World> dimension,
                                   net.minecraft.util.math.ChunkPos chunk) {
        // TODO: Handle chunk unload
        Craneshot.LOGGER.debug("Chunk unloaded at {},{}", chunk.x, chunk.z);
    }
}
