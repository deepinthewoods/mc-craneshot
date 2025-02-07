package ninja.trek.cameramovements.movements;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import ninja.trek.Craneshot;
import ninja.trek.cameramovements.CameraMovementType;
import ninja.trek.cameramovements.CameraTarget;
import ninja.trek.cameramovements.ICameraMovement;
import ninja.trek.cameramovements.MovementState;
import ninja.trek.cameramovements.AbstractMovementSettings;
import ninja.trek.config.MovementSetting;

@CameraMovementType(
        name = "Bezier Movement",
        description = "Moves the camera in a curved path using quadratic bezier interpolation"
)
public class BezierMovement extends AbstractMovementSettings implements ICameraMovement {
    @MovementSetting(label = "Position Easing", min = 0.01, max = 1.0)
    private double positionEasing = 0.1;

    @MovementSetting(label = "Position Speed Limit", min = 0.1, max = 100.0)
    private double positionSpeedLimit = 2.0;

    @MovementSetting(label = "Rotation Easing", min = 0.01, max = 1.0)
    private double rotationEasing = 0.1;

    @MovementSetting(label = "Rotation Speed Limit", min = 0.1, max = 3600.0)
    private double rotationSpeedLimit = 45.0;

    @MovementSetting(label = "Target Distance", min = 1.0, max = 50.0)
    private double targetDistance = 10.0;

    @MovementSetting(label = "Control Point Height", min = 1.0, max = 20.0)
    private double controlPointHeight = 5.0;

    @MovementSetting(label = "Min Distance", min = 1.0, max = 10.0)
    private double minDistance = 2.0;

    @MovementSetting(label = "Max Distance", min = 10.0, max = 50.0)
    private double maxDistance = 20.0;

    public CameraTarget start = new CameraTarget(), end = new CameraTarget(), current = new CameraTarget();
    private boolean resetting = false;
    private float weight = 1.0f;
    private double progress = 0.0;
    private Vec3d initialStart;
    private Vec3d initialEnd;
    private Vec3d initialControl;

    @Override
    public void start(MinecraftClient client, Camera camera) {
        PlayerEntity player = client.player;
        if (player == null) return;
        start = CameraTarget.fromCamera(camera);
        current = CameraTarget.fromCamera(camera);
        end = getEndTarget(player, targetDistance);

        // Store initial positions for the bezier curve
        initialStart = start.getPosition();
        initialEnd = end.getPosition();
        initialControl = calculateControlPoint(initialStart, initialEnd);

        resetting = false;
        weight = 1.0f;
        progress = 0.0;
    }

    private Vec3d calculateControlPoint(Vec3d start, Vec3d end) {
        Vec3d midpoint = start.lerp(end, 0.5);
        Vec3d direction = end.subtract(start);
        Vec3d perpendicular = direction.crossProduct(new Vec3d(0, 1, 0)).normalize();
        Vec3d upwardPerpendicular = perpendicular.add(new Vec3d(0, 1, 0)).normalize();
        return midpoint.add(upwardPerpendicular.multiply(controlPointHeight));
    }

    private Vec3d calculateBezierPoint(Vec3d p0, Vec3d p1, Vec3d p2, double t) {
        double oneMinusT = 1.0 - t;
        return p0.multiply(oneMinusT * oneMinusT)
                .add(p1.multiply(2.0 * oneMinusT * t))
                .add(p2.multiply(t * t));
    }

    @Override
    public MovementState calculateState(MinecraftClient client, Camera camera) {
        PlayerEntity player = client.player;
        if (player == null) return new MovementState(current, true);

        // Update targets
        start.set(player.getEyePos(), player.getYaw(), player.getPitch());
        end.set(getEndTarget(player, targetDistance));

        // Calculate current position on the bezier curve
        Vec3d currentPlayerPos = start.getPosition();

        if (!resetting) {
            progress = Math.min(1.0, progress + positionEasing);
            initialEnd = end.getPosition();
            initialControl = calculateControlPoint(initialStart, initialEnd);
        } else {
            progress = Math.min(1.0, progress + positionEasing);
            initialEnd = currentPlayerPos;
            initialControl = calculateControlPoint(initialStart, initialEnd);
        }

        double t = resetting ? 1.0 - progress : progress;
        Vec3d desired = calculateBezierPoint(
                initialStart,
                initialControl,
                initialEnd,
                t
        );

        // Apply speed limit to position change
        Vec3d moveVector = desired.subtract(current.getPosition());
        double moveDistance = moveVector.length();
        if (moveDistance > positionSpeedLimit * (1.0/20.0)) {
            moveVector = moveVector.normalize().multiply(positionSpeedLimit * (1.0/20.0));
            desired = current.getPosition().add(moveVector);
        }
        

        // Rotation interpolation with speed limit
        float targetYaw = resetting ? start.getYaw() : end.getYaw();
        float targetPitch = resetting ? start.getPitch() : end.getPitch();

        float yawDiff = targetYaw - current.getYaw();
        float pitchDiff = targetPitch - current.getPitch();

        // Normalize angles to [-180, 180]
        while (yawDiff > 180) yawDiff -= 360;
        while (yawDiff < -180) yawDiff += 360;

        // Apply easing to get desired rotation speed
        float desiredYawSpeed = (float)(yawDiff * rotationEasing);
        float desiredPitchSpeed = (float)(pitchDiff * rotationEasing);

        // Apply rotation speed limit
        float maxRotation = (float)(rotationSpeedLimit * (1.0/20.0)); // Convert degrees/second to degrees/tick
        if (Math.abs(desiredYawSpeed) > maxRotation) {
            desiredYawSpeed = Math.signum(desiredYawSpeed) * maxRotation;
        }
        if (Math.abs(desiredPitchSpeed) > maxRotation) {
            desiredPitchSpeed = Math.signum(desiredPitchSpeed) * maxRotation;
        }

        // Apply the final rotation
        float newYaw = current.getYaw() + desiredYawSpeed;
        float newPitch = current.getPitch() + desiredPitchSpeed;

        current = new CameraTarget(
                desired,
                newYaw,
                newPitch
        );

        boolean complete = resetting && progress >= 1.0 && current.getPosition().distanceTo(start.getPosition()) < 0.1;
        return new MovementState(current, complete);
    }

    @Override
    public void queueReset(MinecraftClient client, Camera camera) {
        if (client.player == null) return;
        resetting = true;
        progress = 0.0;
    }

    @Override
    public void adjustDistance(boolean increase) {
        double multiplier = increase ? 1.1 : 0.9;
        targetDistance = Math.max(minDistance, Math.min(maxDistance, targetDistance * multiplier));
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
        Craneshot.LOGGER.info("progress {} dist:{}", progress, current.getPosition().distanceTo(start.getPosition()));

        return resetting && progress >= 1.0 && current.getPosition().distanceTo(start.getPosition()) < 0.1;
    }
}