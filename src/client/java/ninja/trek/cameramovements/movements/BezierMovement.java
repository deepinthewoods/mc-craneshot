package ninja.trek.cameramovements.movements;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import ninja.trek.cameramovements.CameraMovementType;
import ninja.trek.cameramovements.CameraTarget;
import ninja.trek.cameramovements.ICameraMovement;
import ninja.trek.cameramovements.MovementState;
import ninja.trek.cameramovements.AbstractMovementSettings;
import ninja.trek.config.MovementSetting;

/**
 * Moves the camera along a quadratic Bézier curve. The curve is defined by three points:
 * the start (initial camera position), the end (computed from the player’s position and target distance)
 * and a control point computed as the midpoint between start and end, displaced in a random direction.
 *
 * When the movement is “reset” the same curve is followed in reverse.
 */
@CameraMovementType(
        name = "Bezier Movement",
        description = "Moves the camera along a quadratic Bézier curve with one control point"
)
public class BezierMovement extends AbstractMovementSettings implements ICameraMovement {

    // --- Settings (similar to LinearMovement) ---
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

    @MovementSetting(label = "Min Distance", min = 1.0, max = 10.0)
    private double minDistance = 2.0;

    @MovementSetting(label = "Max Distance", min = 10.0, max = 50.0)
    private double maxDistance = 20.0;

    // --- New setting: how far to displace the curve’s control point ---
    @MovementSetting(label = "Control Point Displacement", min = 0.0, max = 10.0)
    private double controlPointDisplacement = 2.0;

    // --- Internal fields for the fixed Bézier path ---
    private CameraTarget initialStart; // starting camera target (captured at start())
    private CameraTarget initialEnd;   // computed end target (captured at start())
    private Vec3d controlPoint;        // computed as the midpoint, then displaced randomly
    private double progress;           // parameter [0,1] for movement along the curve
    private boolean resetting = false; // whether we are “resetting” (i.e. going in reverse)
    private float weight = 1.0f;

    // The current camera target – updated every tick
    private CameraTarget current;

    @Override
    public void start(MinecraftClient client, Camera camera) {
        PlayerEntity player = client.player;
        if (player == null) return;

        // Capture the current camera state as the start
        initialStart = CameraTarget.fromCamera(camera);
        // Compute the end target (using the same helper as in LinearMovement)
        initialEnd = getEndTarget(player, targetDistance);

        // Compute the control point:
        // First, take the midpoint between the start and end positions…
        Vec3d mid = initialStart.getPosition().add(initialEnd.getPosition()).multiply(0.5);
        // …then add a random displacement of length controlPointDisplacement.
        // (Here we generate a random unit vector in 3D.)
        double theta = Math.random() * 2 * Math.PI;
        double phi = Math.acos(2 * Math.random() - 1);
        double x = Math.sin(phi) * Math.cos(theta);
        double y = Math.sin(phi) * Math.sin(theta);
        double z = Math.cos(phi);
        Vec3d randomDir = new Vec3d(x, y, z);
        Vec3d offset = randomDir.multiply(controlPointDisplacement);
        controlPoint = mid.add(offset);

        // Initialize progress along the curve (0 means start, 1 means end)
        progress = 0.0;
        resetting = false;
        // Start at the initial camera target.
        // (Assuming CameraTarget has a copy constructor or similar method.)
        current = new CameraTarget(initialStart.getPosition(), initialStart.getYaw(), initialStart.getPitch());
        weight = 1.0f;
    }

    /**
     * Helper method for quadratic Bézier interpolation.
     *
     * @param p0 starting point
     * @param p1 control point
     * @param p2 ending point
     * @param t  interpolation parameter between 0 and 1
     * @return the interpolated point on the Bézier curve
     */
    private Vec3d quadraticBezier(Vec3d p0, Vec3d p1, Vec3d p2, double t) {
        double oneMinusT = 1 - t;
        return p0.multiply(oneMinusT * oneMinusT)
                .add(p1.multiply(2 * oneMinusT * t))
                .add(p2.multiply(t * t));
    }

    /**
     * Linearly interpolate between two angles (in degrees), taking wrap‐around into account.
     */
    private float lerpAngle(float a, float b, double t) {
        float diff = b - a;
        while (diff > 180) diff -= 360;
        while (diff < -180) diff += 360;
        return a + (float) (diff * t);
    }

    @Override
    public MovementState calculateState(MinecraftClient client, Camera camera) {
        // For the Bézier movement the endpoints for the interpolation depend on whether we’re resetting.
        // In the forward movement, we go from initialStart to initialEnd.
        // When resetting, we reverse them.
        CameraTarget a, b;
        if (!resetting) {
            a = initialStart;
            b = initialEnd;
        } else {
            a = initialEnd;
            b = initialStart;
        }

        // Advance the progress parameter using an easing factor.
        // (This is similar in spirit to how the LinearMovement code uses lerp.)
        double newProgress = progress + (1 - progress) * positionEasing;
        if (newProgress > 1.0) {
            newProgress = 1.0;
        }
        progress = newProgress;

        // Compute the “ideal” position on the Bézier curve given the current progress.
        Vec3d desiredPos = quadraticBezier(a.getPosition(), controlPoint, b.getPosition(), progress);

        // Now move the current position toward the desired position,
        // applying a speed limit similar to LinearMovement.
        Vec3d currentPos = current.getPosition();
        Vec3d moveVector = desiredPos.subtract(currentPos);
        double moveDistance = moveVector.length();
        if (moveDistance > 0.01) {
            double maxMove = positionSpeedLimit * (1.0 / 20.0); // per-tick move limit
            if (moveDistance > maxMove) {
                Vec3d limitedMove = moveVector.normalize().multiply(maxMove);
                desiredPos = currentPos.add(limitedMove);
            }
        }

        // For rotation we interpolate between the start and end rotations.
        float desiredYaw = lerpAngle(a.getYaw(), b.getYaw(), progress);
        float desiredPitch = lerpAngle(a.getPitch(), b.getPitch(), progress);

        // Just as in LinearMovement, we use easing plus a speed limit for rotation.
        float yawDiff = desiredYaw - current.getYaw();
        float pitchDiff = desiredPitch - current.getPitch();
        while (yawDiff > 180) yawDiff -= 360;
        while (yawDiff < -180) yawDiff += 360;
        float desiredYawSpeed = (float) (yawDiff * rotationEasing);
        float desiredPitchSpeed = (float) (pitchDiff * rotationEasing);
        float maxRotation = (float) (rotationSpeedLimit * (1.0 / 20.0));
        if (Math.abs(desiredYawSpeed) > maxRotation) {
            desiredYawSpeed = Math.signum(desiredYawSpeed) * maxRotation;
        }
        if (Math.abs(desiredPitchSpeed) > maxRotation) {
            desiredPitchSpeed = Math.signum(desiredPitchSpeed) * maxRotation;
        }
        float newYaw = current.getYaw() + desiredYawSpeed;
        float newPitch = current.getPitch() + desiredPitchSpeed;

        // Update the current camera target
        current = new CameraTarget(desiredPos, newYaw, newPitch);

        // Compute an “alpha” value similar to LinearMovement – the ratio of the distance
        // from our current position to b over the total chord length.
        double totalDistance = a.getPosition().distanceTo(b.getPosition());
        double remaining = current.getPosition().distanceTo(b.getPosition());
        alpha = totalDistance != 0 ? remaining / totalDistance : 0.0;

        // Determine whether the movement is complete. In our design only the reset phase can finish.
        boolean complete = false;
        if (resetting && current.getPosition().distanceTo(b.getPosition()) < 0.01) {
            complete = true;
        }

        return new MovementState(current, complete);
    }

    @Override
    public void queueReset(MinecraftClient client, Camera camera) {
        // When resetting, we want to retrace the same Bézier curve in reverse.
        // So we swap the roles of start and end (and keep the same control point) and reset the progress.
        if (!resetting) {
            resetting = true;
            progress = 0.0;
        }
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
        // The movement is considered complete if we are in the resetting phase
        // and our current position is near the (original) start.
        return resetting && current.getPosition().distanceTo(initialStart.getPosition()) < 0.01;
    }
}
