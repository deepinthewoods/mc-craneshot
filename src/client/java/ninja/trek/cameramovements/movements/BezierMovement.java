package ninja.trek.cameramovements.movements;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import ninja.trek.CameraController;
import ninja.trek.cameramovements.*;
import ninja.trek.config.MovementSetting;

@CameraMovementType(
        name = "Bezier",
        description = "Moves the camera in a curved line"
)
public class BezierMovement extends AbstractMovementSettings implements ICameraMovement {
    @MovementSetting(label = "Position Easing", min = 0.01, max = 1.0)
    private double positionEasing = 0.1;

    @MovementSetting(label = "Position Speed Limit", min = 0.1, max = 100.0)
    private double positionSpeedLimit = 10;

    @MovementSetting(label = "Rotation Easing", min = 0.01, max = 1.0)
    private double rotationEasing = 0.1;

    @MovementSetting(label = "Rotation Speed Limit", min = 0.1, max = 1000)
    private double rotationSpeedLimit = 500;

    @MovementSetting(label = "Target Distance", min = 1.0, max = 50.0)
    private double targetDistance = 10.0;

    @MovementSetting(label = "Min Distance", min = 1.0, max = 10.0)
    private double minDistance = 2.0;

    @MovementSetting(label = "Max Distance", min = 10.0, max = 50.0)
    private double maxDistance = 20.0;

    @MovementSetting(label = "Control Point Displacement", min = 0.0, max = 30)
    private double controlPointDisplacement = 5;

    @MovementSetting(label = "Displacement Angle", min = -180.0, max = 180.0)
    private double displacementAngle = 0.0;

    @MovementSetting(label = "Displacement Angle Variance", min = 0.0, max = 180.0)
    private double displacementAngleVariance = 0.0;

    private CameraTarget start = new CameraTarget();
    private CameraTarget end = new CameraTarget();
    private CameraTarget current = new CameraTarget();
    private Vec3d controlPoint;

    private double progress;
    private boolean resetting = false;
    private boolean linearMode = false;
    private boolean distanceChanged = false;
    private float weight = 1.0f;

    @Override
    public void start(MinecraftClient client, Camera camera) {
        // Initialize with camera's current state
        start = CameraTarget.fromCamera(camera);
        current = CameraTarget.fromCamera(camera);

        // Calculate end target based on controlStick
        Vec3d targetPos = calculateTargetPosition(CameraController.controlStick);
        end = new CameraTarget(targetPos, CameraController.controlStick.getYaw(), CameraController.controlStick.getPitch());

        // Generate control point for the bezier curve
        controlPoint = generateControlPoint(start.getPosition(), end.getPosition());

        progress = 0.0;
        resetting = false;
        linearMode = false;
        distanceChanged = false;
        weight = 1.0f;
    }

    private Vec3d calculateTargetPosition(CameraTarget stick) {
        double yaw = Math.toRadians(stick.getYaw());
        double pitch = Math.toRadians(stick.getPitch());
        // Calculate offset based on target distance
        double xOffset = Math.sin(yaw) * Math.cos(pitch) * targetDistance;
        double yOffset = Math.sin(pitch) * targetDistance;
        double zOffset = -Math.cos(yaw) * Math.cos(pitch) * targetDistance;
        return stick.getPosition().add(xOffset, yOffset, zOffset);
    }

    @Override
    public MovementState calculateState(MinecraftClient client, Camera camera) {
        if (client.player == null) return new MovementState(current, true);

        // Update start target with controlStick's current state
        start = new CameraTarget(
                CameraController.controlStick.getPosition(),
                CameraController.controlStick.getYaw(),
                CameraController.controlStick.getPitch()
        );

        // Update end target based on controlStick and target distance
        Vec3d targetPos = calculateTargetPosition(CameraController.controlStick);
        end = new CameraTarget(targetPos, CameraController.controlStick.getYaw(), CameraController.controlStick.getPitch());

        // Regenerate control point if distance changed
        if (distanceChanged) {
            controlPoint = generateControlPoint(start.getPosition(), end.getPosition());
            distanceChanged = false;
        }

        CameraTarget a = resetting ? end : start;
        CameraTarget b = resetting ? start : end;

        Vec3d desiredPos;
        if (!linearMode) {
            // Bezier movement mode
            double potentialDelta = (1.0 - progress) * positionEasing;
            double totalDistance = a.getPosition().distanceTo(b.getPosition());
            double maxMove = positionSpeedLimit * (1.0 / 20.0);
            double allowedDelta = totalDistance > 0 ? maxMove / totalDistance : potentialDelta;
            double progressDelta = Math.min(potentialDelta, allowedDelta);
            progress = Math.min(1.0, progress + progressDelta);

            desiredPos = quadraticBezier(
                    a.getPosition(),
                    controlPoint,
                    b.getPosition(),
                    progress
            );
        } else {
            // Linear movement mode
            Vec3d delta = b.getPosition().subtract(current.getPosition());
            double deltaLength = delta.length();
            double maxMove = positionSpeedLimit * (1.0 / 20.0);
            Vec3d move;
            if (deltaLength > 0) {
                move = delta.multiply(positionEasing);
                if (move.length() > maxMove) {
                    move = move.normalize().multiply(maxMove);
                }
            } else {
                move = Vec3d.ZERO;
            }
            desiredPos = current.getPosition().add(move);
        }

        // Calculate target rotation
        float targetYaw = b.getYaw();
        float targetPitch = b.getPitch();

        // Apply rotation easing
        float yawError = targetYaw - current.getYaw();
        float pitchError = targetPitch - current.getPitch();

        while (yawError > 180) yawError -= 360;
        while (yawError < -180) yawError += 360;

        float desiredYawSpeed = (float)(yawError * rotationEasing);
        float desiredPitchSpeed = (float)(pitchError * rotationEasing);
        float maxRotation = (float)(rotationSpeedLimit * (1.0 / 20.0));

        if (Math.abs(desiredYawSpeed) > maxRotation) {
            desiredYawSpeed = Math.signum(desiredYawSpeed) * maxRotation;
        }
        if (Math.abs(desiredPitchSpeed) > maxRotation) {
            desiredPitchSpeed = Math.signum(desiredPitchSpeed) * maxRotation;
        }

        float newYaw = current.getYaw() + desiredYawSpeed;
        float newPitch = current.getPitch() + desiredPitchSpeed;

        // Update current target
        current = new CameraTarget(desiredPos, newYaw, newPitch);

        // Update alpha for external systems
        double remaining = current.getPosition().distanceTo(b.getPosition());
        double totalDistance = a.getPosition().distanceTo(b.getPosition());
        alpha = totalDistance != 0 ? remaining / totalDistance : 0.0;

        // Switch to linear mode when out phase completes
        if (!resetting && progress >= 0.999) {
            linearMode = true;
        }

        boolean complete = resetting && progress >= 0.999;
        return new MovementState(current, complete);
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

        // If points are too close, force upward control point
        if (diff.lengthSquared() < 1e-6) {
            return mid.add(new Vec3d(0, controlPointDisplacement, 0));
        }

        // Calculate normalized direction vector
        Vec3d direction = diff.normalize();
        Vec3d worldUp = new Vec3d(0, 1, 0);

        // Calculate up vector that's perpendicular to movement direction
        Vec3d right = direction.crossProduct(worldUp).normalize();
        Vec3d perpUp = direction.crossProduct(right).normalize();

        // Ensure upward direction
        if (perpUp.y < 0) {
            perpUp = perpUp.multiply(-1);
        }

        // Apply displacement
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
    public void queueReset(MinecraftClient client, Camera camera) {
        if (!resetting) {
            resetting = true;
            linearMode = false;
            progress = 0.0;
            controlPoint = generateControlPoint(end.getPosition(), start.getPosition());
        }
    }

    @Override
    public void adjustDistance(boolean increase) {
        double multiplier = increase ? 1.1 : 0.9;
        targetDistance = Math.max(minDistance, Math.min(maxDistance, targetDistance * multiplier));
        distanceChanged = true;
    }

    @Override
    public String getName() {
        return "Bezier";
    }

    @Override
    public float getWeight() {
        return weight;
    }

    @Override
    public boolean isComplete() {
        return resetting && current.getPosition().distanceTo(start.getPosition()) < 0.03;
    }

    @Override
    public boolean hasCompletedOutPhase() {
        if (resetting) return false;
        if (linearMode) {
            return alpha < 0.1;
        } else {
            return progress >= 0.999;
        }
    }
}