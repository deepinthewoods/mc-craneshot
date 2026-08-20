package ninja.trek.follower;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import ninja.trek.CameraController;
import ninja.trek.Craneshot;
import ninja.trek.CraneshotClient;
import ninja.trek.cameramovements.AbstractMovementSettings;
import ninja.trek.cameramovements.CameraTarget;
import ninja.trek.cameramovements.ICameraMovement;
import ninja.trek.cameramovements.MovementState;
import ninja.trek.cameramovements.RaycastType;
import ninja.trek.cameramovements.movements.LinearMovement;
import ninja.trek.config.FollowerConfig;
import ninja.trek.nodes.NodeManager;
import ninja.trek.nodes.model.CameraNode;
import ninja.trek.nodes.model.NodeType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Render-frame camera director used only by follower zero. Normal and speaking
 * shots are ordinary configurable Craneshot movements; timelapse shots replace
 * their output for exactly one call to {@link #calculateState}.
 */
public final class FollowerCameraDirector extends AbstractMovementSettings implements ICameraMovement {
    private static final String ANNOTATION_SENDER_KEY = "obsannotator:annotation-sender-v1";

    private final FollowerConfig.FollowerEntry config;
    private final ICameraMovement normalMovement;
    private final ICameraMovement speakingMovement;
    private final ArrayDeque<TimelapsePulse> pendingPulses = new ArrayDeque<>();
    private final Map<UUID, SmoothedFocus> trackedFocus = new HashMap<>();
    private final Map<UUID, String> observedBuildStates = new HashMap<>();

    private ICameraMovement activeMovement;
    private Predicate<UUID> speakingProvider;
    private Function<String, Boolean> annotationSender;
    private boolean speaking;
    private boolean rawSpeaking;
    private long rawSpeechChangedAtNanos;
    private long nextTimelapseAtNanos;
    private long renderSequence;
    private long markerSequence;
    private String lastCameraMarkerMode;
    private boolean resetting;

    public FollowerCameraDirector(FollowerConfig.FollowerEntry config) {
        this.config = Objects.requireNonNull(config, "config");
        this.normalMovement = movementOrDefault(config.getMovement());
        this.speakingMovement = movementOrDefault(config.getSpeakingMovement());
        this.activeMovement = normalMovement;
        setRaycastType(RaycastType.NONE);
    }

    private static ICameraMovement movementOrDefault(ICameraMovement movement) {
        return movement != null ? movement : new LinearMovement();
    }

    @Override
    public void start(Minecraft client, Camera camera) {
        activeMovement = normalMovement;
        speaking = false;
        rawSpeaking = false;
        rawSpeechChangedAtNanos = System.nanoTime();
        nextTimelapseAtNanos = 0L;
        renderSequence = 0L;
        markerSequence = 0L;
        lastCameraMarkerMode = null;
        pendingPulses.clear();
        trackedFocus.clear();
        observedBuildStates.clear();
        resetting = false;
        activeMovement.start(client, camera);
        applyMovementSettings(activeMovement);
    }

    @Override
    public MovementState calculateState(Minecraft client, Camera camera, float tickDelta, float deltaSeconds) {
        renderSequence++;
        observeBuildStates();

        long now = System.nanoTime();
        applyMovementSettings(activeMovement);
        MovementState baseState = activeMovement.calculateState(client, camera, tickDelta, deltaSeconds);
        if (baseState == null || baseState.getCameraTarget() == null) {
            baseState = new MovementState(CameraTarget.fromCamera(camera), false);
        }

        if (updateSpeechState(client, camera, now, baseState)) {
            applyMovementSettings(activeMovement);
        }

        if (resetting) {
            return new MovementState(baseState.getCameraTarget(), true);
        }

        if (config.isTimelapseEnabled() && pendingPulses.isEmpty() && now >= nextTimelapseAtNanos) {
            collectTimelapsePulses(client, tickDelta, now);
            nextTimelapseAtNanos = now + secondsToNanos(config.getTimelapseIntervalSeconds());
        }

        TimelapsePulse pulse = pendingPulses.pollFirst();
        if (pulse != null) {
            if (sendCameraMarker("timelapse", pulse.node())) {
                return new MovementState(pulse.target(), false);
            }
            pendingPulses.clear();
        }

        String baseMode = speaking ? "face" : "normal";
        if (!baseMode.equals(lastCameraMarkerMode)) {
            sendCameraMarker(baseMode, null);
        }
        return new MovementState(baseState.getCameraTarget(), false);
    }

    private boolean updateSpeechState(Minecraft client, Camera camera, long now, MovementState currentState) {
        boolean detected = false;
        if (config.isSpeechCameraEnabled()) {
            Player trackedPlayer = CameraController.getTrackedPlayer(client);
            Predicate<UUID> provider = getSpeakingProvider();
            if (trackedPlayer != null && provider != null) {
                try {
                    detected = provider.test(trackedPlayer.getUUID());
                } catch (RuntimeException exception) {
                    Craneshot.LOGGER.debug("Mouth Anim speaking provider failed", exception);
                }
            }
        }

        if (detected != rawSpeaking) {
            rawSpeaking = detected;
            rawSpeechChangedAtNanos = now;
        }

        long threshold = rawSpeaking
                ? millisecondsToNanos(config.getSpeechOnsetMs())
                : millisecondsToNanos(config.getSpeechReleaseMs());
        boolean desired = rawSpeaking;
        if (desired != speaking && now - rawSpeechChangedAtNanos >= threshold) {
            speaking = desired;
            activeMovement = speaking ? speakingMovement : normalMovement;
            if (activeMovement instanceof AbstractMovementSettings settings) {
                settings.startFromState(client, camera, currentState);
            } else {
                activeMovement.start(client, camera);
            }
            Craneshot.LOGGER.info("Follower speech camera switched to {} mode",
                    speaking ? "speaking" : "normal");
            return true;
        }
        return false;
    }

    private Predicate<UUID> getSpeakingProvider() {
        speakingProvider = MouthAnimSpeakingBridge.getProvider();
        return speakingProvider;
    }

    @SuppressWarnings("unchecked")
    private Function<String, Boolean> getAnnotationSender() {
        if (annotationSender == null) {
            Object sender = net.fabricmc.loader.api.FabricLoader.getInstance()
                    .getObjectShare().get(ANNOTATION_SENDER_KEY);
            if (sender instanceof Function<?, ?>) {
                annotationSender = (Function<String, Boolean>) sender;
            }
        }
        return annotationSender;
    }

    private void collectTimelapsePulses(Minecraft client, float tickDelta, long now) {
        Player trackedPlayer = CameraController.getTrackedPlayer(client);
        if (trackedPlayer == null || client.level == null) return;

        Vec3 playerPosition = trackedPlayer.getPosition(tickDelta);
        double maxDistance = config.getTimelapseDistanceChunks() * 16.0;
        double maxDistanceSquared = maxDistance * maxDistance;
        List<TimelapsePulse> candidates = new ArrayList<>();

        for (CameraNode node : NodeManager.get().getNodes()) {
            if (node.type != NodeType.TIMELAPSE || !node.timelapseEnabled
                    || node.timelapseIndex != config.getTimelapseIndex()) {
                continue;
            }
            CameraTarget target = resolveTimelapseTarget(client, node, tickDelta, now);
            if (target == null) continue;
            Vec3 rangePoint = getRangePoint(client, node, target, tickDelta);
            if (rangePoint.distanceToSqr(playerPosition) <= maxDistanceSquared) {
                candidates.add(new TimelapsePulse(node, target,
                        rangePoint.distanceToSqr(playerPosition)));
            }
        }

        candidates.sort(Comparator.comparingDouble(TimelapsePulse::distanceSquared)
                .thenComparing(pulse -> pulse.node().id));
        pendingPulses.addAll(candidates);
    }

    private CameraTarget resolveTimelapseTarget(Minecraft client, CameraNode node, float tickDelta, long now) {
        if (!node.autoManaged) {
            if (node.position == null) return null;
            return new CameraTarget(node.position, node.timelapseYaw, node.timelapsePitch,
                    node.timelapseFovMultiplier);
        }

        Vec3 focus;
        double requiredDistance = config.getTrackingDistance();
        if (node.framingMin != null && node.framingMax != null) {
            focus = node.framingMin.add(node.framingMax).scale(0.5);
            requiredDistance = Math.max(requiredDistance,
                    calculateFramingDistance(client, node, node.timelapseFovMultiplier));
        } else {
            Entity trackedEntity = findEntity(client, node.trackedEntityId);
            if (trackedEntity == null) return null;
            Vec3 rawFocus = trackedEntity.getBoundingBox().getCenter();
            focus = smoothFocus(node.id, rawFocus, now);
        }

        float elevation = config.getRigElevationDegrees();
        double elevationRadians = Math.toRadians(elevation);
        double horizontalDistance = Math.cos(elevationRadians) * requiredDistance;
        double verticalDistance = Math.sin(elevationRadians) * requiredDistance;
        double yawRadians = Math.toRadians(node.autoRigYaw);
        Vec3 cameraPosition = focus.add(
                Math.sin(yawRadians) * horizontalDistance,
                verticalDistance,
                -Math.cos(yawRadians) * horizontalDistance);
        return lookAt(cameraPosition, focus, node.timelapseFovMultiplier);
    }

    private Vec3 getRangePoint(Minecraft client, CameraNode node, CameraTarget target, float tickDelta) {
        if (node.framingMin != null && node.framingMax != null) {
            return node.framingMin.add(node.framingMax).scale(0.5);
        }
        if (node.autoManaged) {
            Entity entity = findEntity(client, node.trackedEntityId);
            if (entity != null) return entity.getPosition(tickDelta);
        }
        return node.position != null ? node.position : target.getPosition();
    }

    private double calculateFramingDistance(Minecraft client, CameraNode node, float fovMultiplier) {
        Vec3 extent = node.framingMax.subtract(node.framingMin).scale(0.5);
        double radius = Math.max(1.0, extent.length()) * 1.15;
        double aspect = client.getWindow().getHeight() > 0
                ? (double) client.getWindow().getWidth() / client.getWindow().getHeight()
                : 16.0 / 9.0;
        double verticalFov = Math.toRadians(Mth.clamp(
                client.options.fov().get() * Math.max(0.1f, fovMultiplier), 1.0, 179.0));
        double horizontalFov = 2.0 * Math.atan(Math.tan(verticalFov / 2.0) * aspect);
        double limitingHalfFov = Math.max(Math.toRadians(1.0),
                Math.min(verticalFov, horizontalFov) / 2.0);
        return radius / Math.sin(limitingHalfFov);
    }

    private Entity findEntity(Minecraft client, UUID id) {
        if (id == null || client.level == null) return null;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (id.equals(entity.getUUID()) && !entity.isRemoved()) {
                return entity;
            }
        }
        return null;
    }

    private Vec3 smoothFocus(UUID nodeId, Vec3 rawFocus, long now) {
        SmoothedFocus previous = trackedFocus.get(nodeId);
        if (previous == null) {
            trackedFocus.put(nodeId, new SmoothedFocus(rawFocus, now));
            return rawFocus;
        }
        double elapsedSeconds = Math.max(0.0, (now - previous.updatedAtNanos()) / 1_000_000_000.0);
        double alpha = 1.0 - Math.pow(0.5,
                elapsedSeconds / Math.max(0.1, config.getTrackingSmoothingSeconds()));
        Vec3 smoothed = previous.position().lerp(rawFocus, (float) Mth.clamp(alpha, 0.0, 1.0));
        trackedFocus.put(nodeId, new SmoothedFocus(smoothed, now));
        return smoothed;
    }

    private static CameraTarget lookAt(Vec3 position, Vec3 focus, float fovMultiplier) {
        Vec3 direction = focus.subtract(position).normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        float pitch = (float) -Math.toDegrees(Math.asin(Mth.clamp(direction.y, -1.0, 1.0)));
        return new CameraTarget(position, yaw, pitch, fovMultiplier);
    }

    private void observeBuildStates() {
        for (CameraNode node : NodeManager.get().getNodes()) {
            if (!node.autoManaged || node.buildSessionId == null || node.buildState == null
                    || node.buildState.isBlank()) {
                continue;
            }
            String previous = observedBuildStates.get(node.buildSessionId);
            if (previous == null && (node.buildState.equals("complete") || node.buildState.equals("stop"))) {
                // Persisted historical nodes are useful for later inspection,
                // but should not create fresh lifecycle markers on client join.
                observedBuildStates.put(node.buildSessionId, node.buildState);
                continue;
            }
            if (!node.buildState.equals(previous)) {
                boolean sent = sendAnnotation("Timelapse Build - version=1;state=" + safe(node.buildState)
                        + ";build=" + node.buildSessionId
                        + ";node=" + node.id
                        + ";mode=" + safe(node.buildMode));
                if (sent) observedBuildStates.put(node.buildSessionId, node.buildState);
            }
        }
    }

    private boolean sendCameraMarker(String mode, CameraNode node) {
        markerSequence++;
        StringBuilder marker = new StringBuilder("Camera - version=1;mode=")
                .append(mode)
                .append(";seq=").append(markerSequence)
                .append(";render=").append(renderSequence);
        if (node != null) {
            marker.append(";node=").append(node.id)
                    .append(";expectedFrames=1");
            if (node.buildSessionId != null) {
                marker.append(";build=").append(node.buildSessionId);
            }
            if (node.buildMode != null && !node.buildMode.isBlank()) {
                marker.append(";buildMode=").append(safe(node.buildMode));
            }
        }
        boolean sent = sendAnnotation(marker.toString());
        if (sent) lastCameraMarkerMode = mode;
        return sent;
    }

    private boolean sendAnnotation(String annotation) {
        Function<String, Boolean> sender = getAnnotationSender();
        if (sender == null) return false;
        try {
            return Boolean.TRUE.equals(sender.apply(annotation));
        } catch (RuntimeException exception) {
            Craneshot.LOGGER.debug("OBS annotation sender failed", exception);
            return false;
        }
    }

    private void applyMovementSettings(ICameraMovement movement) {
        if (movement instanceof AbstractMovementSettings settings) {
            this.endTarget = settings.getEndTarget();
            this.yawOffset = settings.getYawOffset();
            this.mouseWheel = settings.mouseWheel;
            CraneshotClient.CAMERA_CONTROLLER.setPreMoveStates(settings);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace(';', '_').replace('=', '_');
    }

    private static long secondsToNanos(float seconds) {
        return (long) (Math.max(1.0f, seconds) * 1_000_000_000L);
    }

    private static long millisecondsToNanos(int milliseconds) {
        return Math.max(0L, milliseconds) * 1_000_000L;
    }

    @Override
    public void queueReset(Minecraft client, Camera camera) {
        resetting = true;
        pendingPulses.clear();
        activeMovement.queueReset(client, camera);
    }

    @Override
    public void adjustDistance(boolean increase, Minecraft client) {
        activeMovement.adjustDistance(increase, client);
    }

    @Override
    public void adjustFov(boolean increase, Minecraft client) {
        if (activeMovement instanceof AbstractMovementSettings settings) {
            settings.adjustFov(increase, client);
        }
    }

    @Override
    public POST_MOVE_MOUSE getPostMoveMouse() {
        return activeMovement instanceof AbstractMovementSettings settings
                ? settings.getPostMoveMouse() : POST_MOVE_MOUSE.NONE;
    }

    @Override
    public POST_MOVE_KEYS getPostMoveKeys() {
        return activeMovement instanceof AbstractMovementSettings settings
                ? settings.getPostMoveKeys() : POST_MOVE_KEYS.NONE;
    }

    @Override
    public PROJECTION getProjection() {
        return activeMovement instanceof AbstractMovementSettings settings
                ? settings.getProjection() : PROJECTION.PERSPECTIVE;
    }

    @Override
    public boolean isHeadLockedToCamera() {
        return !(activeMovement instanceof AbstractMovementSettings settings)
                || settings.isHeadLockedToCamera();
    }

    @Override
    public String getName() {
        return "Follower Camera Director";
    }

    @Override
    public float getWeight() {
        return activeMovement.getWeight();
    }

    @Override
    public boolean isComplete() {
        return resetting && activeMovement.isComplete();
    }

    @Override
    public RaycastType getRaycastType() {
        return RaycastType.NONE;
    }

    @Override
    public boolean hasCompletedOutPhase() {
        return activeMovement.hasCompletedOutPhase();
    }

    private record TimelapsePulse(CameraNode node, CameraTarget target, double distanceSquared) { }

    private record SmoothedFocus(Vec3 position, long updatedAtNanos) { }
}
