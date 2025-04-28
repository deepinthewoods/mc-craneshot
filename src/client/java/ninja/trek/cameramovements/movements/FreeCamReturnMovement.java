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
    
    // For handling orthographic mode during transitions
    private boolean hasForcedOrthoState = false;
    private boolean forcedOrthoState = false;

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
        
        // Determine orthographic state - either from forced state or current movement settings
        boolean isOrthoModeEnabled;
        if (hasForcedOrthoState) {
            isOrthoModeEnabled = forcedOrthoState;
            Craneshot.LOGGER.info("Using forced ortho state: {}", isOrthoModeEnabled);
        } else {
            isOrthoModeEnabled = getProjection() == PROJECTION.ORTHO;
            Craneshot.LOGGER.info("Using movement settings ortho state: {}", isOrthoModeEnabled);
        }
        
        float orthoFactor = isOrthoModeEnabled ? 1.0f : 0.0f;
        
        // Set orthographic factor in start and current positions
        start.setOrthoFactor(orthoFactor);
        current.setOrthoFactor(orthoFactor);
        
        if (isOrthoModeEnabled) {
            Craneshot.LOGGER.info("FreeCamReturnMovement started with orthographic mode enabled");
        }
        
        // Create end target with ortho factor to match the determined state
        end = new CameraTarget(targetPos, targetYaw, targetPitch, 1.0f, orthoFactor);
        
        isComplete = false;
        isStarted = true;
        
        Craneshot.LOGGER.info("FreeCamReturnMovement started: {} -> {}, Original Target: {}, Ortho: {}", 
            start.getPosition(), end.getPosition(), originalEndTarget, isOrthoModeEnabled);
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
        
        // Now handle orthographic factor transition
        float currentOrthoFactor = current.getOrthoFactor();
        float targetOrthoFactor = end.getOrthoFactor();
        float orthoDiff = targetOrthoFactor - currentOrthoFactor;
        
        // Only interpolate if there's a difference and we're not at the target
        if (Math.abs(orthoDiff) > 0.001f) {
            // Use the same adaptive easing as for FOV
            float adaptiveOrthoEasing = (float) (fovEasing * (0.5 + 0.5 * (Math.abs(orthoDiff) / 0.1)));
            if (adaptiveOrthoEasing > fovEasing) adaptiveOrthoEasing = (float)fovEasing;
            
            float desiredOrthoSpeed = orthoDiff * adaptiveOrthoEasing;
            float maxOrthoChange = (float) (fovSpeedLimit * (1.0f/20.0f)); 
            
            if (Math.abs(desiredOrthoSpeed) > maxOrthoChange) {
                desiredOrthoSpeed = Math.signum(desiredOrthoSpeed) * maxOrthoChange;
            }
            
            float newOrthoFactor = currentOrthoFactor + desiredOrthoSpeed;
            // Keep within valid range
            newOrthoFactor = Math.max(0.0f, Math.min(1.0f, newOrthoFactor));
            
            // Update the current ortho factor
            current.setOrthoFactor(newOrthoFactor);
            
            // Log significant changes
            if (Math.abs(newOrthoFactor - currentOrthoFactor) > 0.05f) {
                Craneshot.LOGGER.debug("Ortho factor transition: {} -> {}", 
                    currentOrthoFactor, newOrthoFactor);
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
        double orthoRemaining = Math.abs(current.getOrthoFactor() - end.getOrthoFactor());
        
        // Movement is complete when we're close enough to the destination
        boolean positionComplete = remainingDistance < completionThreshold;
        boolean rotationComplete = yawRemaining < 1.0 && pitchRemaining < 1.0;
        
        // Use a larger threshold for FOV to ensure smoother final transition
        // This allows the FOV to continue its gradual transition longer
        boolean fovComplete = fovRemaining < 0.05;
        
        // Use similar threshold for ortho factor transition
        boolean orthoComplete = orthoRemaining < 0.05;
        
        // Only mark as complete when all aspects (position, rotation, FOV, and ortho factor) are complete
        isComplete = positionComplete && rotationComplete && fovComplete && orthoComplete;
        
        // Log the current target rotation and actual rotation values
        if (Craneshot.LOGGER.isDebugEnabled()) {
            Craneshot.LOGGER.debug("FreeCamReturnMovement progress - Position: {} / {} blocks, Rotation: {},{} -> {},{}, FOV: {} -> {}, Ortho: {} -> {}",
                String.format("%.2f", remainingDistance),
                String.format("%.2f", completionThreshold),
                String.format("%.1f", current.getYaw()),
                String.format("%.1f", current.getPitch()),
                String.format("%.1f", end.getYaw()),
                String.format("%.1f", end.getPitch()),
                String.format("%.2f", current.getFovMultiplier()),
                String.format("%.2f", end.getFovMultiplier()),
                String.format("%.2f", current.getOrthoFactor()),
                String.format("%.2f", end.getOrthoFactor()));
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
        // Preserve the orthographic factor when updating the end position
        float orthoFactor = end.getOrthoFactor();
        float fovMultiplier = end.getFovMultiplier();
        end = new CameraTarget(position, yaw, pitch, fovMultiplier, orthoFactor);
    }
    
    /**
     * Forces a specific orthographic camera state during the return movement.
     * This is used when there's an inconsistency between the camera target's
     * orthographic factor and the global orthographic mode setting.
     * 
     * @param orthoEnabled Whether orthographic mode should be enabled
     */
    public void setForcedOrthoState(boolean orthoEnabled) {
        this.hasForcedOrthoState = true;
        this.forcedOrthoState = orthoEnabled;
        Craneshot.LOGGER.info("Setting forced ortho state: {}", orthoEnabled);
        
        // Update the target ortho factors immediately to match
        float orthoFactor = orthoEnabled ? 1.0f : 0.0f;
        this.end.setOrthoFactor(orthoFactor);
        
        // If we haven't started yet, also set the start/current ortho factor
        // to create a smooth transition from the current state
        if (!isStarted) {
            this.start.setOrthoFactor(orthoFactor);
            this.current.setOrthoFactor(orthoFactor);
        }
    }
}