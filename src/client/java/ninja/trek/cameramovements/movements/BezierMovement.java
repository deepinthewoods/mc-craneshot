package ninja.trek.cameramovements.movements;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import ninja.trek.Craneshot;
import ninja.trek.CraneshotClient;
import ninja.trek.cameramovements.CameraMovementType;
import ninja.trek.cameramovements.CameraTarget;
import ninja.trek.cameramovements.ICameraMovement;
import ninja.trek.cameramovements.MovementState;
import ninja.trek.cameramovements.AbstractMovementSettings;
import ninja.trek.config.MovementSetting;

/**
 * Moves the camera along a quadratic Bézier curve (in canonical space) and then transforms
 * to world space using the player's head rotation.
 *
 * Modified so that:
 * - While “moving out” (Bézier phase), adjustDistance() does not change the current path.
 * - Once the “out” phase completes, the movement switches to linear mode so that any subsequent
 *   distance (or rotation) changes immediately recalc the canonical endpoints.
 * - When resetting, we exit linear mode and return along a Bézier curve that goes back to the
 *   original absolute camera position.
 *
 * To achieve that, we now store the player's original eye position and head rotation (as well as
 * the original canonical start) and use these for converting canonical coordinates back to world space
 * during the reset.
 */
@CameraMovementType(
        name = "Bezier Movement (Canonical Relative)",
        description = "Moves the camera along a quadratic Bézier curve in canonical space then converts to world space using the player's head rotation. (Modified to fix the return path.)"
)
public class BezierMovement extends AbstractMovementSettings implements ICameraMovement {

    // --- Settings ---
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

    // --- Internal fields for the canonical Bézier path ---
    // These vectors are expressed in canonical (local) space.
    private Vec3d canonicalStart;
    private Vec3d canonicalEnd;
    private Vec3d canonicalControl;
    // Store the original canonical start (i.e. the original camera offset relative to the player)
    private Vec3d originalCanonicalStart;

    // --- Store the player's original eye position and orientation ---
    private Vec3d originalPlayerEye;
    private float originalPlayerYaw;
    private float originalPlayerPitch;

    private double progress;
    private boolean resetting = false;
    private boolean linearMode = false;
    private boolean distanceChanged = false;
    private float weight = 1.0f;
    private CameraTarget current;

    @Override
    public void start(MinecraftClient client, Camera camera) {
        PlayerEntity player = client.player;
        if (player == null) return;

        // Capture the player's eye position and rotation at the start.
        Vec3d playerEye = player.getEyePos();
        float playerYaw = player.getYaw();
        float playerPitch = player.getPitch();

        // Save these original parameters.
        originalPlayerEye = playerEye;
        originalPlayerYaw = playerYaw;
        originalPlayerPitch = playerPitch;

        // Get the camera's current (absolute) position.
        Vec3d absStart = CameraTarget.fromCamera(camera).getPosition();
        // Determine the desired end target.
        CameraTarget camEndTarget = getEndTarget(player, targetDistance);
        Vec3d absEnd = camEndTarget.getPosition();

        // Convert the offsets (relative to the eye) into canonical space.
        canonicalStart = unrotateVectorByYawPitch(absStart.subtract(playerEye), playerYaw, playerPitch);
        canonicalEnd   = unrotateVectorByYawPitch(absEnd.subtract(playerEye), playerYaw, playerPitch);
        // Save the original canonical start for use in the reset.
        originalCanonicalStart = canonicalStart;

        // Compute the control point for the Bézier curve.
        canonicalControl = generateControlPoint(canonicalStart, canonicalEnd);

        progress = 0.0;
        resetting = false;
        linearMode = false;
        distanceChanged = false;

        // Set the initial camera target.
        Vec3d desiredPos = playerEye.add(rotateVectorByYawPitch(canonicalStart, playerYaw, playerPitch));
        current = new CameraTarget(desiredPos, playerYaw, playerPitch);
        weight = 1.0f;
    }

    /**
     * Computes the quadratic Bézier control point between the given canonical start and end.
     */
    private Vec3d generateControlPoint(Vec3d start, Vec3d end) {
        Vec3d mid = start.add(end).multiply(0.5);
        Vec3d diff = end.subtract(start);
        if (diff.lengthSquared() < 1e-6) {
            return mid.add(new Vec3d(0, controlPointDisplacement, 0));
        } else {
            Vec3d tangent = diff.normalize();
            Vec3d worldUp = new Vec3d(0, 1, 0);
            Vec3d projectedUp = worldUp.subtract(tangent.multiply(worldUp.dotProduct(tangent)));
            if (projectedUp.lengthSquared() < 1e-6) {
                Vec3d arbitrary = new Vec3d(1, 0, 0);
                projectedUp = arbitrary.subtract(tangent.multiply(arbitrary.dotProduct(tangent)));
            }
            projectedUp = projectedUp.normalize();
            if (projectedUp.y > 0) {
                projectedUp = projectedUp.multiply(-1);
            }
            double randomOffset = (Math.random() * 2 - 1) * displacementAngleVariance;
            double angleDegrees = displacementAngle + randomOffset;
            double angleRadians = Math.toRadians(angleDegrees);
            Vec3d rotatedUp = projectedUp.multiply(Math.cos(angleRadians))
                    .add(tangent.crossProduct(projectedUp).multiply(Math.sin(angleRadians)));
            Vec3d offset = rotatedUp.multiply(controlPointDisplacement);
            return mid.add(offset);
        }
    }

    /**
     * Returns the point on a quadratic Bézier curve defined by p0, p1, p2 at parameter t.
     */
    private Vec3d quadraticBezier(Vec3d p0, Vec3d p1, Vec3d p2, double t) {
        double oneMinusT = 1.0 - t;
        return p0.multiply(oneMinusT * oneMinusT)
                .add(p1.multiply(2 * oneMinusT * t))
                .add(p2.multiply(t * t));
    }

    @Override
    public MovementState calculateState(MinecraftClient client, Camera camera) {
        if (linearMode) Craneshot.LOGGER.info("linear");
        PlayerEntity player = client.player;
        if (player == null) return new MovementState(current, true);

        // Determine canonical endpoints.
        Vec3d startCanon, endCanon;
        if (!resetting) {
            startCanon = canonicalStart;
            endCanon = canonicalEnd;
        } else {
            // For reset, go from canonicalEnd back to the ORIGINAL canonicalStart.
            startCanon = canonicalEnd;
            endCanon = originalCanonicalStart;
        }

        // Compute the total distance between the endpoints (used later for alpha).
        double totalDistance = startCanon.distanceTo(endCanon);

        Vec3d desiredCanonical;
        if (!linearMode) {
            // --- Bezier movement mode ---
            // Advance the progress along the curve using positionEasing and limiting the delta so that
            // the movement does not exceed positionSpeedLimit per tick.
            double potentialDelta = (1.0 - progress) * positionEasing;
            double maxMove = positionSpeedLimit * (1.0 / 20.0); // maximum allowed movement per tick
            double allowedDelta = totalDistance > 0 ? maxMove / totalDistance : potentialDelta;
            double progressDelta = Math.min(potentialDelta, allowedDelta);
            progress = Math.min(1.0, progress + progressDelta);

            // Get the canonical position along the quadratic Bézier curve.
            desiredCanonical = quadraticBezier(startCanon, canonicalControl, endCanon, progress);
        } else {
            // --- Linear movement mode ---
            // Instead of moving along the Bézier, convert the current camera position into canonical space,
            // then move it directly toward the target endpoint.
            Vec3d playerEye = player.getEyePos();
            float playerYaw = player.getYaw();
            float playerPitch = player.getPitch();
            // Convert current absolute camera position to canonical coordinates.
            Vec3d currentCanon = unrotateVectorByYawPitch(current.getPosition().subtract(playerEye), playerYaw, playerPitch);
            // Compute the difference from the current position to the target.
            Vec3d delta = endCanon.subtract(currentCanon);
            double deltaLength = delta.length();
            double maxMove = positionSpeedLimit * (1.0 / 20.0); // maximum movement per tick

            // Calculate the movement step: scale the delta by positionEasing, but clamp its length to maxMove.
            Vec3d move;
            if (deltaLength > 0) {
                move = delta.multiply(positionEasing);
                if (move.length() > maxMove) {
                    move = move.normalize().multiply(maxMove);
                }
            } else {
                move = Vec3d.ZERO;
            }
            // The new canonical target is the current position plus the clamped move vector.
            desiredCanonical = currentCanon.add(move);
        }

        // Convert the desired canonical position back to absolute world space using the player's current eye position and rotation.
        Vec3d conversionEye = player.getEyePos();
        float conversionYaw = player.getYaw();
        float conversionPitch = player.getPitch();
        Vec3d desiredAbs = conversionEye.add(rotateVectorByYawPitch(desiredCanonical, conversionYaw, conversionPitch));

        // --- Rotation easing (remains the same) ---
        float targetYaw, targetPitch;
        if (!resetting && this.endTarget == END_TARGET.FRONT) {
            targetYaw = conversionYaw + 180f;
            targetPitch = -conversionPitch;
        } else {
            targetYaw = conversionYaw;
            targetPitch = conversionPitch;
        }
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

        current = new CameraTarget(desiredAbs, newYaw, newPitch);

        // Compute a normalized "alpha" value (fraction remaining) for use by other systems if needed.
        double remaining = desiredCanonical.distanceTo(endCanon);
        alpha = totalDistance != 0 ? remaining / totalDistance : 0.0;

        // Once the out phase completes, switch to linear mode and recalc canonical endpoints.
        if (!resetting && progress >= 0.999) {
            linearMode = true;
            Vec3d curEye = player.getEyePos();
            float curYaw = player.getYaw();
            float curPitch = player.getPitch();
            canonicalStart = unrotateVectorByYawPitch(current.getPosition().subtract(curEye), curYaw, curPitch);
            canonicalEnd = unrotateVectorByYawPitch(getEndTarget(player, targetDistance)
                    .getPosition().subtract(curEye), curYaw, curPitch);
        }

        boolean complete = resetting && progress >= 0.999;
        return new MovementState(current, complete);
    }

    @Override
    public void queueReset(MinecraftClient client, Camera camera) {
        if (client.player == null) return;
        if (!resetting) {
            resetting = true;
            linearMode = false;
            progress = 1.0 - progress;
            canonicalControl = generateControlPoint(canonicalEnd, originalCanonicalStart);

            if (CraneshotClient.CAMERA_CONTROLLER.getMovementManager() != null) {
                CraneshotClient.CAMERA_CONTROLLER.getMovementManager().resetMovement(this);
            }
        }
    }



    @Override
    public void adjustDistance(boolean increase) {
        double multiplier = increase ? 1.1 : 0.9;
        targetDistance = Math.max(minDistance, Math.min(maxDistance, targetDistance * multiplier));
        if (linearMode) {
            distanceChanged = true;
        }
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
     * Converts a canonical (local) vector into world space using the given yaw and pitch.
     * (In canonical space a level, south–facing view has forward = (0,0,1).)
     */
    private Vec3d rotateVectorByYawPitch(Vec3d canonical, float playerYaw, float playerPitch) {
        double yawRad   = Math.toRadians(playerYaw);
        double pitchRad = Math.toRadians(playerPitch);

        // Compute forward vector.
        Vec3d forward = new Vec3d(
                -Math.sin(yawRad) * Math.cos(pitchRad),
                -Math.sin(pitchRad),
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

        return right.multiply(canonical.x)
                .add(up.multiply(canonical.y))
                .add(forward.multiply(canonical.z));
    }

    /**
     * Converts a world–space offset (typically relative to the player's eye) into canonical space.
     * This is the inverse of rotateVectorByYawPitch().
     */
    private Vec3d unrotateVectorByYawPitch(Vec3d worldVec, float playerYaw, float playerPitch) {
        double yawRad   = Math.toRadians(playerYaw);
        double pitchRad = Math.toRadians(playerPitch);

        Vec3d forward = new Vec3d(
                -Math.sin(yawRad) * Math.cos(pitchRad),
                -Math.sin(pitchRad),
                Math.cos(yawRad) * Math.cos(pitchRad)
        );

        Vec3d right = new Vec3d(
                Math.cos(yawRad),
                0,
                Math.sin(yawRad)
        );

        Vec3d up = right.crossProduct(forward);

        double xCanonical = worldVec.dotProduct(right);
        double yCanonical = worldVec.dotProduct(up);
        double zCanonical = worldVec.dotProduct(forward);
        return new Vec3d(xCanonical, yCanonical, zCanonical);
    }

    @Override
    public boolean hasCompletedOutPhase() {
        return !resetting && progress >= 0.999;
    }
}
