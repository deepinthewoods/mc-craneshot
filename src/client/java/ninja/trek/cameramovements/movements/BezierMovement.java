
package ninja.trek.cameramovements.movements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import ninja.trek.Craneshot;
import ninja.trek.CraneshotClient;
import ninja.trek.CameraController;
import ninja.trek.cameramovements.*;
import ninja.trek.config.MovementSetting;
@CameraMovementType(
        name = "Bezier",
        description = "Moves the camera in a curved line"
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
    private Vec3d canonicalStart;
    private Vec3d canonicalEnd;
    private Vec3d canonicalControl;
    private Vec3d originalCanonicalStart;
    // Store the control stick's original position and orientation
    private Vec3d originalStickPosition;
    private float originalStickYaw;
    private float originalStickPitch;
    private double progress;
    private boolean resetting = false;
    private boolean linearMode = false;
    private boolean distanceChanged = false;
    private float weight = 1.0f;
    private CameraTarget current;
    @Override
    public void start(MinecraftClient client, Camera camera) {
        // Capture the control stick's position and rotation at the start
        Vec3d stickPosition = CameraController.controlStick.getPosition();
        float stickYaw = CameraController.controlStick.getYaw();
        float stickPitch = CameraController.controlStick.getPitch();
        // Save these original parameters
        originalStickPosition = stickPosition;
        originalStickYaw = stickYaw;
        originalStickPitch = stickPitch;
        // Get the camera's current (absolute) position
        Vec3d absStart = CameraTarget.fromCamera(camera).getPosition();
        // Determine the desired end target
        CameraTarget camEndTarget = getEndTarget(client.player, targetDistance);
        Vec3d absEnd = camEndTarget.getPosition();
        // Convert the offsets (relative to the stick) into canonical space
        canonicalStart = unrotateVectorByYawPitch(absStart.subtract(stickPosition), stickYaw, stickPitch);
        canonicalEnd = unrotateVectorByYawPitch(absEnd.subtract(stickPosition), stickYaw, stickPitch);
        // Save the original canonical start for use in the reset
        originalCanonicalStart = canonicalStart;
        // Compute the control point for the Bézier curve
        canonicalControl = generateControlPoint(canonicalStart, canonicalEnd);
        progress = 0.0;
        resetting = false;
        linearMode = false;
        distanceChanged = false;
        // Set the initial camera target
        Vec3d desiredPos = stickPosition.add(rotateVectorByYawPitch(canonicalStart, stickYaw, stickPitch));
        current = new CameraTarget(desiredPos, stickYaw, stickPitch);
        weight = 1.0f;
    }
    @Override
    public MovementState calculateState(MinecraftClient client, Camera camera) {
        // Get current control stick state
        Vec3d stickPosition = CameraController.controlStick.getPosition();
        float stickYaw = CameraController.controlStick.getYaw();
        float stickPitch = CameraController.controlStick.getPitch();
        // Update canonical space endpoints based on current stick position
        if (!resetting) {
            Vec3d currentPos = current.getPosition();
            Vec3d endPos = getEndTarget(client.player, targetDistance).getPosition();
            // Transform current positions to canonical space
            canonicalStart = unrotateVectorByYawPitch(
                    currentPos.subtract(stickPosition),
                    stickYaw,
                    stickPitch
            );
            canonicalEnd = unrotateVectorByYawPitch(
                    endPos.subtract(stickPosition),
                    stickYaw,
                    stickPitch
            );
            // Regenerate control point if distance changed
            if (distanceChanged) {
                canonicalControl = generateControlPoint(canonicalStart, canonicalEnd);
                distanceChanged = false;
            }
        }
        // Determine canonical endpoints for current phase
        Vec3d startCanon = resetting ? canonicalEnd : canonicalStart;
        Vec3d endCanon = resetting ? originalCanonicalStart : canonicalEnd;
        double totalDistance = startCanon.distanceTo(endCanon);
        Vec3d desiredCanonical;
        if (!linearMode) {
            // Bezier movement mode
            double potentialDelta = (1.0 - progress) * positionEasing;
            double maxMove = positionSpeedLimit * (1.0 / 20.0);
            double allowedDelta = totalDistance > 0 ? maxMove / totalDistance : potentialDelta;
            double progressDelta = Math.min(potentialDelta, allowedDelta);
            progress = Math.min(1.0, progress + progressDelta);
            desiredCanonical = quadraticBezier(startCanon, canonicalControl, endCanon, progress);
        } else {
            // Linear movement mode
            Vec3d currentCanon = unrotateVectorByYawPitch(
                    current.getPosition().subtract(stickPosition),
                    stickYaw,
                    stickPitch
            );
            Vec3d delta = endCanon.subtract(currentCanon);
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
            desiredCanonical = currentCanon.add(move);
        }
        // Convert back to world space
        Vec3d desiredAbs = stickPosition.add(
                rotateVectorByYawPitch(desiredCanonical, stickYaw, stickPitch)
        );
        // Calculate target rotation
        float targetYaw = !resetting && this.endTarget == END_TARGET.HEAD_FRONT ?
                stickYaw + 180f : stickYaw;
        float targetPitch = !resetting && this.endTarget == END_TARGET.HEAD_FRONT ?
                -stickPitch : stickPitch;
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
        current = new CameraTarget(desiredAbs, newYaw, newPitch);
        // Update alpha for external systems
        double remaining = desiredCanonical.distanceTo(endCanon);
        alpha = totalDistance != 0 ? remaining / totalDistance : 0.0;
        // Switch to linear mode when out phase completes
        if (!resetting && progress >= 0.999) {
            linearMode = true;
        }
        boolean complete = resetting && progress >= 0.999;
        return new MovementState(current, complete);
    }
    // Rest of the helper methods remain largely unchanged, just updating documentation
    // to reflect that we're using control stick instead of player position
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
    @Override
    public void queueReset(MinecraftClient client, Camera camera) {
        if (!resetting) {
            resetting = true;
            linearMode = false;
            progress = 1.0 - progress;
            canonicalControl = generateControlPoint(canonicalEnd, originalCanonicalStart);
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
    private Vec3d rotateVectorByYawPitch(Vec3d canonical, float yaw, float pitch) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
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
        return right.multiply(canonical.x)
                .add(up.multiply(canonical.y))
                .add(forward.multiply(canonical.z));
    }
    private Vec3d unrotateVectorByYawPitch(Vec3d worldVec, float yaw, float pitch) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
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
    public String getName() {
        return "Bezier";
    }
    @Override
    public float getWeight() {
        return weight;
    }
    @Override
    public boolean isComplete() {
        // Check if we're in reset phase and either:
        // 1. Progress is complete in bezier mode
        // 2. Current position is very close to start position in linear mode
        if (!resetting) return false;
        if (linearMode) {
            return current.getPosition().distanceTo(originalStickPosition) < 0.03;
        } else {
            return progress >= 0.999;
        }
    }
    @Override
    public boolean hasCompletedOutPhase() {
        // We've completed outward phase if:
        // 1. Not in reset phase
        // 2. Either progress is complete in bezier mode or alpha is small in linear mode
        if (resetting) return false;
        if (linearMode) {
            return alpha < 0.1;
        } else {
            return progress >= 0.999;
        }
    }
}