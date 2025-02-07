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
 * Moves the camera along a quadratic Bézier curve calculated in coordinates relative to the player's head.
 * The relative control points are computed once at start(), and on each update the relative position is
 * transformed using the player's current head rotation.
 *
 * When resetting the curve is retraced in reverse.
 */
@CameraMovementType(
        name = "Bezier Movement (Relative)",
        description = "Moves the camera along a quadratic Bézier curve in relative coordinates, applying player head rotation"
)
public class BezierMovement extends AbstractMovementSettings implements ICameraMovement {

    // --- Settings ---
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

    // New setting controlling how far the Bézier control point is displaced
    @MovementSetting(label = "Control Point Displacement", min = 0.0, max = 10.0)
    private double controlPointDisplacement = 2.0;

    // --- Internal fields for the relative Bézier path ---
    // These are stored in a coordinate system defined by the player's head (eye) position and orientation at start()
    private Vec3d initialRelativeStart;
    private Vec3d initialRelativeEnd;
    private Vec3d controlPointRelative;
    private double progress;
    private boolean resetting = false;
    private float weight = 1.0f;
    private CameraTarget current;

    @Override
    public void start(MinecraftClient client, Camera camera) {
        PlayerEntity player = client.player;
        if (player == null) return;

        // Get player's head (eye) position and orientation at the moment of starting.
        Vec3d playerEye = player.getEyePos();
        float playerYaw = player.getYaw();
        float playerPitch = player.getPitch();

        // Capture the current camera target (absolute position) as our starting point.
        CameraTarget camTarget = CameraTarget.fromCamera(camera);
        Vec3d absStart = camTarget.getPosition();
        // Convert the absolute start to a relative offset using the player's current head rotation.
        initialRelativeStart = unrotateVectorByYawPitch(absStart.subtract(playerEye), playerYaw, playerPitch);

        // Compute the absolute end target using the inherited helper.
        // (Renamed to camEndTarget to avoid clashing with the inherited field "endTarget".)
        CameraTarget camEndTarget = getEndTarget(player, targetDistance);
        Vec3d absEnd = camEndTarget.getPosition();
        // Convert the end target to a relative offset.
        initialRelativeEnd = unrotateVectorByYawPitch(absEnd.subtract(playerEye), playerYaw, playerPitch);

        // Compute the control point (in relative coordinates) as the midpoint plus a random displacement.
        Vec3d mid = initialRelativeStart.add(initialRelativeEnd).multiply(0.5);
        double theta = Math.random() * 2 * Math.PI;
        double phi = Math.acos(2 * Math.random() - 1);
        double x = Math.sin(phi) * Math.cos(theta);
        double y = Math.sin(phi) * Math.sin(theta);
        double z = Math.cos(phi);
        Vec3d randomDir = new Vec3d(x, y, z);
        Vec3d offset = randomDir.multiply(controlPointDisplacement);
        controlPointRelative = mid.add(offset);

        progress = 0.0;
        resetting = false;
        // Compute the initial absolute camera position by transforming the relative start back using the player's head rotation.
        Vec3d desiredPos = playerEye.add(rotateVectorByYawPitch(initialRelativeStart, playerYaw, playerPitch));
        current = new CameraTarget(desiredPos, playerYaw, playerPitch);
        weight = 1.0f;
    }

    /**
     * Returns the point on a quadratic Bézier curve defined by points p0, p1, p2 at parameter t.
     */
    private Vec3d quadraticBezier(Vec3d p0, Vec3d p1, Vec3d p2, double t) {
        double oneMinusT = 1 - t;
        return p0.multiply(oneMinusT * oneMinusT)
                .add(p1.multiply(2 * oneMinusT * t))
                .add(p2.multiply(t * t));
    }

    /**
     * Rotates a vector by the given yaw and pitch (in degrees).
     * First rotates around the Y-axis (yaw), then around the X-axis (pitch).
     */
    private Vec3d rotateVectorByYawPitch(Vec3d vec, float yaw, float pitch) {
        double yawRad = Math.toRadians(yaw);
        double cosYaw = Math.cos(yawRad);
        double sinYaw = Math.sin(yawRad);
        double x1 = vec.x * cosYaw - vec.z * sinYaw;
        double z1 = vec.x * sinYaw + vec.z * cosYaw;
        double y1 = vec.y;

        double pitchRad = Math.toRadians(pitch);
        double cosPitch = Math.cos(pitchRad);
        double sinPitch = Math.sin(pitchRad);
        double y2 = y1 * cosPitch - z1 * sinPitch;
        double z2 = y1 * sinPitch + z1 * cosPitch;

        return new Vec3d(x1, y2, z2);
    }

    /**
     * Inversely rotates a vector by the given yaw and pitch.
     * (This undoes the rotation applied by rotateVectorByYawPitch.)
     */
    private Vec3d unrotateVectorByYawPitch(Vec3d vec, float yaw, float pitch) {
        // Inverse of pitch rotation (rotate by -pitch)
        double pitchRad = Math.toRadians(-pitch);
        double cosPitch = Math.cos(pitchRad);
        double sinPitch = Math.sin(pitchRad);
        double y1 = vec.y * cosPitch - vec.z * sinPitch;
        double z1 = vec.y * sinPitch + vec.z * cosPitch;

        // Inverse of yaw rotation (rotate by -yaw)
        double yawRad = Math.toRadians(-yaw);
        double cosYaw = Math.cos(yawRad);
        double sinYaw = Math.sin(yawRad);
        double x1 = vec.x * cosYaw - z1 * sinYaw;
        double z2 = vec.x * sinYaw + z1 * cosYaw;

        return new Vec3d(x1, y1, z2);
    }

    @Override
    public MovementState calculateState(MinecraftClient client, Camera camera) {
        PlayerEntity player = client.player;
        if (player == null) return new MovementState(current, true);

        // Choose the appropriate relative endpoints depending on whether we are resetting.
        Vec3d relStart, relEnd;
        if (!resetting) {
            relStart = initialRelativeStart;
            relEnd = initialRelativeEnd;
        } else {
            relStart = initialRelativeEnd;
            relEnd = initialRelativeStart;
        }

        // --- Update progress using easing and a speed limit based on the straight-line distance ---
        double potentialDelta = (1.0 - progress) * positionEasing;
        double totalDistance = relStart.distanceTo(relEnd);
        double maxMove = positionSpeedLimit * (1.0 / 20.0); // max movement per tick (world units)
        double allowedDelta = totalDistance > 0 ? maxMove / totalDistance : potentialDelta;
        double progressDelta = Math.min(potentialDelta, allowedDelta);
        progress = Math.min(1.0, progress + progressDelta);

        // --- Compute the desired position based solely on the Bézier progress ---
        Vec3d desiredRel = quadraticBezier(relStart, controlPointRelative, relEnd, progress);

        // Get the player's current head (eye) position and rotation.
        Vec3d playerEye = player.getEyePos();
        float currentYaw = player.getYaw();
        float currentPitch = player.getPitch();

        // Transform the relative desired position into world coordinates using the current head rotation.
        Vec3d desiredAbs = playerEye.add(rotateVectorByYawPitch(desiredRel, currentYaw, currentPitch));

        // --- Rotation: determine target rotation ---
        // When moving out (not resetting) in FRONT mode, rotate 180° in yaw and invert the pitch.
        // When resetting, always target the player's current view.
        float targetYaw;
        float targetPitch;
        if (!resetting && this.endTarget == END_TARGET.FRONT) {
            targetYaw = currentYaw + 180f;
            targetPitch = -currentPitch;
        } else {
            targetYaw = currentYaw;
            targetPitch = currentPitch;
        }

        // Apply easing to the rotation.
        float yawDiff = targetYaw - current.getYaw();
        float pitchDiff = targetPitch - current.getPitch();
        while (yawDiff > 180) yawDiff -= 360;
        while (yawDiff < -180) yawDiff += 360;
        float desiredYawSpeed = (float)(yawDiff * rotationEasing);
        float desiredPitchSpeed = (float)(pitchDiff * rotationEasing);
        float maxRotation = (float)(rotationSpeedLimit * (1.0 / 20.0));
        if (Math.abs(desiredYawSpeed) > maxRotation) {
            desiredYawSpeed = Math.signum(desiredYawSpeed) * maxRotation;
        }
        if (Math.abs(desiredPitchSpeed) > maxRotation) {
            desiredPitchSpeed = Math.signum(desiredPitchSpeed) * maxRotation;
        }
        float newYaw = current.getYaw() + desiredYawSpeed;
        float newPitch = current.getPitch() + desiredPitchSpeed;

        // Directly set the camera target using the Bézier–computed position and eased rotation.
        current = new CameraTarget(desiredAbs, newYaw, newPitch);

        // (Optional) Update an alpha value based on the remaining straight–line distance.
        double remaining = desiredRel.distanceTo(relEnd);
        alpha = totalDistance != 0 ? remaining / totalDistance : 0.0;

        // Consider the movement complete if we are resetting and progress has essentially reached 1.
        boolean complete = resetting && progress >= 0.999;
        return new MovementState(current, complete);
    }

    @Override
    public void queueReset(MinecraftClient client, Camera camera) {
        if (client.player == null) return;
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
        return "Bezier (Relative)";
    }

    @Override
    public float getWeight() {
        return weight;
    }

    @Override
    public boolean isComplete() {
        return resetting && progress >= 0.999;
    }
}
