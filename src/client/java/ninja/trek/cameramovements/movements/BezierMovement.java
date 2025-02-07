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
 * Moves the camera along a quadratic Bézier curve. The curve’s control points are
 * computed in a fixed local (“canonical”) coordinate system (in which a level, south–facing
 * view corresponds to yaw=0, pitch=0 and forward = (0,0,1)), and on every update the
 * current player yaw/pitch is applied to transform the canonical offset back to world coordinates.
 *
 * (If you see that the motion works when looking flat (yaw=0) but is off when looking up/down
 * or east/west, then it’s likely that the coordinate conversion wasn’t matching Minecraft’s conventions.
 * This version uses conversion functions tuned for Minecraft.)
 */
@CameraMovementType(
        name = "Bezier Movement (Canonical Relative)",
        description = "Moves the camera along a quadratic Bézier curve in a fixed local coordinate system (canonical), then converts to world space using the current head rotation"
)
public class BezierMovement extends AbstractMovementSettings implements ICameraMovement {

    // --- Settings ---
    @MovementSetting(label = "Position Easing", min = 0.01, max = 1.0)
    private double positionEasing = 0.1;

    @MovementSetting(label = "Position Speed Limit", min = 0.1, max = 100.0)
    private double positionSpeedLimit = 10;

    @MovementSetting(label = "Rotation Easing", min = 0.01, max = 1.0)
    private double rotationEasing = 0.1;

    @MovementSetting(label = "Rotation Speed Limit", min = 0.1, max = 3600.0)
    private double rotationSpeedLimit = 500;

    @MovementSetting(label = "Target Distance", min = 1.0, max = 50.0)
    private double targetDistance = 10.0;

    @MovementSetting(label = "Min Distance", min = 1.0, max = 10.0)
    private double minDistance = 2.0;

    @MovementSetting(label = "Max Distance", min = 10.0, max = 50.0)
    private double maxDistance = 20.0;

    @MovementSetting(label = "Control Point Displacement", min = 0.0, max = 30)
    private double controlPointDisplacement = 5;

    // --- New settings for the displacement angle ---
    // Instead of specifying a min and max, we now specify a central angle and a variance.
    @MovementSetting(label = "Displacement Angle", min = -180.0, max = 180.0)
    private double displacementAngle = 0.0;

    @MovementSetting(label = "Displacement Angle Variance", min = 0.0, max = 180.0)
    private double displacementAngleVariance = 0.0;

    // --- Internal fields for the canonical Bézier path ---
    // These vectors are expressed in canonical (local) space, defined so that:
    //   - a level player looking south (yaw=0, pitch=0) has forward = (0,0,1)
    //   - right = (1,0,0) and up = (0,1,0)
    private Vec3d canonicalStart;
    private Vec3d canonicalEnd;
    private Vec3d canonicalControl;
    private double progress;
    private boolean resetting = false;
    private float weight = 1.0f;
    private CameraTarget current;

    @Override
    public void start(MinecraftClient client, Camera camera) {
        PlayerEntity player = client.player;
        if (player == null) return;

        // Get the player's eye (head) position and current rotation.
        Vec3d playerEye = player.getEyePos();
        float playerYaw = player.getYaw();     // In Minecraft, yaw=0 means south.
        float playerPitch = player.getPitch();

        // Capture the current camera start position (world space).
        Vec3d absStart = CameraTarget.fromCamera(camera).getPosition();
        // Determine the absolute end target using our helper.
        CameraTarget camEndTarget = getEndTarget(player, targetDistance);
        Vec3d absEnd = camEndTarget.getPosition();

        // Convert the absolute offsets (relative to the eye) into canonical local space.
        // (The canonical space is defined as if the player were looking south and level.)
        canonicalStart = unrotateVectorByYawPitch(absStart.subtract(playerEye), playerYaw, playerPitch);
        canonicalEnd   = unrotateVectorByYawPitch(absEnd.subtract(playerEye), playerYaw, playerPitch);

        // --- Modified control point computation ---
        // Compute the midpoint between start and end in canonical space.
        Vec3d mid = canonicalStart.add(canonicalEnd).multiply(0.5);
        Vec3d diff = canonicalEnd.subtract(canonicalStart);
        if (diff.lengthSquared() < 1e-6) {
            // If the start and end are nearly identical, simply move upward.
            canonicalControl = mid.add(new Vec3d(0, controlPointDisplacement, 0));
        } else {
            Vec3d tangent = diff.normalize();
            Vec3d worldUp = new Vec3d(0, 1, 0);
            // Project worldUp onto the plane perpendicular to tangent.
            Vec3d projectedUp = worldUp.subtract(tangent.multiply(worldUp.dotProduct(tangent)));
            if (projectedUp.lengthSquared() < 1e-6) {
                // If tangent is parallel to worldUp, use an arbitrary perpendicular vector.
                Vec3d arbitrary = new Vec3d(1, 0, 0);
                projectedUp = arbitrary.subtract(tangent.multiply(arbitrary.dotProduct(tangent)));
            }
            projectedUp = projectedUp.normalize();
            // Ensure the projected vector points upward (i.e. positive Y). I had to reverse this for it to work right.
            if (projectedUp.y > 0) {
                projectedUp = projectedUp.multiply(-1);
            }
            // Choose a random angle based on the configured angle and variance.
            // Math.random() returns a value in [0,1], remap it to [-1,1] then multiply by the variance.
            double randomOffset = (Math.random() * 2 - 1) * displacementAngleVariance;
            double angleDegrees = displacementAngle + randomOffset;
            double angleRadians = Math.toRadians(angleDegrees);
            // Rotate projectedUp about the tangent axis by the random angle.
            // Since projectedUp is perpendicular to tangent, we can use:
            //   rotated = projectedUp*cos(theta) + (tangent cross projectedUp)*sin(theta)
            Vec3d rotatedUp = projectedUp.multiply(Math.cos(angleRadians))
                    .add(tangent.crossProduct(projectedUp).multiply(Math.sin(angleRadians)));
            Vec3d offset = rotatedUp.multiply(controlPointDisplacement);
            canonicalControl = mid.add(offset);
        }

        progress = 0.0;
        resetting = false;

        // Compute the initial absolute camera position by converting canonicalStart back to world space.
        Vec3d desiredPos = playerEye.add(rotateVectorByYawPitch(canonicalStart, playerYaw, playerPitch));
        current = new CameraTarget(desiredPos, playerYaw, playerPitch);
        weight = 1.0f;
    }

    /**
     * Returns the point on a quadratic Bézier curve defined by points p0, p1, p2 at parameter t.
     */
    private Vec3d quadraticBezier(Vec3d p0, Vec3d p1, Vec3d p2, double t) {
        double oneMinusT = 1.0 - t;
        return p0.multiply(oneMinusT * oneMinusT)
                .add(p1.multiply(2 * oneMinusT * t))
                .add(p2.multiply(t * t));
    }

    @Override
    public MovementState calculateState(MinecraftClient client, Camera camera) {
        PlayerEntity player = client.player;
        if (player == null) return new MovementState(current, true);

        // Select canonical endpoints based on whether we are resetting.
        Vec3d startCanon, endCanon;
        if (!resetting) {
            startCanon = canonicalStart;
            endCanon = canonicalEnd;
        } else {
            startCanon = canonicalEnd;
            endCanon = canonicalStart;
        }

        // Update progress using easing and a speed limit.
        double potentialDelta = (1.0 - progress) * positionEasing;
        double totalDistance = startCanon.distanceTo(endCanon);
        double maxMove = positionSpeedLimit * (1.0 / 20.0); // movement per tick (world units)
        double allowedDelta = totalDistance > 0 ? maxMove / totalDistance : potentialDelta;
        double progressDelta = Math.min(potentialDelta, allowedDelta);
        progress = Math.min(1.0, progress + progressDelta);

        // Compute the desired canonical position along the Bézier curve.
        Vec3d desiredCanonical = quadraticBezier(startCanon, canonicalControl, endCanon, progress);

        // Convert the canonical desired position back into world coordinates.
        Vec3d playerEye = player.getEyePos();
        float playerYaw = player.getYaw();
        float playerPitch = player.getPitch();
        Vec3d desiredAbs = playerEye.add(rotateVectorByYawPitch(desiredCanonical, playerYaw, playerPitch));

        // --- Rotation easing ---
        // Determine the target rotation. (If moving forward in FRONT mode, flip yaw/pitch.)
        float targetYaw;
        float targetPitch;
        if (!resetting && this.endTarget == END_TARGET.FRONT) {
            targetYaw = playerYaw + 180f;
            targetPitch = -playerPitch;
        } else {
            targetYaw = playerYaw;
            targetPitch = playerPitch;
        }

        // Easing for rotation.
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

        // Update the camera target.
        current = new CameraTarget(desiredAbs, newYaw, newPitch);

        // Update alpha (if used) based on the remaining distance in canonical space.
        double remaining = desiredCanonical.distanceTo(endCanon);
        alpha = totalDistance != 0 ? remaining / totalDistance : 0.0;

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
        return "Bezier (Canonical Relative)";
    }

    @Override
    public float getWeight() {
        return weight;
    }

    @Override
    public boolean isComplete() {
        return resetting && progress >= 0.999;
    }

    /**
     * Converts a canonical (local) vector into world space.
     * In canonical coordinates a level, south–facing view (yaw=0, pitch=0)
     * has right = (1,0,0), up = (0,1,0) and forward = (0,0,1).
     *
     * Here we build the player’s local axes from the current yaw and pitch.
     * Note that we use -Math.sin(pitchRad) for the forward’s Y component so that
     * the camera will move in the same direction as the head.
     */
    private Vec3d rotateVectorByYawPitch(Vec3d canonical, float playerYaw, float playerPitch) {
        double yawRad   = Math.toRadians(playerYaw);
        double pitchRad = Math.toRadians(playerPitch);

        // Compute forward vector.
        // Using -Math.sin(pitchRad) reverses the pitch contribution relative to our previous version.
        Vec3d forward = new Vec3d(
                -Math.sin(yawRad) * Math.cos(pitchRad),
                -Math.sin(pitchRad), // flipped sign here
                Math.cos(yawRad) * Math.cos(pitchRad)
        );

        // Right vector is independent of pitch.
        Vec3d right = new Vec3d(
                Math.cos(yawRad),
                0,
                Math.sin(yawRad)
        );

        // Up vector computed via cross product.
        Vec3d up = right.crossProduct(forward);

        // Recompose the world offset from the canonical coordinates.
        return right.multiply(canonical.x)
                .add(up.multiply(canonical.y))
                .add(forward.multiply(canonical.z));
    }

    /**
     * Converts a world–space offset (typically relative to the player’s eye) into canonical space.
     * This function is the inverse of rotateVectorByYawPitch().
     */
    private Vec3d unrotateVectorByYawPitch(Vec3d worldVec, float playerYaw, float playerPitch) {
        double yawRad   = Math.toRadians(playerYaw);
        double pitchRad = Math.toRadians(playerPitch);

        Vec3d forward = new Vec3d(
                -Math.sin(yawRad) * Math.cos(pitchRad),
                -Math.sin(pitchRad), // flipped sign here as well
                Math.cos(yawRad) * Math.cos(pitchRad)
        );

        Vec3d right = new Vec3d(
                Math.cos(yawRad),
                0,
                Math.sin(yawRad)
        );

        Vec3d up = right.crossProduct(forward);

        // Project the world vector onto the local axes.
        double xCanonical = worldVec.dotProduct(right);
        double yCanonical = worldVec.dotProduct(up);
        double zCanonical = worldVec.dotProduct(forward);
        return new Vec3d(xCanonical, yCanonical, zCanonical);
    }
}
