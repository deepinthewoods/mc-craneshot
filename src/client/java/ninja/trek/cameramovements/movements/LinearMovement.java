package ninja.trek.cameramovements.movements;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import ninja.trek.CameraController;
import ninja.trek.Craneshot;
import ninja.trek.CraneshotClient;
import ninja.trek.cameramovements.*;
import ninja.trek.config.MovementSetting;
import ninja.trek.mixin.client.FovAccessor;

@CameraMovementType(
        name = "Linear",
        description = "Moves the camera along a line"
)
public class LinearMovement extends AbstractMovementSettings implements ICameraMovement {
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

    public CameraTarget start = new CameraTarget();
    public CameraTarget end = new CameraTarget();
    public CameraTarget current = new CameraTarget();
    private boolean resetting = false;
    private float weight = 1.0f;
    private double baseFov;
    // Final interpolation state
    private boolean finalInterpActive = false;
    private double finalInterpT = 0.0;
    private Vec3d finalInterpStart = null;

    public void start(MinecraftClient client, Camera camera) {
        // Initialize with camera's current state
        start = CameraTarget.fromCamera(camera);
        current = CameraTarget.fromCamera(camera);
        // Store base FOV and set initial FOV delta to 0
        baseFov = client.options.getFov().getValue().doubleValue();
        start.setFovMultiplier(1.0f);  // Start at normal FOV
        current.setFovMultiplier(1.0f); // Start at normal FOV
        // Set initial ortho factor based on projection setting
        start.setOrthoFactor(0.0f);  // Start with perspective (non-ortho)
        current.setOrthoFactor(0.0f); // Start with perspective (non-ortho)
        // Calculate end target based on controlStick
        Vec3d targetPos = calculateTargetPosition(CameraController.controlStick);
        // Use the FOV multiplier from settings for the end target
        end = new CameraTarget(targetPos, CameraController.controlStick.getYaw(),
                CameraController.controlStick.getPitch() + pitchOffset, fovMultiplier);
        // Set ortho factor for end target based on projection setting
        if (projection == PROJECTION.ORTHO) {
            end.setOrthoFactor(1.0f); // Target full orthographic
            ninja.trek.Craneshot.LOGGER.info("LinearMovement started with ORTHO projection mode");
        } else {
            end.setOrthoFactor(0.0f); // Stay in perspective mode
            ninja.trek.Craneshot.LOGGER.info("LinearMovement started with PERSPECTIVE projection mode");
        }
        resetting = false;
        weight = 1.0f;
        // Reset final interpolation state
        finalInterpActive = false;
        finalInterpT = 0.0;
        finalInterpStart = null;
    }


    private Vec3d calculateTargetPosition(CameraTarget stick) {
        double yaw = Math.toRadians(stick.getYaw());
        double pitch = Math.toRadians(stick.getPitch() + pitchOffset);
        // Calculate offset based on target distance
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
        CameraController.controlStick.getPitch() + pitchOffset,
        start.getFovMultiplier(),
        start.getOrthoFactor() // Preserve ortho factor
        );

        // Update end target based on controlStick and target distance
        Vec3d targetPos = calculateTargetPosition(CameraController.controlStick);
        end = new CameraTarget(targetPos, CameraController.controlStick.getYaw(),
        CameraController.controlStick.getPitch() + pitchOffset, 
        end.getFovMultiplier(),
        end.getOrthoFactor()); // Preserve ortho factor

        CameraTarget a = resetting ? end : start;
        CameraTarget b = resetting ? start : end;
        
        // When returning, continuously update the target to follow the player's head position and rotation
        if (resetting && client.player != null) {
            Vec3d playerPos = client.player.getEyePos();
            float playerYaw = client.player.getYaw();
            float playerPitch = client.player.getPitch();
            
            // Update return target to always be the player's current head position and rotation
            // But preserve the orthoFactor from the existing target
            float preservedOrtho = b.getOrthoFactor();
            b = new CameraTarget(playerPos, playerYaw, playerPitch, b.getFovMultiplier(), preservedOrtho);
            
            // Debug logging of position updates
            if (Math.random() < 0.001) { // Very rarely log to avoid spam
                ninja.trek.Craneshot.LOGGER.info("LinearMovement updating return target position - preserving orthoFactor={}", 
                    preservedOrtho);
            }
        }

        // Position interpolation
        // If within final threshold, perform time-based linear interpolation directly to the target
        double distanceToTarget = current.getPosition().distanceTo(b.getPosition());
        if (resetting && !finalInterpActive && distanceToTarget <= AbstractMovementSettings.FINAL_INTERP_DISTANCE_THRESHOLD) {
            finalInterpActive = true;
            finalInterpT = 0.0;
            finalInterpStart = current.getPosition();
        }

        Vec3d desired;
        if (finalInterpActive) {
            double step = 1.0 / (AbstractMovementSettings.FINAL_INTERP_TIME_SECONDS * 20.0);
            finalInterpT = Math.min(1.0, finalInterpT + step);
            desired = finalInterpStart.lerp(b.getPosition(), finalInterpT);
        } else {
            // Use a distance-based adaptive easing that increases as we get closer to the target
            // This helps ensure the camera actually reaches the target position
            double adaptiveEasing;
            if (resetting) {
                if (distanceToTarget < 1.0) {
                    adaptiveEasing = Math.max(positionEasing, positionEasing * (2.0 - distanceToTarget));
                } else {
                    adaptiveEasing = positionEasing;
                }
                if (Math.random() < 0.005 && distanceToTarget < 2.0) {
                    ninja.trek.Craneshot.LOGGER.info("LinearMovement return phase - distance: {}, adaptive easing: {}",
                        String.format("%.3f", distanceToTarget),
                        String.format("%.3f", adaptiveEasing));
                }
            } else {
                adaptiveEasing = positionEasing;
            }

            // Calculate the desired position using adaptive easing with speed limit
            desired = current.getPosition().lerp(b.getPosition(), adaptiveEasing);
            Vec3d moveVector = desired.subtract(current.getPosition());
            double moveDistance = moveVector.length();
            if (moveDistance > 0.01) {
                double maxMove = positionSpeedLimit * (1.0/20.0); // Convert blocks/second to blocks/tick
                if (moveDistance > maxMove) {
                    Vec3d limitedMove = moveVector.normalize().multiply(maxMove);
                    desired = current.getPosition().add(limitedMove);
                }
            }
        }

        // Rotation interpolation with speed limit
        float targetYaw = b.getYaw();
        float targetPitch = b.getPitch();
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

        // FOV interpolation with adaptive easing - slower as we approach the target
        float targetFovDelta = b.getFovMultiplier();
        float fovDiff = targetFovDelta - current.getFovMultiplier();
        float absFovDiff = Math.abs(fovDiff);
        
        // Calculate an adaptive easing that slows down as we get closer to the target
        float adaptiveFovEasing = (float) (fovEasing * (0.5 + 0.5 * (absFovDiff / 0.1)));
        if (adaptiveFovEasing > fovEasing) adaptiveFovEasing = (float)fovEasing;
        
        float desiredFovSpeed = fovDiff * adaptiveFovEasing;
        float maxFovChange = (float) (fovSpeedLimit * (1.0f/20.0f));

        if (Math.abs(desiredFovSpeed) > maxFovChange) {
            desiredFovSpeed = Math.signum(desiredFovSpeed) * maxFovChange;
        }
        
        // Orthographic projection factor interpolation
        float targetOrthoFactor = b.getOrthoFactor();
        float orthoDiff = targetOrthoFactor - current.getOrthoFactor();
        
        // Use similar easing for ortho projection transition
        // We want projection changes to be fairly smooth
        float orthoEasing = (float) fovEasing; // Reuse FOV easing value for consistency
        float desiredOrthoSpeed; // Declare the variable here
        
        // Make the return transition (to perspective) as smooth as the out transition
        // Apply special case if we're returning to player view
        if (resetting) {
            // During the initial phase of reset (first ~20% of movement), maintain the current ortho factor
            // This prevents the immediate jump to perspective
            float positionProgress = (float) (current.getPosition().distanceTo(start.getPosition()) / 
                                             Math.max(0.1f, start.getPosition().distanceTo(end.getPosition())));
                                             
            // Normalize to 0.0-1.0 range where 1.0 means at start, 0.0 means at end
            positionProgress = 1.0f - positionProgress;
            
            // Only begin transitioning ortho factor after we've moved a bit
            if (positionProgress < 0.2f) {
                // In the first 20% of movement, maintain the initial ortho factor
                // This means setting desiredOrthoSpeed to 0
                desiredOrthoSpeed = 0.0f;
                
                if (Math.random() < 0.01) { // Log occasionally
                    ninja.trek.Craneshot.LOGGER.debug("Initial return phase - preserving original orthoFactor={}", 
                        current.getOrthoFactor());
                }
            } else {
                // After 20% movement, start a slow transition to perspective
                // Remap progress to 0-1 range for the remaining 80% of movement
                float transitionProgress = (positionProgress - 0.2f) / 0.8f;
                
                // Use a special returnEasing that's slower for more visual smoothness
                float returnPhaseOrthoEasing = (float) (fovEasing * 0.5f * transitionProgress);
                orthoDiff = -current.getOrthoFactor(); // Target 0.0 (perspective)
                desiredOrthoSpeed = orthoDiff * returnPhaseOrthoEasing;
                
                if (Math.random() < 0.01) { // Log occasionally
                    ninja.trek.Craneshot.LOGGER.debug("Return transition - orthoFactor={}, progress={}, easing={}", 
                        current.getOrthoFactor(), positionProgress, returnPhaseOrthoEasing);
                }
            }
        } else {
            // Regular ortho easing during out phase
            desiredOrthoSpeed = orthoDiff * orthoEasing;
        }
        
        if (Math.random() < 0.01) { // Log occasionally
            ninja.trek.Craneshot.LOGGER.debug("Ortho interpolation: current={}, target={}, diff={}, desiredSpeed={}, resetting={}",
                                               current.getOrthoFactor(), targetOrthoFactor, orthoDiff, desiredOrthoSpeed, resetting);
        }
        
        // Apply speed limit for ortho changes similar to FOV
        if (Math.abs(desiredOrthoSpeed) > maxFovChange) {
            desiredOrthoSpeed = Math.signum(desiredOrthoSpeed) * maxFovChange;
        }

        // Apply final changes
        float newYaw = current.getYaw() + desiredYawSpeed;
        float newPitch = current.getPitch() + desiredPitchSpeed;
        float newFovDelta = current.getFovMultiplier() + desiredFovSpeed;
        float newOrthoFactor = Math.max(0.0f, Math.min(1.0f, current.getOrthoFactor() + desiredOrthoSpeed));

        // Update current target with all new values
        current = new CameraTarget(desired, newYaw, newPitch, newFovDelta, newOrthoFactor);

        // Update FOV in game renderer
        if (client.gameRenderer instanceof FovAccessor) {
            ((FovAccessor) client.gameRenderer).setFovModifier(current.getFovMultiplier());
        }

        // Calculate progress for blending
        alpha = current.getPosition().distanceTo(b.getPosition()) /
                a.getPosition().distanceTo(b.getPosition());

        // Log final approach distances during return phase
        if (resetting && current.getPosition().distanceTo(b.getPosition()) < 0.05) {
            ninja.trek.Craneshot.LOGGER.info("LinearMovement final approach - distance to target: {}, alpha: {}",
                String.format("%.5f", current.getPosition().distanceTo(b.getPosition())),
                String.format("%.5f", alpha));
        }

        boolean complete = resetting && current.getPosition().distanceTo(b.getPosition()) < 0.03;
        return new MovementState(current, complete);
    }

    @Override
    public void queueReset(MinecraftClient client, Camera camera) {
        if (client.player == null) return;
        resetting = true;
        // Reset final interpolation state when entering reset
        finalInterpActive = false;
        finalInterpT = 0.0;
        finalInterpStart = null;
        
        // Always target the player head position/rotation during return phase
        if (client.player != null) {
            // Always return to player's head rotation regardless of END_TARGET
            float playerYaw = client.player.getYaw();
            float playerPitch = client.player.getPitch();
            
            // Set the target position to player head and proper rotation for return
            Vec3d playerPos = client.player.getEyePos();
            
            // Get the current orthographic factor
            float currentOrtho = current.getOrthoFactor();
            
            // First preserve the current orthographic factor in end target
            // This is crucial for a smooth transition
            end = new CameraTarget(playerPos, playerYaw, playerPitch, 1.0f, currentOrtho);
            
            // Log this important transition
            ninja.trek.Craneshot.LOGGER.info("LinearMovement return transition starting with orthoFactor={}", currentOrtho);
            
            // Create a new temporary start point that matches current state exactly
            // This ensures our interpolation starts from exactly where we are
            start = new CameraTarget(
                current.getPosition(),
                current.getYaw(),
                current.getPitch(),
                current.getFovMultiplier(),
                current.getOrthoFactor()
            );
            
            ninja.trek.Craneshot.LOGGER.debug("LinearMovement return - current={}, target={}", 
                current.getOrthoFactor(), end.getOrthoFactor());
        }
        
        // For free camera, also update the current target's position to ensure smooth transition
        if (current.getPosition().distanceTo(camera.getPos()) > 0.1) {
            current = CameraTarget.fromCamera(camera);
        }
        
        // Reset FOV delta when movement ends
        end.setFovMultiplier(1.0f);
    }

    @Override
    public void adjustDistance(boolean increase, MinecraftClient client) {
        if (mouseWheel == SCROLL_WHEEL.DISTANCE) {
            double multiplier = increase ? 1.1 : 0.9;
            targetDistance = Math.max(minDistance, Math.min(maxDistance, targetDistance * multiplier));
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
        
        ninja.trek.Craneshot.LOGGER.debug("LinearMovement - Target FOV updated to: {}, current: {}", 
            fovMultiplier, current.getFovMultiplier());
    }

    @Override
    public String getName() {
        return "Linear";
    }

    @Override
    public float getWeight() {
        return weight;
    }

    @Override
    public boolean isComplete() {
        // Consider the movement complete when:
        // 1. We're in the resetting phase
        // 2. We're very close to the target position (player's head)
        // 3. The FOV has nearly returned to normal
        if (resetting) {
            // Using end position (player position) as the target
            double positionDistance = current.getPosition().distanceTo(end.getPosition());
            float fovDifference = Math.abs(current.getFovMultiplier() - 1.0f);
            float orthoDifference = current.getOrthoFactor(); // Distance to 0.0
            
            // Log completion progress for debugging
            if (Math.random() < 0.01) { // Only log occasionally
                ninja.trek.Craneshot.LOGGER.info("LinearMovement completion check - pos distance: {}, fov diff: {}, ortho: {}",
                    String.format("%.3f", positionDistance),
                    String.format("%.3f", fovDifference),
                    String.format("%.3f", orthoDifference));
            }
            
            // Much stricter requirements to ensure movement completes fully
            // Position must be very close to destination
            boolean positionComplete = positionDistance < 0.05;
            boolean fovComplete = fovDifference < 0.1f;
            
            // We don't require ortho to be complete for the movement to end
            // as that will continue to blend after the movement
            return positionComplete && fovComplete;
        }
        return false;
    }

    @Override
    public boolean hasCompletedOutPhase() {
        // Consider movement complete when the position has nearly reached its target
        // The FOV will continue to transition smoothly using fovEasing even after
        // the out phase is considered complete
        return !resetting && alpha < .02;
    }
}
