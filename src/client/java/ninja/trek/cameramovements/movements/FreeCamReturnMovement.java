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
    
    @MovementSetting(label = "FOV Easing", min = 0.01, max = 1.0)
    private double fovEasing = 0.05; // Slower easing for smoother FOV transition

    @MovementSetting(label = "FOV Speed Limit", min = 0.1, max = 100.0)
    private double fovSpeedLimit = 1.0; // Slower speed limit for smoother transitions

    private CameraTarget start = new CameraTarget();
    private CameraTarget end = new CameraTarget();
    private CameraTarget current = new CameraTarget();
    private boolean isComplete = false;
    private boolean isStarted = false;
    private double completionThreshold = 0.05; // Distance in blocks to consider movement complete
    private END_TARGET originalEndTarget = END_TARGET.HEAD_BACK; // Store the original movement's target type
    
    // Orthographic handling removed

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
        
        // Set initial target rotation based on the original movement's end target type
        // Note: For dynamic targets like HEAD_BACK and HEAD_FRONT, this will be 
        // continuously updated in calculateState() to track the player's current orientation
        if (originalEndTarget == END_TARGET.HEAD_FRONT) {
            // For HEAD_FRONT we need to ensure we return to the player's head rotation inverted
            if (client.player != null) {
                targetYaw = (client.player.getYaw() + 180) % 360;
                targetPitch = -client.player.getPitch();
            }
        } else if (originalEndTarget == END_TARGET.HEAD_BACK) {
            // For HEAD_BACK we use the player's current orientation
            if (client.player != null) {
                targetYaw = client.player.getYaw();
                targetPitch = client.player.getPitch();
            }
        }
        
        // Always return to the default FOV (1.0) when returning to player view
        // The transition will use the fovEasing and fovSpeedLimit settings
        
        // Create end target (orthographic support removed)
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
        Craneshot.LOGGER.info("FreeCamReturnMovement processing");
        // Ensure the keyboard movement flag is reset when in the return phase
        // This prevents any issues if we resume normal movement during return
        ninja.trek.CameraController.hasMovedWithKeyboard = false;
        
        // Continuously update the target rotation based on the player's current orientation
        // This ensures we always return to the current player head position, not just the initial one
        if (originalEndTarget == END_TARGET.HEAD_FRONT) {
            // For HEAD_FRONT target type, invert the player's orientation
            float targetYaw = (client.player.getYaw() + 180) % 360;
            float targetPitch = -client.player.getPitch();
            float oldYaw = end.getYaw();
            float oldPitch = end.getPitch();
            
            // Create new end target while preserving orthographic factor
            float orthoFactor = end.getOrthoFactor();
            end = new CameraTarget(end.getPosition(), targetYaw, targetPitch, end.getFovMultiplier(), orthoFactor);
            
            // Log significant rotation changes (when greater than 1 degree)
            if (Math.abs(targetYaw - oldYaw) > 1.0f || Math.abs(targetPitch - oldPitch) > 1.0f) {
                Craneshot.LOGGER.debug("Updating HEAD_FRONT target rotation: yaw {} -> {}, pitch {} -> {}", 
                    oldYaw, targetYaw, oldPitch, targetPitch);
            }
        } else if (originalEndTarget == END_TARGET.HEAD_BACK) {
            // For HEAD_BACK target type, use the player's current orientation
            float targetYaw = client.player.getYaw();
            float targetPitch = client.player.getPitch();
            float oldYaw = end.getYaw();
            float oldPitch = end.getPitch();
            
            // Create new end target while preserving orthographic factor
            float orthoFactor = end.getOrthoFactor();
            end = new CameraTarget(end.getPosition(), targetYaw, targetPitch, end.getFovMultiplier(), orthoFactor);
            
            // Log significant rotation changes (when greater than 1 degree)
            if (Math.abs(targetYaw - oldYaw) > 1.0f || Math.abs(targetPitch - oldPitch) > 1.0f) {
                Craneshot.LOGGER.debug("Updating HEAD_BACK target rotation: yaw {} -> {}, pitch {} -> {}", 
                    oldYaw, targetYaw, oldPitch, targetPitch);
            }
        } 
        // For VELOCITY and FIXED types, we keep the original end rotation since they're not
        // directly tied to the player's head orientation
        
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

        // FOV interpolation with adaptive easing - slower as we approach the target
        float targetFovMultiplier = end.getFovMultiplier();
        float fovDiff = targetFovMultiplier - current.getFovMultiplier();
        float absFovDiff = Math.abs(fovDiff);
        
        // Calculate an adaptive easing that slows down as we get closer to the target
        float adaptiveFovEasing = (float) (fovEasing * (0.5 + 0.5 * (absFovDiff / 0.1)));
        if (adaptiveFovEasing > fovEasing) adaptiveFovEasing = (float)fovEasing;
        
        float desiredFovSpeed = fovDiff * adaptiveFovEasing;
        float maxFovChange = (float) (fovSpeedLimit * (1.0f/20.0f));
        
        if (Math.abs(desiredFovSpeed) > maxFovChange) {
            desiredFovSpeed = Math.signum(desiredFovSpeed) * maxFovChange;
        }
        
        float newFovMultiplier = current.getFovMultiplier() + desiredFovSpeed;
        
        // Update the position and fov of the current target
        // (we'll update orthoFactor separately below)
        current = new CameraTarget(desired, newYaw, newPitch, newFovMultiplier, current.getOrthoFactor());
        
        // Handle orthographic factor transition using actual movement progress
        float currentOrthoFactor = current.getOrthoFactor();
        float targetOrthoFactor = end.getOrthoFactor();
        
        // Calculate total position progress (0.0 to 1.0) based on position and rotation
        double positionProgress = Math.min(1.0, current.getPosition().distanceTo(end.getPosition()) / 
                                         Math.max(0.1, start.getPosition().distanceTo(end.getPosition())));
                                         
        // Normalize to 0.0-1.0 range where 1.0 means we're at start, 0.0 means we're at destination
        double normalizedProgress = 1.0 - positionProgress;
        
        // Use the normalized movement progress for ortho factor interpolation
        float newOrthoFactor;
        if (normalizedProgress < 0.1) {
            // Start of transition (first 10%) - very gentle curve
            newOrthoFactor = currentOrthoFactor + (targetOrthoFactor - currentOrthoFactor) * 
                            (float)(normalizedProgress * 2.0);
        } else if (normalizedProgress > 0.9) {
            // End of transition (last 10%) - very gentle curve
            float remainingFactor = 1.0f - (float)((normalizedProgress - 0.9) * 5.0);
            newOrthoFactor = targetOrthoFactor - (targetOrthoFactor - currentOrthoFactor) * remainingFactor;
        } else {
            // Middle of transition - linear interpolation
            newOrthoFactor = currentOrthoFactor + (targetOrthoFactor - currentOrthoFactor) * 
                            (float)((normalizedProgress - 0.1) / 0.8);
        }
        
        // Keep within valid range
        newOrthoFactor = Math.max(0.0f, Math.min(1.0f, newOrthoFactor));
        
        // Only apply if there's a significant change
        if (Math.abs(newOrthoFactor - currentOrthoFactor) > 0.001f) {
            // Update the current ortho factor
            current.setOrthoFactor(newOrthoFactor);
            
            // Log significant changes
            if (Math.abs(newOrthoFactor - currentOrthoFactor) > 0.05f) {
                Craneshot.LOGGER.debug("Ortho transition using movement progress: {} -> {}, progress: {}", 
                    currentOrthoFactor, newOrthoFactor, normalizedProgress);
            }
        }

        // Update FOV in game renderer
        if (client.gameRenderer instanceof FovAccessor) {
            ((FovAccessor) client.gameRenderer).setFovModifier(current.getFovMultiplier());
        }

        // Check if we've reached the destination
        double remainingDistance = current.getPosition().distanceTo(end.getPosition());
        double yawRemaining = Math.abs(yawDiff);
        double pitchRemaining = Math.abs(pitchDiff);
        double fovRemaining = Math.abs(fovDiff);
        // Movement is complete when we're close enough to the destination
        boolean positionComplete = remainingDistance < completionThreshold;
        boolean rotationComplete = yawRemaining < 1.0 && pitchRemaining < 1.0;
        
        // Use a larger threshold for FOV to ensure smoother final transition
        // This allows the FOV to continue its gradual transition longer
        boolean fovComplete = fovRemaining < 0.05;
        // Only mark as complete when position, rotation and FOV are complete
        isComplete = positionComplete && rotationComplete && fovComplete;
        
        // Log the current target rotation and actual rotation values
        if (Craneshot.LOGGER.isDebugEnabled()) {
            Craneshot.LOGGER.debug("FreeCamReturnMovement progress - Position: {} / {} blocks, Rotation: {},{} -> {},{}, FOV: {} -> {}",
                String.format("%.2f", remainingDistance),
                String.format("%.2f", completionThreshold),
                String.format("%.1f", current.getYaw()),
                String.format("%.1f", current.getPitch()),
                String.format("%.1f", end.getYaw()),
                String.format("%.1f", end.getPitch()),
                String.format("%.2f", current.getFovMultiplier()),
                String.format("%.2f", end.getFovMultiplier()));
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
    
    /**
     * Updates the target position with default FOV multiplier while preserving orthographic factor.
     * @param position The new position
     * @param yaw The new yaw angle
     * @param pitch The new pitch angle
     */
    private void updateEndPosition(Vec3d position, float yaw, float pitch) {
        float fovMultiplier = end.getFovMultiplier();
        end = new CameraTarget(position, yaw, pitch, fovMultiplier);
    }
    
    /**
     * Forces a specific orthographic camera state during the return movement.
     * This is used when there's an inconsistency between the camera target's
     * orthographic factor and the global orthographic mode setting.
     * 
     * @param orthoEnabled Whether orthographic mode should be enabled
     */
    public void setForcedOrthoState(boolean orthoEnabled) {
        // No-op: orthographic projection removed
    }
}
