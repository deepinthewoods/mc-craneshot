package ninja.trek.cameramovements.movements;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import ninja.trek.CameraController;
import ninja.trek.cameramovements.*;
import ninja.trek.config.MovementSetting;
import ninja.trek.mixin.client.FovAccessor;

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

    public CameraTarget start = new CameraTarget();
    private CameraTarget end = new CameraTarget();
    public CameraTarget current = new CameraTarget();
    private Vec3d controlPoint;
    private double progress;
    private boolean resetting = false;
    private boolean linearMode = false;
    private boolean distanceChanged = false;
    private float weight = 1.0f;
    private float baseFov;

    @Override
    public void start(MinecraftClient client, Camera camera) {
        start = CameraTarget.fromCamera(camera);
        current = CameraTarget.fromCamera(camera);

        // Store base FOV
        baseFov = client.options.getFov().getValue().floatValue();

        Vec3d targetPos = calculateTargetPosition(CameraController.controlStick);
        // Initialize the end target with the FOV multiplier from settings
        end = new CameraTarget(targetPos, CameraController.controlStick.getYaw(),
                CameraController.controlStick.getPitch(), fovMultiplier);

        controlPoint = generateControlPoint(start.getPosition(), end.getPosition());
        progress = 0.0;
        resetting = false;
        linearMode = false;
        distanceChanged = false;
        weight = 1.0f;
        alpha = 1;
    }

    private Vec3d calculateTargetPosition(CameraTarget stick) {
        double yaw = Math.toRadians(stick.getYaw());
        double pitch = Math.toRadians(stick.getPitch());
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
                CameraController.controlStick.getPitch(),
                start.getFovMultiplier()
        );

        // Update end target based on controlStick and target distance
        Vec3d targetPos = calculateTargetPosition(CameraController.controlStick);
        end = new CameraTarget(targetPos, CameraController.controlStick.getYaw(),
                CameraController.controlStick.getPitch(), end.getFovMultiplier());

        if (distanceChanged) {
            controlPoint = generateControlPoint(start.getPosition(), end.getPosition());
            distanceChanged = false;
        }

        CameraTarget a = resetting ? end : start;
        CameraTarget b = resetting ? start : end;
        
        // When returning, continuously update the target to follow the player's head position and rotation
        if (resetting && client.player != null) {
            Vec3d playerPos = client.player.getEyePos();
            float playerYaw = client.player.getYaw();
            float playerPitch = client.player.getPitch();
            
            // Update return target to always be the player's current head position and rotation
            b = new CameraTarget(playerPos, playerYaw, playerPitch, b.getFovMultiplier());
            
            // If needed, update the control point to ensure smooth path to player
            if (progress < 0.5) {
                controlPoint = generateControlPoint(current.getPosition(), playerPos);
            }
        }
        
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

        // Calculate target rotation and FOV
        float targetYaw = b.getYaw();
        float targetPitch = b.getPitch();
        float targetFovDelta = (float) b.getFovMultiplier();

        // Apply rotation easing
        float yawError = targetYaw - current.getYaw();
        float pitchError = targetPitch - current.getPitch();
        
        // Normalize angles to [-180, 180]
        while (yawError > 180) yawError -= 360;
        while (yawError < -180) yawError += 360;

        float desiredYawSpeed = (float)(yawError * rotationEasing);
        float desiredPitchSpeed = (float)(pitchError * rotationEasing);
        
        // Apply FOV multiplier transitions with easing
        // Use a custom curve for FOV transitions to make them extra smooth
        // As the FOV approaches its target, we slow down the transition further
        float fovError = (float) (targetFovDelta - current.getFovMultiplier());
        float absFovError = Math.abs(fovError);
        
        // Use a decreasing easing factor as we get closer to the target
        // This avoids abrupt changes at the end of transitions
        float adaptiveFovEasing = (float) (fovEasing * (0.5 + 0.5 * (absFovError / 0.1)));
        if (adaptiveFovEasing > fovEasing) adaptiveFovEasing = (float)fovEasing;
        
        float desiredFovSpeed = fovError * adaptiveFovEasing;

        // Apply speed limits
        float maxRotation = (float)(rotationSpeedLimit * (1.0 / 20.0));
        float maxFovChange = (float)(fovSpeedLimit * (1.0 / 20.0));

        if (Math.abs(desiredYawSpeed) > maxRotation) {
            desiredYawSpeed = Math.signum(desiredYawSpeed) * maxRotation;
        }
        if (Math.abs(desiredPitchSpeed) > maxRotation) {
            desiredPitchSpeed = Math.signum(desiredPitchSpeed) * maxRotation;
        }
        if (Math.abs(desiredFovSpeed) > maxFovChange) {
            desiredFovSpeed = Math.signum(desiredFovSpeed) * maxFovChange;
        }

        float newYaw = current.getYaw() + desiredYawSpeed;
        float newPitch = current.getPitch() + desiredPitchSpeed;
        float newFovDelta = (float) (current.getFovMultiplier() + desiredFovSpeed);

        // Update current target
        current = new CameraTarget(desiredPos, newYaw, newPitch, newFovDelta);

        // Update FOV in game renderer
        if (client.gameRenderer instanceof FovAccessor) {
            ((FovAccessor) client.gameRenderer).setFovModifier((float) current.getFovMultiplier());
        }

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
    public void queueReset(MinecraftClient client, Camera camera) {
        if (!resetting) {
            resetting = true;
            linearMode = false;
            progress = 0.0;
            
            // Always target the player head position/rotation during return phase
            if (client.player != null) {
                // Always return to player's head rotation regardless of END_TARGET
                float playerYaw = client.player.getYaw();
                float playerPitch = client.player.getPitch();
                Vec3d playerPos = client.player.getEyePos();
                
                // Set the target position to player head with proper rotation for return
                // Always reset FOV to normal (1.0) when returning to player view
                end = new CameraTarget(playerPos, playerYaw, playerPitch, 1.0f);
                
                ninja.trek.Craneshot.LOGGER.info("BezierMovement return to player head rotation: pos={}, yaw={}, pitch={}, fov=1.0", 
                    playerPos, playerYaw, playerPitch);
            }
            
            // Update current camera position
            current = CameraTarget.fromCamera(camera);
            
            // Generate a control point for the return path
            // We're always returning to player position now
            if (client.player != null) {
                controlPoint = generateControlPoint(current.getPosition(), client.player.getEyePos());
                
                ninja.trek.Craneshot.LOGGER.info("Returning with control point: {} {} {}", 
                    controlPoint.getX(), controlPoint.getY(), controlPoint.getZ());
            }
            
            // Reset FOV delta when movement ends
            end.setFovMultiplier(1.0f);
        }
    }

    @Override
    public void adjustDistance(boolean increase, MinecraftClient client) {
        if (mouseWheel == SCROLL_WHEEL.DISTANCE) {
            double multiplier = increase ? 1.1 : 0.9;
            targetDistance = Math.max(minDistance, Math.min(maxDistance, targetDistance * multiplier));
            distanceChanged = true;
        } else if (mouseWheel == SCROLL_WHEEL.FOV) {
            adjustFov(increase, client);
        }
    }

    @Override
    public void adjustFov(boolean increase, MinecraftClient client) {
        if (mouseWheel != SCROLL_WHEEL.FOV) return;
        
        // Call the parent implementation to update the target FOV multiplier
        super.adjustFov(increase, client);

        // Don't immediately update the current FOV, let it transition smoothly using easing
        // Only update the end target's FOV as the goal to transition toward
        end.setFovMultiplier(fovMultiplier);
        
        ninja.trek.Craneshot.LOGGER.debug("BezierMovement - Target FOV updated to: {}, current: {}", 
            fovMultiplier, current.getFovMultiplier());
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
        
        // Consider both position/rotation progress and FOV transition
        if (linearMode) {
            // In linear mode, use alpha which tracks position progress
            return alpha < 0.1;
        } else {
            // In Bezier mode, use progress which tracks the curve progress
            return progress >= 0.999;
        }
        
        // We're not checking FOV specifically since this could cause inconsistent
        // behavior with other movement aspects. The FOV will continue to transition
        // smoothly using fovEasing even after the position/rotation has completed.
    }
}