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

    @MovementSetting(label = "Position Speed Limit", min = 0.1, max = 200.0)
    private double positionSpeedLimit = 10;

    @MovementSetting(label = "Rotation Easing", min = 0.01, max = 1.0)
    private double rotationEasing = 0.1;

    @MovementSetting(label = "Rotation Speed Limit", min = 0.1, max = 1000)
    private double rotationSpeedLimit = 500;

    @MovementSetting(label = "Target Distance", min = 1.0, max = 50.0)
    private double targetDistance = 10.0;

    @MovementSetting(label = "Min Distance", min = 0.0, max = 10.0)
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
    // Prevent orthoFactor from dipping during OUT phase when mouse input retargets end
    private float orthoOutLock = -1.0f;
    // Final interpolation state
    private boolean finalInterpActive = false;
    private double finalInterpT = 0.0;
    private Vec3d finalInterpStart = null;

    // Jitter suppression state
    private float lastTargetYaw = 0f;
    private float lastTargetPitch = 0f;
    private float lastYawError = 0f;
    private float lastPitchError = 0f;
    private Vec3d lastPlayerEyePos = null;
    private boolean jitterStateInit = false;

    @Override
    public void start(MinecraftClient client, Camera camera) {
        start = CameraTarget.fromCamera(camera);
        current = CameraTarget.fromCamera(camera);

        // Store base FOV
        baseFov = client.options.getFov().getValue().floatValue();
        
        // Orthographic handling removed

        Vec3d targetPos = calculateTargetPosition(CameraController.controlStick);
        // Initialize the end target with the FOV multiplier from settings
        end = new CameraTarget(targetPos, CameraController.controlStick.getYaw(),
                CameraController.controlStick.getPitch(), fovMultiplier);
                
        // Orthographic handling removed

        controlPoint = generateControlPoint(start.getPosition(), end.getPosition());
        progress = 0.0;
        resetting = false;
        linearMode = false;
        distanceChanged = false;
        weight = 1.0f;
        alpha = 1;
        // Orthographic handling removed
        // Reset final interpolation state
        finalInterpActive = false;
        finalInterpT = 0.0;
        finalInterpStart = null;

        // Reset jitter suppression tracking
        jitterStateInit = false;
        lastPlayerEyePos = null;
        lastTargetYaw = current.getYaw();
        lastTargetPitch = current.getPitch();
        lastYawError = 0f;
        lastPitchError = 0f;
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
    public MovementState calculateState(MinecraftClient client, Camera camera, float deltaSeconds) {
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
                CameraController.controlStick.getPitch(), 
                end.getFovMultiplier(),
                end.getOrthoFactor()); // Preserve ortho factor

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
            // Update the return target
            b = new CameraTarget(playerPos, playerYaw, playerPitch, b.getFovMultiplier());
            
            // If needed, update the control point to ensure smooth path to player
            if (progress < 0.5) {
                controlPoint = generateControlPoint(current.getPosition(), playerPos);
            }
        }
        
        Vec3d desiredPos;

        // Determine if we should switch to final time-based interpolation
        double remainingDistanceForFinal = current.getPosition().distanceTo(b.getPosition());
        if (resetting && !finalInterpActive && remainingDistanceForFinal <= AbstractMovementSettings.FINAL_INTERP_DISTANCE_THRESHOLD) {
            finalInterpActive = true;
            finalInterpT = 0.0;
            finalInterpStart = current.getPosition();
        }

        if (finalInterpActive) {
            double step = deltaSeconds / (AbstractMovementSettings.FINAL_INTERP_TIME_SECONDS);
            finalInterpT = Math.min(1.0, finalInterpT + step);
            desiredPos = finalInterpStart.lerp(b.getPosition(), finalInterpT);
        } else if (!linearMode) {
            // Bezier movement mode
            double potentialDelta;
            
            if (resetting && progress > 0.8) {
                // When returning and progress is high (near completion), accelerate to ensure we reach the end
                // This helps avoid stopping short of the target position
                potentialDelta = (1.0 - progress) * positionEasing * 1.5; // Use higher multiplier
                // logging removed
            } else {
                // Standard easing for normal progress
                potentialDelta = (1.0 - progress) * positionEasing;
            }
            
            double totalDistance = a.getPosition().distanceTo(b.getPosition());
            double maxMove = positionSpeedLimit * (deltaSeconds);
            double allowedDelta = totalDistance > 0 ? maxMove / totalDistance : potentialDelta;
            double progressDelta = Math.min(potentialDelta, allowedDelta);
            
            // When very close to completion during reset, use larger steps
            if (resetting && progress > 0.95) {
                // Ensure we reach the final position by using larger steps near the end
                progressDelta = Math.max(progressDelta, 0.01);
            }
            
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
            double maxMove = positionSpeedLimit * (deltaSeconds);
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

        // Jitter suppression: when fully out (linear mode) and player is moving,
        // ignore one-frame micro corrections caused by tiny oscillations.
        if (!resetting) {
            boolean fullyOut = linearMode || progress >= 0.999;
            if (fullyOut && client.player != null) {
                Vec3d eye = client.player.getEyePos();
                double playerMove = 0.0;
                if (lastPlayerEyePos != null) {
                    playerMove = eye.distanceTo(lastPlayerEyePos);
                }
                // Thresholds tuned to suppress sub-degree jitter while running
                final float ANGLE_EPS = 0.7f;      // degrees
                final float TARGET_EPS = 0.7f;     // degrees
                final double MOVE_EPS = 0.03;      // blocks per tick

                // Angle delta for target (normalize to [-180,180])
                float targetYawDelta = targetYaw - lastTargetYaw;
                while (targetYawDelta > 180) targetYawDelta -= 360;
                while (targetYawDelta < -180) targetYawDelta += 360;
                float targetPitchDelta = targetPitch - lastTargetPitch;
                while (targetPitchDelta > 180) targetPitchDelta -= 360;
                while (targetPitchDelta < -180) targetPitchDelta += 360;

                boolean smallYawJitter = Math.abs(yawError) < ANGLE_EPS && Math.abs(lastYawError) < ANGLE_EPS && Math.abs(targetYawDelta) < TARGET_EPS;
                boolean smallPitchJitter = Math.abs(pitchError) < ANGLE_EPS && Math.abs(lastPitchError) < ANGLE_EPS && Math.abs(targetPitchDelta) < TARGET_EPS;
                boolean playerMoving = playerMove > MOVE_EPS;

                if (playerMoving && (smallYawJitter || (lastYawError * yawError < 0 && Math.abs(yawError) < ANGLE_EPS))) {
                    desiredYawSpeed = 0f;
                }
                if (playerMoving && (smallPitchJitter || (lastPitchError * pitchError < 0 && Math.abs(pitchError) < ANGLE_EPS))) {
                    desiredPitchSpeed = 0f;
                }

                // Update jitter tracking state
                lastPlayerEyePos = eye;
                jitterStateInit = true;
            }
        }
        
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
        
        // Handle orthographic projection with smooth transitions in both directions
        float calculatedOrthoTarget;
        if (resetting) {
            // Unlock ortho when returning so it can smoothly blend back to perspective
            orthoOutLock = -1.0f;
            // During return phase, preserve the end orthographic factor
            // We'll still set it to 0.0 for the target, but we want a slow transition
            calculatedOrthoTarget = 0.0f;
            
            // Use cubic easing to make the transition more visually pleasing
            float t = (float)progress;
            float easeOutCubic = 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);
            
            // Only actually modify the orthoFactor once we're a bit into the transition
            // This helps prevent immediate jumping to perspective
            if (t > 0.15f) {
                // Slow down the transition to perspective
                float transitionFactor = (t - 0.15f) / 0.85f; // Remap to 0-1 range
                float progressBasedOrtho = end.getOrthoFactor() * (1.0f - transitionFactor * easeOutCubic);
                current.setOrthoFactor(progressBasedOrtho);
            } else {
                // During the beginning of the transition, keep our existing ortho factor
                // Note: This is important to prevent the initial jump to perspective
                // logging removed
            }
        } else {
            // During out phase, use the projection setting to determine target
            calculatedOrthoTarget = (projection == PROJECTION.ORTHO ? 1.0f : 0.0f);
            
            // Use actual movement progress value for ortho transitions
            float computedOutPhaseOrtho;
            if (!linearMode) {                
                // In bezier mode, use the curve progress directly (smooth transitions)
                computedOutPhaseOrtho = (float)progress * calculatedOrthoTarget;
            } else {
                // During out phase in linear mode
                computedOutPhaseOrtho = (1.0f - (float)alpha) * calculatedOrthoTarget;
            }
            // Lock ortho during OUT phase for ORTHO projection so it never dips on mouse move
            if (projection == PROJECTION.ORTHO) {
                float baseLock = (orthoOutLock < 0.0f) ? 0.0f : orthoOutLock;
                float locked = Math.max(baseLock, computedOutPhaseOrtho);
                orthoOutLock = locked;
                current.setOrthoFactor(locked);
            } else {
                current.setOrthoFactor(computedOutPhaseOrtho);
            }
        }
        
        // logging removed
        
        // logging removed

        // Apply speed limits
        float maxRotation = (float)(rotationSpeedLimit * (deltaSeconds));
        float maxFovChange = (float)(fovSpeedLimit * (deltaSeconds));

        if (Math.abs(desiredYawSpeed) > maxRotation) {
            desiredYawSpeed = Math.signum(desiredYawSpeed) * maxRotation;
        }
        if (Math.abs(desiredPitchSpeed) > maxRotation) {
            desiredPitchSpeed = Math.signum(desiredPitchSpeed) * maxRotation;
        }
        if (Math.abs(desiredFovSpeed) > maxFovChange) {
            desiredFovSpeed = Math.signum(desiredFovSpeed) * maxFovChange;
        }
        // Replaced with direct movement-based ortho factor calculation above

        float newYaw = current.getYaw() + desiredYawSpeed;
        float newPitch = current.getPitch() + desiredPitchSpeed;
        float newFovDelta = (float) (current.getFovMultiplier() + desiredFovSpeed);
        // We're now directly setting orthoFactor based on movement progress above

        // Update current target - note we're keeping the orthoFactor that was already set directly
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

        // logging removed

        // Track last target/errors for next frame (after we compute current)
        lastTargetYaw = targetYaw;
        lastTargetPitch = targetPitch;
        lastYawError = yawError;
        lastPitchError = pitchError;

        boolean complete = resetting && (remaining < 0.007 || progress >= 0.9999);
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
            // Reset final interpolation state upon starting reset
            finalInterpActive = false;
            finalInterpT = 0.0;
            finalInterpStart = null;
            
            // Always target the player head position/rotation during return phase
            if (client.player != null) {
                // Always return to player's head rotation regardless of END_TARGET
                float playerYaw = client.player.getYaw();
                float playerPitch = client.player.getPitch();
                Vec3d playerPos = client.player.getEyePos();
                
                // Set the target position to player head with proper rotation for return
                // When returning to player view, we'll gradually transition back to perspective mode
                // but we need to ensure a smooth transition by starting from the current ortho state
                float currentOrthoFactor = current.getOrthoFactor();
                
                // Create a target with normal FOV (1.0) and preserve the current orthoFactor
                // Instead of immediately setting target to 0.0 ortho factor, we'll transition
                // gradually in calculateState
                end = new CameraTarget(playerPos, playerYaw, playerPitch, 1.0f, currentOrthoFactor);
                
                // Create a new start point that matches current state exactly
                // This ensures our interpolation starts from exactly where we are
                start = new CameraTarget(
                    current.getPosition(),
                    current.getYaw(),
                    current.getPitch(),
                    current.getFovMultiplier(),
                    current.getOrthoFactor()
                );
                
                // logging removed
            }
            
            // Update current camera position
            current = CameraTarget.fromCamera(camera);
            
            // Generate a control point for the return path
            // We're always returning to player position now
            if (client.player != null) {
                controlPoint = generateControlPoint(current.getPosition(), client.player.getEyePos());
                // logging removed
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
        
        // logging removed
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
        // Consider the movement complete when:
        // 1. We're in the resetting phase
        // 2. We're very close to the target position (player's head) OR the progress is nearly complete
        // 3. The FOV has nearly returned to normal
        if (resetting) {
            // Using end position (player position) as the target
            double positionDistance = current.getPosition().distanceTo(end.getPosition());
            float fovDifference = Math.abs(current.getFovMultiplier() - 1.0f);
            float orthoDifference = current.getOrthoFactor(); // Distance to 0.0
            
            // logging removed
            
            // Much stricter position requirement to ensure full movement completion
            // For bezier, we allow completion either by distance OR by progress
            boolean positionComplete = positionDistance < 0.005 || progress > 0.9999;
            boolean fovComplete = fovDifference < 0.01f;
            
            // Only return true when both criteria are met
            // We deliberately don't include ortho completion as it can keep blending after
            return positionComplete && fovComplete;
        }
        return false;
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
