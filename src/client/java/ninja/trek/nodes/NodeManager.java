package ninja.trek.nodes;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import ninja.trek.Craneshot;
import ninja.trek.cameramovements.CameraTarget;
import ninja.trek.cameramovements.movements.StaticMovement;
import ninja.trek.nodes.io.NodeStorage;
import ninja.trek.nodes.model.*;
import ninja.trek.nodes.network.ClientNodeNetworking;

import java.util.*;

public class NodeManager {
    private static final NodeManager INSTANCE = new NodeManager();
    public static NodeManager get() { return INSTANCE; }

    private final List<CameraNode> nodes = new ArrayList<>();
    private final List<AreaInstance> areas = new ArrayList<>();
    private UUID selectedNodeId = null;
    private UUID selectedAreaId = null;
    private boolean editing = false;
    private final Map<UUID, Boolean> movementStateFilterCache = new HashMap<>();
    private boolean serverMode = false;
    private boolean serverCanEdit = false;
    private final Map<UUID, ChunkPos> nodeChunkIndex = new HashMap<>();
    private final Map<UUID, CameraNode> nodeLookup = new HashMap<>();
    private final Map<UUID, AreaInstance> areaLookup = new HashMap<>();
    private final Set<UUID> pendingAreaUpdates = new HashSet<>();

    public enum PlayerStateKey {
        WALKING("walking", "Walking"),
        SNEAKING("sneaking", "Sneaking"),
        ELYTRA("elytra", "Elytra"),
        SWIMMING("swimming", "Swimming"),
        BOAT("boat", "Boat"),
        MINECART("minecart", "Minecart"),
        RIDING_GHAST("riding_ghast", "Riding Ghast"),
        RIDING_OTHER("riding_other", "Riding Other"),
        CRAWLING_1_BLOCK("crawling_1_block", "Crawling (1-block)");

        private final String id;
        private final String label;

        PlayerStateKey(String id, String label) {
            this.id = id;
            this.label = label;
        }

        public String id() { return id; }
        public String label() { return label; }

        public static PlayerStateKey fromId(String id) {
            if (id == null || id.isEmpty()) return null;
            for (PlayerStateKey key : values()) {
                if (key.id.equals(id)) return key;
            }
            return null;
        }
    }

    private static final List<PlayerStateKey> CANONICAL_STATE_KEYS = List.of(
            PlayerStateKey.WALKING,
            PlayerStateKey.SNEAKING,
            PlayerStateKey.ELYTRA,
            PlayerStateKey.SWIMMING,
            PlayerStateKey.BOAT,
            PlayerStateKey.MINECART,
            PlayerStateKey.RIDING_GHAST,
            PlayerStateKey.RIDING_OTHER,
            PlayerStateKey.CRAWLING_1_BLOCK
    );

    public static List<PlayerStateKey> getCanonicalStateKeys() {
        return CANONICAL_STATE_KEYS;
    }

    // Edit-mode rotation override (used when a Screen is open)
    private boolean hasEditRotation = false;
    private float editYaw = 0f;
    private float editPitch = 0f;

    public void load() {
        nodes.clear();
        areas.clear();
        nodeLookup.clear();
        nodeChunkIndex.clear();
        areaLookup.clear();
        pendingAreaUpdates.clear();
        NodeStorage.Payload payload = NodeStorage.load();
        nodes.addAll(payload.nodes);
        areas.addAll(payload.areas);
        movementStateFilterCache.clear();
        selectedNodeId = null;
        selectedAreaId = null;
    }

    public void save() {
        if (serverMode) {
            flushServerSaves();
            return;
        }
        purgeMovementStateCache();
        NodeStorage.save(nodes, areas);
    }

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
    public List<AreaInstance> getAreas() { return Collections.unmodifiableList(areas); }

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

    public void setSelected(UUID id) {
        this.selectedNodeId = id;
        if (id != null) {
            this.selectedAreaId = null;
        }
    }
    public CameraNode getSelected() {
        if (selectedNodeId == null) return null;
        for (var n : nodes) if (n.id.equals(selectedNodeId)) return n;
        return null;
    }

    public void setSelectedArea(UUID id) {
        this.selectedAreaId = id;
        if (id != null) {
            this.selectedNodeId = null;
        }
    }
    public AreaInstance getSelectedArea() {
        if (selectedAreaId == null) return null;
        for (var a : areas) if (a.id.equals(selectedAreaId)) return a;
        return null;
    }

    public AreaInstance addArea(Vec3d center) {
        AreaInstance area = new AreaInstance();
        area.center = center;
        area.name = "Area " + (areas.size() + 1);
        area.owner = getCurrentPlayerId();
        CameraNode selected = getSelected();
        if (selected != null) {
            AreaMovementConfig cfg = new AreaMovementConfig();
            cfg.movementType = StaticMovement.MOVEMENT_ID;
            cfg.settings.put("positionNodeId", selected.id.toString());
            area.movements.add(cfg);
        }
        areas.add(area);
        if (serverMode) {
            areaLookup.put(area.id, area);
            if (serverCanEdit) {
                RegistryKey<World> dimension = getCurrentDimension();
                if (dimension != null) {
                    AreaInstanceDTO dto = AreaInstanceDTO.fromAreaInstance(area);
                    dto.clientRequestId = area.id;
                    ClientNodeNetworking.sendAreaCreate(dimension, dto);
                } else {
                    Craneshot.LOGGER.debug("Skipping area create: unknown dimension");
                }
            } else {
                Craneshot.LOGGER.debug("Skipping area create: client lacks edit permission");
            }
            return area;
        }
        save();
        return area;
    }

    public void removeArea(UUID areaId) {
        if (areaId == null) return;
        if (serverMode) {
            if (!serverCanEdit) {
                Craneshot.LOGGER.debug("Skipping area removal: client lacks edit permission");
                return;
            }
            AreaInstance removed = areaLookup.remove(areaId);
            if (removed == null) {
                Iterator<AreaInstance> iterator = areas.iterator();
                while (iterator.hasNext()) {
                    AreaInstance candidate = iterator.next();
                    if (candidate.id.equals(areaId)) {
                        removed = candidate;
                        iterator.remove();
                        break;
                    }
                }
            } else {
                areas.remove(removed);
            }
            if (removed != null) {
                clearMovementCacheForArea(removed);
            }
            pendingAreaUpdates.remove(areaId);
            if (areaId.equals(selectedAreaId)) selectedAreaId = null;
            RegistryKey<World> dimension = getCurrentDimension();
            if (dimension != null) {
                ClientNodeNetworking.sendAreaDelete(dimension, areaId);
            }
            return;
        }
        AreaInstance removed = null;
        Iterator<AreaInstance> iterator = areas.iterator();
        while (iterator.hasNext()) {
            AreaInstance candidate = iterator.next();
            if (candidate.id.equals(areaId)) {
                removed = candidate;
                iterator.remove();
                break;
            }
        }
        if (removed != null) {
            clearMovementCacheForArea(removed);
        }
        areaLookup.remove(areaId);
        if (areaId.equals(selectedAreaId)) selectedAreaId = null;
        save();
    }

    public void markAreaDirty(UUID areaId) {
        if (!serverMode || areaId == null) return;
        pendingAreaUpdates.add(areaId);
    }

    public AreaInstance getArea(UUID id) {
        if (id == null) return null;
        for (var area : areas) if (area.id.equals(id)) return area;
        return null;
    }

    public CameraNode getNode(UUID id) {
        if (id == null) return null;
        for (var node : nodes) if (node.id.equals(id)) return node;
        return null;
    }

    public void replaceAll(List<CameraNode> newNodes, List<AreaInstance> newAreas) {
        nodes.clear();
        areas.clear();
        if (newNodes != null) nodes.addAll(newNodes);
        if (newAreas != null) areas.addAll(newAreas);
        selectedNodeId = null;
        selectedAreaId = null;
        movementStateFilterCache.clear();
        save();
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

    // Influence computation
    public CameraTarget applyInfluence(CameraTarget base, boolean skipInfluence) {
        if (skipInfluence || areas.isEmpty() || base == null) return base;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return base;
        PlayerStateSnapshot stateSnapshot = collectPlayerStates(mc);
        Vec3d playerPos = mc.player.getEyePos();

        double totalWeight = 0.0;
        Vec3d accumPos = Vec3d.ZERO;
        float yawBase = base.getYaw();
        float pitchBase = base.getPitch();
        float baseFov = base.getFovMultiplier();
        double accumYawDelta = 0.0;
        double accumPitch = 0.0;
        double accumFov = 0.0;
        boolean anyOrientation = false;
        boolean anyFov = false;

        for (var area : areas) {
            if (!areaMatchesPlayerStates(area, stateSnapshot)) continue;

            double rawInfluence = area.shape == AreaShape.SPHERE
                    ? influenceForSphereArea(playerPos, area)
                    : influenceForBoxArea(playerPos, area);
            double easedInfluence = area.easing != null ? area.easing.apply(rawInfluence) : rawInfluence;
            double areaWeight = MathHelper.clamp(easedInfluence, 0.0, 1.0);
            if (areaWeight <= 1e-6) continue;

            boolean producedMovement = false;
            for (AreaMovementConfig config : area.movements) {
                if (config == null || !config.enabled) continue;
                if (!passesStateFilters(config, stateSnapshot)) continue;
                double movementWeight = MathHelper.clamp(config.weight, 0.0f, 1.0f);
                if (movementWeight <= 1e-6) continue;

                CameraTarget target = resolveMovementConfig(area, config, base);
                if (target == null) continue;

                double finalWeight = areaWeight * movementWeight;
                accumPos = accumPos.add(target.getPosition().multiply(finalWeight));
                totalWeight += finalWeight;

                accumYawDelta += wrapAngleDelta(yawBase, target.getYaw()) * finalWeight;
                accumPitch += target.getPitch() * finalWeight;
                accumFov += target.getFovMultiplier() * finalWeight;
                anyOrientation = true;
                anyFov = true;
                producedMovement = true;
            }

            if (!producedMovement) {
                accumPos = accumPos.add(area.center.multiply(areaWeight));
                totalWeight += areaWeight;
            }
        }

        if (totalWeight <= 1e-6) return base;

        if (totalWeight > 1.0) {
            double inv = 1.0 / totalWeight;
            accumPos = accumPos.multiply(inv);
            accumYawDelta *= inv;
            accumPitch *= inv;
            accumFov *= inv;
            totalWeight = 1.0;
        }

        Vec3d blendedPos = base.getPosition().multiply(1.0 - totalWeight).add(accumPos);

        float outYaw = yawBase;
        float outPitch = pitchBase;
        if (anyOrientation) {
            outYaw = normalizeAngle(yawBase + (float)(accumYawDelta * totalWeight));
            outPitch = pitchBase * (1.0f - (float)totalWeight) + (float)(accumPitch * totalWeight);
            outPitch = MathHelper.clamp(outPitch, -90f, 90f);
        }

        float outFov = baseFov;
        if (anyFov) {
            outFov = baseFov * (1.0f - (float)totalWeight) + (float)(accumFov * totalWeight);
        }

        return new CameraTarget(blendedPos, outYaw, outPitch, outFov, base.getOrthoFactor());
    }

    private boolean passesStateFilters(AreaMovementConfig config, PlayerStateSnapshot snapshot) {
        List<String> filters = config.stateFilters;
        if (filters == null || filters.isEmpty()) {
            if (config.id != null) {
                movementStateFilterCache.remove(config.id);
            }
            return true;
        }

        boolean matched = snapshot != null && snapshot.matchesAny(filters);
        recordStateFilterResult(config, filters, snapshot, matched);
        return matched;
    }

    private CameraTarget resolveMovementConfig(AreaInstance area, AreaMovementConfig config, CameraTarget base) {
        String type = config.movementType;
        if (type == null || type.isBlank()) return null;
        if (StaticMovement.MOVEMENT_ID.equals(type) || StaticMovement.class.getName().equals(type)) {
            return StaticMovement.resolveTarget(this, config, base);
        }
        return null;
    }

    /**
     * Calculate the total influence weight from all areas at the given position.
     * Returns 0.0 if no influence, up to 1.0+ if multiple areas overlap.
     * This is normalized to max 1.0 in applyInfluence(), but raw total is useful for detection.
     */
    public double getTotalInfluence(Vec3d position) {
        if (areas.isEmpty() || position == null) return 0.0;

        double totalWeight = 0.0;
        PlayerStateSnapshot snapshot = collectPlayerStates();
        for (var area : areas) {
            if (!areaMatchesPlayerStates(area, snapshot)) continue;
            double t = area.shape == AreaShape.SPHERE
                    ? influenceForSphereArea(position, area)
                    : influenceForBoxArea(position, area);
            t = area.easing != null ? area.easing.apply(t) : t;
            totalWeight += MathHelper.clamp(t, 0.0, 1.0);
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

    public PlayerStateSnapshot collectPlayerStates() {
        return collectPlayerStates(MinecraftClient.getInstance());
    }

    public PlayerStateSnapshot collectPlayerStates(MinecraftClient mc) {
        if (mc == null || mc.player == null) return PlayerStateSnapshot.noPlayer();
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

        EnumSet<PlayerStateKey> activeStates = EnumSet.noneOf(PlayerStateKey.class);
        boolean walkingState = false;
        boolean sneakingState = false;

        if (isWalking && !isSneaking) {
            walkingState = true;
            activeStates.add(PlayerStateKey.WALKING);
        }
        if (isWalking && isSneaking) {
            sneakingState = true;
            activeStates.add(PlayerStateKey.SNEAKING);
        }
        if (isElytra) activeStates.add(PlayerStateKey.ELYTRA);
        if (isSwimming) activeStates.add(PlayerStateKey.SWIMMING);
        if (isBoat) activeStates.add(PlayerStateKey.BOAT);
        if (isMinecart) activeStates.add(PlayerStateKey.MINECART);
        if (isRidingGhast) activeStates.add(PlayerStateKey.RIDING_GHAST);
        if (isRidingOther) activeStates.add(PlayerStateKey.RIDING_OTHER);
        if (isCrawling1Block) activeStates.add(PlayerStateKey.CRAWLING_1_BLOCK);

        return new PlayerStateSnapshot(
                true,
                walkingState,
                sneakingState,
                isSwimming,
                isElytra,
                isBoat,
                isMinecart,
                isRidingGhast,
                isRidingOther,
                isCrawling1Block,
                activeStates
        );
    }

    public static boolean areaMatchesPlayerStates(Area area, PlayerStateSnapshot snapshot) {
        if (snapshot == null || !snapshot.hasPlayer()) return true;
        boolean ok = false;
        if (area.filterWalking && snapshot.isWalking()) ok = true;
        if (area.filterSneaking && snapshot.isSneaking()) ok = true;
        if (area.filterElytra && snapshot.isElytra()) ok = true;
        if (area.filterBoat && snapshot.isBoat()) ok = true;
        if (area.filterMinecart && snapshot.isMinecart()) ok = true;
        if (area.filterRidingGhast && snapshot.isRidingGhast()) ok = true;
        if (area.filterRidingOther && snapshot.isRidingOther()) ok = true;
        if (area.filterSwimming && snapshot.isSwimming()) ok = true;
        if (area.filterCrawling1Block && snapshot.isCrawling1Block()) ok = true;
        return ok;
    }

    private void recordStateFilterResult(AreaMovementConfig config, List<String> filters, PlayerStateSnapshot snapshot, boolean matched) {
        if (config == null || config.id == null) return;
        Boolean previous = movementStateFilterCache.put(config.id, matched);
        if (previous == null || previous != matched) {
            String label = config.name != null && !config.name.isBlank() ? config.name : config.movementType;
            List<String> active = snapshot != null ? snapshot.getActiveStateIds() : List.of();
            Craneshot.LOGGER.debug("Area movement {} ({}) {} by state filters {} active={}",
                    config.id,
                    label,
                    matched ? "allowed" : "suppressed",
                    filters,
                    active);
        }
    }

    private void purgeMovementStateCache() {
        if (movementStateFilterCache.isEmpty()) return;
        Set<UUID> valid = new HashSet<>();
        for (AreaInstance area : areas) {
            if (area == null) continue;
            for (AreaMovementConfig cfg : area.movements) {
                if (cfg != null && cfg.id != null) {
                    valid.add(cfg.id);
                }
            }
        }
        movementStateFilterCache.keySet().retainAll(valid);
    }

    public static final class PlayerStateSnapshot {
        private static final PlayerStateSnapshot NO_PLAYER = new PlayerStateSnapshot(
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                EnumSet.noneOf(PlayerStateKey.class)
        );

        private final boolean hasPlayer;
        private final boolean walking;
        private final boolean sneaking;
        private final boolean swimming;
        private final boolean elytra;
        private final boolean boat;
        private final boolean minecart;
        private final boolean ridingGhast;
        private final boolean ridingOther;
        private final boolean crawling1Block;
        private final EnumSet<PlayerStateKey> activeStates;

        private PlayerStateSnapshot(boolean hasPlayer,
                                    boolean walking,
                                    boolean sneaking,
                                    boolean swimming,
                                    boolean elytra,
                                    boolean boat,
                                    boolean minecart,
                                    boolean ridingGhast,
                                    boolean ridingOther,
                                    boolean crawling1Block,
                                    EnumSet<PlayerStateKey> activeStates) {
            this.hasPlayer = hasPlayer;
            this.walking = walking;
            this.sneaking = sneaking;
            this.swimming = swimming;
            this.elytra = elytra;
            this.boat = boat;
            this.minecart = minecart;
            this.ridingGhast = ridingGhast;
            this.ridingOther = ridingOther;
            this.crawling1Block = crawling1Block;
            this.activeStates = activeStates;
        }

        public static PlayerStateSnapshot noPlayer() {
            return NO_PLAYER;
        }

        public boolean hasPlayer() { return hasPlayer; }
        public boolean isWalking() { return walking; }
        public boolean isSneaking() { return sneaking; }
        public boolean isSwimming() { return swimming; }
        public boolean isElytra() { return elytra; }
        public boolean isBoat() { return boat; }
        public boolean isMinecart() { return minecart; }
        public boolean isRidingGhast() { return ridingGhast; }
        public boolean isRidingOther() { return ridingOther; }
        public boolean isCrawling1Block() { return crawling1Block; }

        public boolean matchesAny(Collection<String> requested) {
            if (!hasPlayer || requested == null || requested.isEmpty()) return true;
            for (String id : requested) {
                PlayerStateKey key = PlayerStateKey.fromId(id);
                if (key != null && activeStates.contains(key)) {
                    return true;
                }
            }
            return false;
        }

        public List<String> getActiveStateIds() {
            if (activeStates.isEmpty()) return List.of();
            List<String> ids = new ArrayList<>(activeStates.size());
            for (PlayerStateKey key : activeStates) {
                ids.add(key.id());
            }
            return ids;
        }
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
        serverMode = true;
        serverCanEdit = canEdit;
        nodes.clear();
        areas.clear();
        nodeChunkIndex.clear();
        nodeLookup.clear();
        areaLookup.clear();
        pendingAreaUpdates.clear();
        movementStateFilterCache.clear();
        selectedNodeId = null;
        selectedAreaId = null;
        Craneshot.LOGGER.info("Entered server mode, canEdit={}", canEdit);
    }

    public void onDisconnected() {
        if (!serverMode) return;
        serverMode = false;
        serverCanEdit = false;
        nodeChunkIndex.clear();
        nodeLookup.clear();
        areaLookup.clear();
        pendingAreaUpdates.clear();
        movementStateFilterCache.clear();
        load();
        Craneshot.LOGGER.info("Disconnected from server");
    }

    public void applyChunkSnapshot(RegistryKey<World> dimension,
                                    ChunkPos chunk,
                                    List<CameraNodeDTO> nodeList) {
        if (!serverMode || !dimensionMatchesCurrent(dimension)) return;
        removeNodesInChunk(chunk);
        for (CameraNodeDTO dto : nodeList) {
            CameraNode node = dto.toCameraNode();
            nodeLookup.put(node.id, node);
            nodeChunkIndex.put(node.id, chunk);
            upsertNode(node);
        }
    }

    public void applyDeltaAdd(RegistryKey<World> dimension,
                               ChunkPos chunk,
                               CameraNodeDTO dto) {
        if (!serverMode || !dimensionMatchesCurrent(dimension)) return;
        CameraNode node = dto.toCameraNode();
        if (dto.clientRequestId != null) {
            CameraNode pending = nodeLookup.remove(dto.clientRequestId);
            if (pending != null) {
                replaceNode(pending.id, node);
                nodeChunkIndex.remove(dto.clientRequestId);
            } else {
                upsertNode(node);
            }
        } else {
            upsertNode(node);
        }
        nodeLookup.put(node.id, node);
        nodeChunkIndex.put(node.id, chunk);
    }

    public void applyDeltaUpdate(RegistryKey<World> dimension,
                                  ChunkPos chunk,
                                  CameraNodeDTO dto) {
        if (!serverMode || !dimensionMatchesCurrent(dimension)) return;
        CameraNode node = dto.toCameraNode();
        nodeLookup.put(node.id, node);
        nodeChunkIndex.put(node.id, chunk);
        replaceNode(node.id, node);
    }

    public void applyDeltaRemove(RegistryKey<World> dimension,
                                  ChunkPos chunk,
                                  UUID nodeId) {
        if (!serverMode || nodeId == null || !dimensionMatchesCurrent(dimension)) return;
        nodeLookup.remove(nodeId);
        nodeChunkIndex.remove(nodeId);
        removeNode(nodeId);
    }

    public void handleChunkUnload(RegistryKey<World> dimension,
                                   ChunkPos chunk) {
        if (!serverMode || !dimensionMatchesCurrent(dimension)) return;
        removeNodesInChunk(chunk);
    }

    public void applyAreasSnapshot(RegistryKey<World> dimension, List<AreaInstanceDTO> areaList) {
        if (!serverMode || !dimensionMatchesCurrent(dimension)) return;
        UUID previousSelected = selectedAreaId;
        areas.clear();
        areaLookup.clear();
        for (AreaInstanceDTO dto : areaList) {
            AreaInstance area = dto.toAreaInstance();
            areas.add(area);
            areaLookup.put(area.id, area);
        }
        if (previousSelected != null && areaLookup.containsKey(previousSelected)) {
            selectedAreaId = previousSelected;
        } else {
            selectedAreaId = null;
        }
        pendingAreaUpdates.clear();
        movementStateFilterCache.clear();
    }

    public void applyAreaDeltaAdd(RegistryKey<World> dimension, AreaInstanceDTO dto) {
        if (!serverMode || !dimensionMatchesCurrent(dimension)) return;
        AreaInstance area = dto.toAreaInstance();
        if (dto.clientRequestId != null) {
            AreaInstance pending = areaLookup.remove(dto.clientRequestId);
            if (pending != null) {
                replaceArea(dto.clientRequestId, area);
                pendingAreaUpdates.remove(dto.clientRequestId);
                return;
            }
        }
        addAreaInstance(area);
    }

    public void applyAreaDeltaUpdate(RegistryKey<World> dimension, AreaInstanceDTO dto) {
        if (!serverMode || !dimensionMatchesCurrent(dimension)) return;
        AreaInstance updated = dto.toAreaInstance();
        AreaInstance existing = areaLookup.get(updated.id);
        if (existing != null) {
            clearMovementCacheForArea(existing);
            copyAreaData(existing, updated);
            areaLookup.put(existing.id, existing);
        } else {
            addAreaInstance(updated);
        }
    }

    public void applyAreaDeltaRemove(RegistryKey<World> dimension, UUID areaId) {
        if (!serverMode || areaId == null || !dimensionMatchesCurrent(dimension)) return;
        AreaInstance removed = areaLookup.remove(areaId);
        if (removed != null) {
            clearMovementCacheForArea(removed);
            areas.remove(removed);
        } else {
            areas.removeIf(a -> a.id.equals(areaId));
        }
        pendingAreaUpdates.remove(areaId);
        if (areaId.equals(selectedAreaId)) selectedAreaId = null;
    }

    private void flushServerSaves() {
        if (!serverCanEdit) {
            pendingAreaUpdates.clear();
            return;
        }
        RegistryKey<World> dimension = getCurrentDimension();
        if (dimension == null) return;
        if (!pendingAreaUpdates.isEmpty()) {
            List<UUID> ids = new ArrayList<>(pendingAreaUpdates);
            pendingAreaUpdates.clear();
            for (UUID id : ids) {
                AreaInstance area = areaLookup.get(id);
                if (area == null) continue;
                AreaInstanceDTO dto = AreaInstanceDTO.fromAreaInstance(area);
                ClientNodeNetworking.sendAreaUpdate(dimension, dto);
            }
        }
    }

    private RegistryKey<World> getCurrentDimension() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null) return null;
        return mc.world.getRegistryKey();
    }

    private UUID getCurrentPlayerId() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return null;
        return mc.player.getUuid();
    }

    private boolean dimensionMatchesCurrent(RegistryKey<World> dimension) {
        RegistryKey<World> current = getCurrentDimension();
        return current == null || current.equals(dimension);
    }

    private void upsertNode(CameraNode node) {
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).id.equals(node.id)) {
                nodes.set(i, node);
                return;
            }
        }
        nodes.add(node);
    }

    private void replaceNode(UUID nodeId, CameraNode replacement) {
        boolean replaced = false;
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).id.equals(nodeId)) {
                nodes.set(i, replacement);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            nodes.add(replacement);
        }
        if (selectedNodeId != null && selectedNodeId.equals(nodeId)) {
            selectedNodeId = replacement.id;
        }
    }

    private void removeNode(UUID nodeId) {
        nodes.removeIf(node -> node.id.equals(nodeId));
        if (selectedNodeId != null && selectedNodeId.equals(nodeId)) selectedNodeId = null;
    }

    private void removeNodesInChunk(ChunkPos chunk) {
        Iterator<CameraNode> iterator = nodes.iterator();
        while (iterator.hasNext()) {
            CameraNode node = iterator.next();
            ChunkPos stored = nodeChunkIndex.get(node.id);
            if (stored != null && stored.equals(chunk)) {
                iterator.remove();
                nodeLookup.remove(node.id);
                if (selectedNodeId != null && selectedNodeId.equals(node.id)) {
                    selectedNodeId = null;
                }
            }
        }
        nodeChunkIndex.entrySet().removeIf(entry -> entry.getValue().equals(chunk));
    }

    private void addAreaInstance(AreaInstance area) {
        areas.add(area);
        areaLookup.put(area.id, area);
    }

    private void replaceArea(UUID oldId, AreaInstance replacement) {
        boolean replaced = false;
        for (int i = 0; i < areas.size(); i++) {
            if (areas.get(i).id.equals(oldId)) {
                areas.set(i, replacement);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            areas.add(replacement);
        }
        areaLookup.put(replacement.id, replacement);
        if (selectedAreaId != null && selectedAreaId.equals(oldId)) {
            selectedAreaId = replacement.id;
        }
    }

    private void copyAreaData(AreaInstance target, AreaInstance source) {
        target.name = source.name;
        target.owner = source.owner;
        target.shape = source.shape;
        target.center = source.center;
        target.insideRadius = source.insideRadius;
        target.outsideRadius = source.outsideRadius;
        target.advanced = source.advanced;
        target.insideRadii = source.insideRadii;
        target.outsideRadii = source.outsideRadii;
        target.filterWalking = source.filterWalking;
        target.filterElytra = source.filterElytra;
        target.filterMinecart = source.filterMinecart;
        target.filterRidingGhast = source.filterRidingGhast;
        target.filterRidingOther = source.filterRidingOther;
        target.filterBoat = source.filterBoat;
        target.filterSwimming = source.filterSwimming;
        target.filterSneaking = source.filterSneaking;
        target.filterCrawling1Block = source.filterCrawling1Block;
        target.easing = source.easing;
        target.movements.clear();
        for (AreaMovementConfig cfg : source.movements) {
            target.movements.add(cfg.copy());
        }
    }

    private void clearMovementCacheForArea(AreaInstance area) {
        if (area == null) return;
        for (AreaMovementConfig cfg : area.movements) {
            if (cfg.id != null) {
                movementStateFilterCache.remove(cfg.id);
            }
        }
    }
}
