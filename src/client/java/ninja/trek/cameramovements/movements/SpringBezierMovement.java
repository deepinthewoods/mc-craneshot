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

    @MovementSetting(label = "Position Halflife", min = 0.01, max = 1.0)
    private double positionHalflife = 0.15;

    @MovementSetting(label = "Rotation Halflife", min = 0.01, max = 1.0)
    private double rotationHalflife = 0.15;

    @MovementSetting(label = "Curve Speed", min = 0.5, max = 10.0)
    private double curveSpeed = 3.0;

    @MovementSetting(label = "Target Distance", min = 1.0, max = 50.0)
    private double targetDistance = 10.0;

    @MovementSetting(label = "Min Distance", min = 0.0, max = 10.0)
    private double minDistance = 2.0;

    @MovementSetting(label = "Max Distance", min = 10.0, max = 50.0)
    private double maxDistance = 20.0;

    @MovementSetting(label = "Control Point Displacement", min = 0.0, max = 30)
    private double controlPointDisplacement = 5;

    @MovementSetting(label = "Displacement Angle", min = -180.0, max = 180.0)
    private double displacementAngle = 0.0;

    @MovementSetting(label = "Displacement Angle Variance", min = 0.0, max = 180.0)
    private double displacementAngleVariance = 0.0;

    @MovementSetting(label = "FOV Halflife", min = 0.01, max = 1.0)
    private double fovHalflife = 0.15;

    public CameraTarget start = new CameraTarget();
    private CameraTarget end = new CameraTarget();
    public CameraTarget current = new CameraTarget();
    private Vec3d controlPoint;
    private double curveProgress;  // Progress along the Bezier curve (0 to 1)
    private boolean resetting = false;
    private float weight = 1.0f;

    // Spring velocity state
    private Vec3d positionVelocity = Vec3d.ZERO;
    private float yawVelocity = 0f;
    private float pitchVelocity = 0f;
    private float fovVelocity = 0f;

    // Cached curve endpoints for out phase
    private Vec3d curveStart;
    private Vec3d curveEnd;

    @Override
    public void start(MinecraftClient client, Camera camera) {
        start = CameraTarget.fromCamera(camera);
        current = CameraTarget.fromCamera(camera);

        Vec3d targetPos = calculateTargetPosition(CameraController.controlStick);
        end = new CameraTarget(targetPos, CameraController.controlStick.getYaw(),
                CameraController.controlStick.getPitch(), fovMultiplier);

        // Set up curve for out phase
        curveStart = start.getPosition();
        curveEnd = targetPos;
        controlPoint = generateControlPoint(curveStart, curveEnd);
        curveProgress = 0.0;

        resetting = false;
        weight = 1.0f;
        alpha = 1;

        // Initialize spring velocities
        positionVelocity = Vec3d.ZERO;
        yawVelocity = 0f;
        pitchVelocity = 0f;
        fovVelocity = 0f;
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
     * Critically damped spring using exact solution.
     */
    private Vec3d springDamperExact3D(Vec3d pos, Vec3d vel, Vec3d target, double halflife, double dt) {
        double damping = (4.0 * 0.69314718056) / Math.max(halflife, 0.001);
        double y = damping / 2.0;
        double eydt = Math.exp(-y * dt);

        Vec3d j0 = pos.subtract(target);
        Vec3d j1 = vel.add(j0.multiply(y));

        Vec3d newPos = j0.add(j1.multiply(dt)).multiply(eydt).add(target);
        positionVelocity = vel.subtract(j1.multiply(y * dt)).multiply(eydt);

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

        Vec3d targetPos;
        float targetYaw;
        float targetPitch;
        float targetFov;

        if (resetting) {
            // Return phase: spring directly toward player's head
            targetPos = client.player.getEyePos();
            targetYaw = client.player.getYaw();
            targetPitch = client.player.getPitch();
            targetFov = 1.0f;

        } else {
            // Out phase: advance curve progress and spring toward the curve point
            // The curve itself moves as the player moves

            // Update curve endpoints based on current player position
            curveStart = CameraController.controlStick.getPosition();
            curveEnd = calculateTargetPosition(CameraController.controlStick);

            // Regenerate control point if endpoints changed significantly
            // (This keeps the curve shape consistent relative to player)
            controlPoint = generateControlPoint(curveStart, curveEnd);

            // Advance curve progress based on time
            if (curveProgress < 1.0) {
                curveProgress += curveSpeed * deltaSeconds;
                if (curveProgress > 1.0) curveProgress = 1.0;
            }

            // The spring target is the point on the Bezier curve
            targetPos = quadraticBezier(curveStart, controlPoint, curveEnd, curveProgress);
            targetYaw = CameraController.controlStick.getYaw();
            targetPitch = CameraController.controlStick.getPitch();
            targetFov = fovMultiplier;

            // Update end for tracking
            end = new CameraTarget(curveEnd, targetYaw, targetPitch, targetFov);
        }

        // Apply spring for position - chases the target point
        Vec3d newPos = springDamperExact3D(
            current.getPosition(),
            positionVelocity,
            targetPos,
            positionHalflife,
            deltaSeconds
        );

        // Apply springs for rotation
        float newYaw = springDamperExact1D(current.getYaw(), yawVelocity, targetYaw, rotationHalflife, deltaSeconds, true);
        yawVelocity = springVelocityUpdate1D(yawVelocity, current.getYaw(), targetYaw, rotationHalflife, deltaSeconds, true);

        float newPitch = springDamperExact1D(current.getPitch(), pitchVelocity, targetPitch, rotationHalflife, deltaSeconds, false);
        pitchVelocity = springVelocityUpdate1D(pitchVelocity, current.getPitch(), targetPitch, rotationHalflife, deltaSeconds, false);

        // Apply spring for FOV
        float newFov = springDamperExact1D(current.getFovMultiplier(), fovVelocity, targetFov, fovHalflife, deltaSeconds, false);
        fovVelocity = springVelocityUpdate1D(fovVelocity, current.getFovMultiplier(), targetFov, fovHalflife, deltaSeconds, false);

        current = new CameraTarget(newPos, newYaw, newPitch, newFov);

        // Update FOV in game renderer
        if (client.gameRenderer instanceof FovAccessor) {
            ((FovAccessor) client.gameRenderer).setFovModifier(current.getFovMultiplier());
        }

        // Update alpha
        if (!resetting) {
            alpha = 1.0 - curveProgress;
        } else {
            double remaining = current.getPosition().distanceTo(targetPos);
            alpha = remaining;
        }

        // Completion check
        boolean complete = resetting &&
            current.getPosition().distanceTo(targetPos) < 0.005 &&
            Math.abs(current.getFovMultiplier() - 1.0f) < 0.01f;

        return new MovementState(current, complete);
    }

    @Override
    public void queueReset(MinecraftClient client, Camera camera) {
        if (!resetting) {
            resetting = true;
            resetReturnTargetTracking();
            curveProgress = 0.0;

            // Keep existing velocity for smooth transition into return
            // The spring will naturally redirect toward the player

            if (client.player != null) {
                float playerYaw = client.player.getYaw();
                float playerPitch = client.player.getPitch();
                Vec3d playerPos = client.player.getEyePos();
                end = new CameraTarget(playerPos, playerYaw, playerPitch, 1.0f);

                // Set up return curve
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

        // Keep velocity for smooth transition

        // Set up new out curve from current position
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
            return positionDistance < 0.005 && fovDifference < 0.01f;
        }
        return false;
    }

    @Override
    public boolean hasCompletedOutPhase() {
        if (resetting) return false;
        return curveProgress >= 0.99;
    }
}
