package ninja.trek.cameramovements.movements;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import ninja.trek.CameraController;
import ninja.trek.Craneshot;
import ninja.trek.cameramovements.*;
import ninja.trek.config.MovementSetting;
import ninja.trek.mixin.client.FovAccessor;

@CameraMovementType(
        name = "FreeCamReturn",
        description = "Smoothly returns from free camera mode to the normal camera position"
)
public class FreeCamReturnMovement extends AbstractMovementSettings implements ICameraMovement {
    @MovementSetting(label = "Position Easing", min = 0.01, max = 1.0)
    private double positionEasing = 0.2;

    @MovementSetting(label = "Position Speed Limit", min = 0.1, max = 100.0)
    private double positionSpeedLimit = 5.0;

    @MovementSetting(label = "Rotation Easing", min = 0.01, max = 1.0)
    private double rotationEasing = 0.2;

    @MovementSetting(label = "Rotation Speed Limit", min = 0.1, max = 3600.0)
    private double rotationSpeedLimit = 90.0;

    private CameraTarget start = new CameraTarget();
    private CameraTarget end = new CameraTarget();
    private CameraTarget current = new CameraTarget();
    private boolean isComplete = false;
    private boolean isStarted = false;
    private double completionThreshold = 0.05; // Distance in blocks to consider movement complete
    private END_TARGET originalEndTarget = END_TARGET.HEAD_BACK; // Store the original movement's target type

    @Override
    public void start(MinecraftClient client, Camera camera) {
        // Initialize with the current camera position (free camera position)
        start = CameraTarget.fromCamera(camera);
        current = CameraTarget.fromCamera(camera);
        
        // Store the original end target for proper rotation calculation
        originalEndTarget = CameraController.currentEndTarget;
        
        // Calculate the destination (out position)
        Vec3d targetPos = CameraController.controlStick.getPosition();
        float targetYaw = CameraController.controlStick.getYaw();
        float targetPitch = CameraController.controlStick.getPitch();
        
        // Ensure target rotation is correct for the original movement's end target type
        if (originalEndTarget == END_TARGET.HEAD_FRONT) {
            // For HEAD_FRONT we need to ensure we return to the player's head rotation inverted
            if (client.player != null) {
                targetYaw = (client.player.getYaw() + 180) % 360;
                targetPitch = -client.player.getPitch();
            }
        }
        
        end = new CameraTarget(targetPos, targetYaw, targetPitch, 1.0f);
        
        isComplete = false;
        isStarted = true;
        
        Craneshot.LOGGER.info("FreeCamReturnMovement started: {} -> {}, Original Target: {}", 
            start.getPosition(), end.getPosition(), originalEndTarget);
    }

    @Override
    public MovementState calculateState(MinecraftClient client, Camera camera) {
        if (!isStarted || client.player == null) {
            return new MovementState(current, true);
        }
        
        // For HEAD_FRONT target type, we need to continuously update the target rotation
        // since the player might be moving and changing their orientation
        if (originalEndTarget == END_TARGET.HEAD_FRONT) {
            // Update the end target rotation based on player's current orientation
            float targetYaw = (client.player.getYaw() + 180) % 360;
            float targetPitch = -client.player.getPitch();
            end = new CameraTarget(end.getPosition(), targetYaw, targetPitch, end.getFovMultiplier());
        }
        
        // Position interpolation with speed limit
        Vec3d desired = current.getPosition().lerp(end.getPosition(), positionEasing);
        Vec3d moveVector = desired.subtract(current.getPosition());
        double moveDistance = moveVector.length();
        
        if (moveDistance > 0.01) {
            double maxMove = positionSpeedLimit * (1.0/20.0); // Convert blocks/second to blocks/tick
            if (moveDistance > maxMove) {
                Vec3d limitedMove = moveVector.normalize().multiply(maxMove);
                desired = current.getPosition().add(limitedMove);
            }
        }

        // Rotation interpolation with speed limit
        float targetYaw = end.getYaw();
        float targetPitch = end.getPitch();
        float yawDiff = targetYaw - current.getYaw();
        float pitchDiff = targetPitch - current.getPitch();

        // Normalize angles to [-180, 180]
        while (yawDiff > 180) yawDiff -= 360;
        while (yawDiff < -180) yawDiff += 360;

        // Apply easing to get desired rotation speed
        float desiredYawSpeed = (float)(yawDiff * rotationEasing);
        float desiredPitchSpeed = (float)(pitchDiff * rotationEasing);

        // Apply rotation speed limit
        float maxRotation = (float)(rotationSpeedLimit * (1.0/20.0));
        if (Math.abs(desiredYawSpeed) > maxRotation) {
            desiredYawSpeed = Math.signum(desiredYawSpeed) * maxRotation;
        }
        if (Math.abs(desiredPitchSpeed) > maxRotation) {
            desiredPitchSpeed = Math.signum(desiredPitchSpeed) * maxRotation;
        }

        // Apply final changes
        float newYaw = current.getYaw() + desiredYawSpeed;
        float newPitch = current.getPitch() + desiredPitchSpeed;

        // Update current target
        current = new CameraTarget(desired, newYaw, newPitch, current.getFovMultiplier());

        // Update FOV in game renderer
        if (client.gameRenderer instanceof FovAccessor) {
            ((FovAccessor) client.gameRenderer).setFovModifier((float) current.getFovMultiplier());
        }

        // Check if we've reached the destination
        double remainingDistance = current.getPosition().distanceTo(end.getPosition());
        double yawRemaining = Math.abs(yawDiff);
        double pitchRemaining = Math.abs(pitchDiff);
        
        // Movement is complete when we're close enough to the destination
        boolean positionComplete = remainingDistance < completionThreshold;
        boolean rotationComplete = yawRemaining < 1.0 && pitchRemaining < 1.0;
        isComplete = positionComplete && rotationComplete;
        
        if (isComplete) {
            Craneshot.LOGGER.info("FreeCamReturnMovement completed");
        }
        
        return new MovementState(current, isComplete);
    }

    @Override
    public void queueReset(MinecraftClient client, Camera camera) {
        // This movement doesn't have a reset phase - it's already returning
        isComplete = true;
    }

    @Override
    public void adjustDistance(boolean increase, MinecraftClient client) {
        // Not applicable for this movement
    }

    @Override
    public String getName() {
        return "FreeCamReturn";
    }

    @Override
    public float getWeight() {
        return 1.0f;
    }

    @Override
    public boolean isComplete() {
        return isComplete;
    }

    @Override
    public RaycastType getRaycastType() {
        return RaycastType.NONE; // Assuming we don't need raycast for this movement
    }
}