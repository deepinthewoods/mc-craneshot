package ninja.trek.cameramovements.movements;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import ninja.trek.CameraController;
import ninja.trek.cameramovements.*;
import ninja.trek.config.MovementSetting;
import ninja.trek.mixin.client.FovAccessor;

@CameraMovementType(
        name = "Spring Bezier",
        description = "Curved movement using critically damped springs for all phases"
)
public class SpringBezierMovement extends AbstractMovementSettings implements ICameraMovement {

    // === Spring Settings ===
    @MovementSetting(label = "Position Halflife", min = 0.01, max = 1.0)
    private double positionHalflife = 0.15;

    @MovementSetting(label = "Rotation Halflife", min = 0.01, max = 1.0)
    private double rotationHalflife = 0.15;

    @MovementSetting(label = "FOV Halflife", min = 0.01, max = 1.0)
    private double fovHalflife = 0.15;

    @MovementSetting(label = "Curve Speed", min = 0.5, max = 10.0)
    private double curveSpeed = 3.0;

    // === Distance Settings ===
    @MovementSetting(label = "Target Distance", min = 1.0, max = 50.0)
    private double targetDistance = 10.0;

    @MovementSetting(label = "Min Distance", min = 0.0, max = 10.0)
    private double minDistance = 2.0;

    @MovementSetting(label = "Max Distance", min = 10.0, max = 50.0)
    private double maxDistance = 20.0;

    // === Curve Settings ===
    @MovementSetting(label = "Control Point Displacement", min = 0.0, max = 30)
    private double controlPointDisplacement = 5;

    @MovementSetting(label = "Displacement Angle", min = -180.0, max = 180.0)
    private double displacementAngle = 0.0;

    @MovementSetting(label = "Displacement Angle Variance", min = 0.0, max = 180.0)
    private double displacementAngleVariance = 0.0;

    // === Final Return Settings ===
    public enum ReturnMode {
        SPRING_ONLY,      // Basic spring - will lag behind moving targets
        VELOCITY_MATCH,   // Spring that matches target velocity on arrival
        PREDICTIVE        // Spring that targets predicted future position
    }

    @MovementSetting(label = "Return Mode", description = "How to handle returning to a moving player")
    private ReturnMode returnMode = ReturnMode.VELOCITY_MATCH;

    @MovementSetting(label = "Velocity Influence", min = 0.0, max = 2.0, description = "How strongly to match player velocity (Velocity Match mode)")
    private double velocityInfluence = 1.0;

    @MovementSetting(label = "Prediction Time", min = 0.0, max = 0.5, description = "How far ahead to predict player position in seconds (Predictive mode)")
    private double predictionTime = 0.15;

    // === State ===
    public CameraTarget start = new CameraTarget();
    private CameraTarget end = new CameraTarget();
    public CameraTarget current = new CameraTarget();
    private Vec3d controlPoint;
    private double curveProgress;
    private boolean resetting = false;
    private float weight = 1.0f;

    // Spring velocity state
    private Vec3d positionVelocity = Vec3d.ZERO;
    private float yawVelocity = 0f;
    private float pitchVelocity = 0f;
    private float fovVelocity = 0f;

    // Cached curve endpoints
    private Vec3d curveStart;
    private Vec3d curveEnd;

    // For tracking player velocity
    private Vec3d lastPlayerPos = null;
    private float lastPlayerYaw = 0f;
    private float lastPlayerPitch = 0f;

    // For overshoot detection
    private double lastDistanceToPlayer = Double.MAX_VALUE;

    @Override
    public void start(MinecraftClient client, Camera camera) {
        start = CameraTarget.fromCamera(camera);
        current = CameraTarget.fromCamera(camera);

        Vec3d targetPos = calculateTargetPosition(CameraController.controlStick);
        end = new CameraTarget(targetPos, CameraController.controlStick.getYaw(),
                CameraController.controlStick.getPitch(), fovMultiplier);

        curveStart = start.getPosition();
        curveEnd = targetPos;
        controlPoint = generateControlPoint(curveStart, curveEnd);
        curveProgress = 0.0;

        resetting = false;
        weight = 1.0f;
        alpha = 1;

        positionVelocity = Vec3d.ZERO;
        yawVelocity = 0f;
        pitchVelocity = 0f;
        fovVelocity = 0f;

        lastPlayerPos = null;
        lastPlayerYaw = 0f;
        lastPlayerPitch = 0f;

        // Reset overshoot detection
        lastDistanceToPlayer = Double.MAX_VALUE;
    }

    private Vec3d calculateTargetPosition(CameraTarget stick) {
        double yaw = Math.toRadians(stick.getYaw());
        double pitch = Math.toRadians(stick.getPitch());
        double xOffset = Math.sin(yaw) * Math.cos(pitch) * targetDistance;
        double yOffset = Math.sin(pitch) * targetDistance;
        double zOffset = -Math.cos(yaw) * Math.cos(pitch) * targetDistance;
        return stick.getPosition().add(xOffset, yOffset, zOffset);
    }

    /**
     * Basic critically damped spring - converges to target with zero velocity.
     */
    private Vec3d springDamperBasic(Vec3d pos, Vec3d vel, Vec3d target, double halflife, double dt) {
        double damping = (4.0 * 0.69314718056) / Math.max(halflife, 0.001);
        double y = damping / 2.0;
        double eydt = Math.exp(-y * dt);

        Vec3d j0 = pos.subtract(target);
        Vec3d j1 = vel.add(j0.multiply(y));

        Vec3d newPos = j0.add(j1.multiply(dt)).multiply(eydt).add(target);
        positionVelocity = vel.subtract(j1.multiply(y * dt)).multiply(eydt);

        return newPos;
    }

    /**
     * Velocity-matching critically damped spring - converges to target position AND velocity.
     */
    private Vec3d springDamperVelocityMatch(Vec3d pos, Vec3d vel, Vec3d targetPos, Vec3d targetVel, double halflife, double dt) {
        double damping = (4.0 * 0.69314718056) / Math.max(halflife, 0.001);
        double y = damping / 2.0;
        double eydt = Math.exp(-y * dt);

        Vec3d j0 = pos.subtract(targetPos);
        Vec3d j1 = vel.subtract(targetVel).add(j0.multiply(y));

        Vec3d newPos = j0.add(j1.multiply(dt)).multiply(eydt).add(targetPos);
        positionVelocity = vel.subtract(targetVel).subtract(j1.multiply(y * dt)).multiply(eydt).add(targetVel);

        return newPos;
    }

    private float springDamperExact1D(float pos, float vel, float target, double halflife, double dt, boolean isAngle) {
        float error = target - pos;
        if (isAngle) {
            while (error > 180) error -= 360;
            while (error < -180) error += 360;
            target = pos + error;
        }

        double damping = (4.0 * 0.69314718056) / Math.max(halflife, 0.001);
        double y = damping / 2.0;
        double eydt = Math.exp(-y * dt);

        float j0 = pos - target;
        float j1 = vel + j0 * (float)y;

        return (float)((j0 + j1 * dt) * eydt + target);
    }

    private float springDamperVelocityMatch1D(float pos, float vel, float target, float targetVel, double halflife, double dt, boolean isAngle) {
        float error = target - pos;
        if (isAngle) {
            while (error > 180) error -= 360;
            while (error < -180) error += 360;
            target = pos + error;
        }

        double damping = (4.0 * 0.69314718056) / Math.max(halflife, 0.001);
        double y = damping / 2.0;
        double eydt = Math.exp(-y * dt);

        float j0 = pos - target;
        float j1 = (vel - targetVel) + j0 * (float)y;

        return (float)((j0 + j1 * dt) * eydt + target);
    }

    private float springVelocityUpdate1D(float vel, float pos, float target, double halflife, double dt, boolean isAngle) {
        float error = target - pos;
        if (isAngle) {
            while (error > 180) error -= 360;
            while (error < -180) error += 360;
            target = pos + error;
        }

        double damping = (4.0 * 0.69314718056) / Math.max(halflife, 0.001);
        double y = damping / 2.0;
        double eydt = Math.exp(-y * dt);

        float j0 = pos - target;
        float j1 = vel + j0 * (float)y;

        return (float)((vel - j1 * y * dt) * eydt);
    }

    private float springVelocityUpdate1DVelMatch(float vel, float pos, float target, float targetVel, double halflife, double dt, boolean isAngle) {
        float error = target - pos;
        if (isAngle) {
            while (error > 180) error -= 360;
            while (error < -180) error += 360;
            target = pos + error;
        }

        double damping = (4.0 * 0.69314718056) / Math.max(halflife, 0.001);
        double y = damping / 2.0;
        double eydt = Math.exp(-y * dt);

        float j0 = pos - target;
        float j1 = (vel - targetVel) + j0 * (float)y;

        return (float)(((vel - targetVel) - j1 * y * dt) * eydt + targetVel);
    }

    private Vec3d quadraticBezier(Vec3d p0, Vec3d p1, Vec3d p2, double t) {
        double oneMinusT = 1.0 - t;
        return p0.multiply(oneMinusT * oneMinusT)
                .add(p1.multiply(2 * oneMinusT * t))
                .add(p2.multiply(t * t));
    }

    private Vec3d generateControlPoint(Vec3d start, Vec3d end) {
        Vec3d mid = start.add(end).multiply(0.5);
        Vec3d diff = end.subtract(start);

        if (diff.lengthSquared() < 1e-6) {
            return mid.add(new Vec3d(0, controlPointDisplacement, 0));
        }

        Vec3d direction = diff.normalize();
        Vec3d worldUp = new Vec3d(0, 1, 0);
        Vec3d right = direction.crossProduct(worldUp).normalize();
        Vec3d perpUp = direction.crossProduct(right).normalize();

        if (perpUp.y < 0) {
            perpUp = perpUp.multiply(-1);
        }

        if (Math.abs(displacementAngle) > 0 || displacementAngleVariance > 0) {
            double angleOffset = displacementAngle +
                    (displacementAngleVariance > 0 ? (Math.random() * 2 - 1) * displacementAngleVariance : 0);
            double angleRadians = Math.toRadians(angleOffset);
            perpUp = perpUp.multiply(Math.cos(angleRadians))
                    .add(direction.crossProduct(perpUp).multiply(Math.sin(angleRadians)));
        }

        return mid.add(perpUp.multiply(controlPointDisplacement));
    }

    @Override
    public MovementState calculateState(MinecraftClient client, Camera camera, float deltaSeconds) {
        if (client.player == null) return new MovementState(current, true);

        // Calculate player velocity for return modes
        Vec3d playerPos = client.player.getEyePos();
        float playerYaw = client.player.getYaw();
        float playerPitch = client.player.getPitch();

        Vec3d playerVelocity = Vec3d.ZERO;
        float playerYawVelocity = 0f;
        float playerPitchVelocity = 0f;

        if (lastPlayerPos != null && deltaSeconds > 0.0001f) {
            playerVelocity = playerPos.subtract(lastPlayerPos).multiply(1.0 / deltaSeconds);
            playerYawVelocity = (playerYaw - lastPlayerYaw) / deltaSeconds;
            playerPitchVelocity = (playerPitch - lastPlayerPitch) / deltaSeconds;

            while (playerYawVelocity > 180 / deltaSeconds) playerYawVelocity -= 360 / deltaSeconds;
            while (playerYawVelocity < -180 / deltaSeconds) playerYawVelocity += 360 / deltaSeconds;
        }

        lastPlayerPos = playerPos;
        lastPlayerYaw = playerYaw;
        lastPlayerPitch = playerPitch;

        Vec3d targetPos;
        float targetYaw;
        float targetPitch;
        float targetFov;
        Vec3d targetVelocity = Vec3d.ZERO;
        float targetYawVel = 0f;
        float targetPitchVel = 0f;

        if (resetting) {
            // Return phase: spring toward player's head
            if (returnMode == ReturnMode.PREDICTIVE) {
                targetPos = playerPos.add(playerVelocity.multiply(predictionTime));
                targetYaw = playerYaw + playerYawVelocity * (float)predictionTime;
                targetPitch = playerPitch + playerPitchVelocity * (float)predictionTime;
            } else {
                targetPos = playerPos;
                targetYaw = playerYaw;
                targetPitch = playerPitch;
            }

            if (returnMode == ReturnMode.VELOCITY_MATCH) {
                targetVelocity = playerVelocity.multiply(velocityInfluence);
                targetYawVel = playerYawVelocity * (float)velocityInfluence;
                targetPitchVel = playerPitchVelocity * (float)velocityInfluence;
            }

            targetFov = 1.0f;

        } else {
            // Out phase: advance curve progress and spring toward the curve point
            curveStart = CameraController.controlStick.getPosition();
            curveEnd = calculateTargetPosition(CameraController.controlStick);
            controlPoint = generateControlPoint(curveStart, curveEnd);

            if (curveProgress < 1.0) {
                curveProgress += curveSpeed * deltaSeconds;
                if (curveProgress > 1.0) curveProgress = 1.0;
            }

            targetPos = quadraticBezier(curveStart, controlPoint, curveEnd, curveProgress);
            targetYaw = CameraController.controlStick.getYaw();
            targetPitch = CameraController.controlStick.getPitch();
            targetFov = fovMultiplier;

            end = new CameraTarget(curveEnd, targetYaw, targetPitch, targetFov);
        }

        // Apply spring for position
        Vec3d newPos;
        if (resetting && returnMode == ReturnMode.VELOCITY_MATCH) {
            newPos = springDamperVelocityMatch(
                current.getPosition(),
                positionVelocity,
                targetPos,
                targetVelocity,
                positionHalflife,
                deltaSeconds
            );
        } else {
            newPos = springDamperBasic(
                current.getPosition(),
                positionVelocity,
                targetPos,
                positionHalflife,
                deltaSeconds
            );
        }

        // Apply springs for rotation
        float newYaw, newPitch;
        if (resetting && returnMode == ReturnMode.VELOCITY_MATCH) {
            newYaw = springDamperVelocityMatch1D(current.getYaw(), yawVelocity, targetYaw, targetYawVel, rotationHalflife, deltaSeconds, true);
            yawVelocity = springVelocityUpdate1DVelMatch(yawVelocity, current.getYaw(), targetYaw, targetYawVel, rotationHalflife, deltaSeconds, true);

            newPitch = springDamperVelocityMatch1D(current.getPitch(), pitchVelocity, targetPitch, targetPitchVel, rotationHalflife, deltaSeconds, false);
            pitchVelocity = springVelocityUpdate1DVelMatch(pitchVelocity, current.getPitch(), targetPitch, targetPitchVel, rotationHalflife, deltaSeconds, false);
        } else {
            newYaw = springDamperExact1D(current.getYaw(), yawVelocity, targetYaw, rotationHalflife, deltaSeconds, true);
            yawVelocity = springVelocityUpdate1D(yawVelocity, current.getYaw(), targetYaw, rotationHalflife, deltaSeconds, true);

            newPitch = springDamperExact1D(current.getPitch(), pitchVelocity, targetPitch, rotationHalflife, deltaSeconds, false);
            pitchVelocity = springVelocityUpdate1D(pitchVelocity, current.getPitch(), targetPitch, rotationHalflife, deltaSeconds, false);
        }

        // Apply spring for FOV
        float newFov = springDamperExact1D(current.getFovMultiplier(), fovVelocity, targetFov, fovHalflife, deltaSeconds, false);
        fovVelocity = springVelocityUpdate1D(fovVelocity, current.getFovMultiplier(), targetFov, fovHalflife, deltaSeconds, false);

        current = new CameraTarget(newPos, newYaw, newPitch, newFov);

        if (client.gameRenderer instanceof FovAccessor) {
            ((FovAccessor) client.gameRenderer).setFovModifier(current.getFovMultiplier());
        }

        // Calculate distance to player for completion/overshoot checks
        double distanceToPlayer = current.getPosition().distanceTo(playerPos);

        // Overshoot detection during return phase
        // If distance is increasing and we're close, we've passed the player - clamp and complete
        boolean overshot = false;
        if (resetting && distanceToPlayer > lastDistanceToPlayer && lastDistanceToPlayer < 2.0) {
            // We overshot - snap to player position
            current = new CameraTarget(playerPos, playerYaw, playerPitch, 1.0f);
            positionVelocity = playerVelocity; // Match player velocity on completion
            overshot = true;
        }
        lastDistanceToPlayer = distanceToPlayer;

        // Update alpha
        if (!resetting) {
            alpha = 1.0 - curveProgress;
        } else {
            alpha = distanceToPlayer;
        }

        // Completion check - use actual player position, or overshot
        boolean complete = overshot || (resetting &&
            distanceToPlayer < 0.05 &&
            Math.abs(current.getFovMultiplier() - 1.0f) < 0.01f);

        return new MovementState(current, complete);
    }

    @Override
    public void queueReset(MinecraftClient client, Camera camera) {
        if (!resetting) {
            resetting = true;
            resetReturnTargetTracking();
            curveProgress = 0.0;

            lastPlayerPos = null;

            // Reset overshoot detection
            lastDistanceToPlayer = Double.MAX_VALUE;

            if (client.player != null) {
                float playerYaw = client.player.getYaw();
                float playerPitch = client.player.getPitch();
                Vec3d playerPos = client.player.getEyePos();
                end = new CameraTarget(playerPos, playerYaw, playerPitch, 1.0f);

                curveStart = current.getPosition();
                curveEnd = playerPos;
                controlPoint = generateControlPoint(curveStart, curveEnd);
            }

            current = CameraTarget.fromCamera(camera);
        }
    }

    public boolean isResetting() {
        return resetting;
    }

    public void resumeOutPhase(MinecraftClient client, Camera camera) {
        if (!resetting) {
            return;
        }
        resetting = false;
        curveProgress = 0.0;

        if (camera != null) {
            current = CameraTarget.fromCamera(camera);
        }

        curveStart = current.getPosition();
        curveEnd = calculateTargetPosition(CameraController.controlStick);
        controlPoint = generateControlPoint(curveStart, curveEnd);

        end = new CameraTarget(
                curveEnd,
                CameraController.controlStick.getYaw(),
                CameraController.controlStick.getPitch(),
                fovMultiplier
        );

        alpha = 1.0;
    }

    @Override
    public void adjustDistance(boolean increase, MinecraftClient client) {
        if (mouseWheel == SCROLL_WHEEL.DISTANCE) {
            double multiplier = increase ? 1.2 : 0.8;
            targetDistance = Math.max(minDistance, Math.min(maxDistance, targetDistance * multiplier));
        } else if (mouseWheel == SCROLL_WHEEL.FOV) {
            adjustFov(increase, client);
        }
    }

    @Override
    public void adjustFov(boolean increase, MinecraftClient client) {
        if (mouseWheel != SCROLL_WHEEL.FOV) return;
        super.adjustFov(increase, client);
    }

    @Override
    public String getName() {
        return "Spring Bezier";
    }

    @Override
    public float getWeight() {
        return weight;
    }

    @Override
    public boolean isComplete() {
        if (resetting) {
            double positionDistance = current.getPosition().distanceTo(end.getPosition());
            float fovDifference = Math.abs(current.getFovMultiplier() - 1.0f);
            return positionDistance < 0.05 && fovDifference < 0.01f;
        }
        return false;
    }

    @Override
    public boolean hasCompletedOutPhase() {
        if (resetting) return false;
        return curveProgress >= 0.99;
    }
}
