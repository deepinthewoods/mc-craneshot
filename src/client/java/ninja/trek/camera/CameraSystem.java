package ninja.trek.camera;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import ninja.trek.CameraController;
import ninja.trek.CraneshotClient;
import ninja.trek.cameramovements.AbstractMovementSettings;
import ninja.trek.mixin.client.CameraAccessor;
import org.jetbrains.annotations.Nullable;

/**
 * Central class that manages all camera behavior.
 * This class coordinates camera position, rotation, rendering, and collision.
 *
 * IMPORTANT: Position Coordinate System Convention
 * ================================================
 * This codebase uses TWO types of positions:
 *
 * 1. RAW positions (entity.getPos(), entity.getEyePos()):
 *    - Updated 20 times per second (game tick)
 *    - Used for game logic (collision, AI, physics)
 *    - NOT suitable for rendering or visual decisions
 *
 * 2. INTERPOLATED positions (entity.getCameraPosVec(tickDelta)):
 *    - Interpolated between ticks for smooth 60fps+ rendering
 *    - Used for ALL rendering and visual decisions
 *    - REQUIRED for camera calculations and distance checks
 *
 * RULE: If a value is used for rendering decisions (like shouldRenderPlayerModel),
 *       it MUST use interpolated positions to match what's visually on screen.
 *
 * Mixing raw and interpolated positions causes visual glitches during fast
 * movement (falling, sprinting, elytra flight).
 */
public class CameraSystem {
    private static CameraSystem instance;

    // Rendering threshold constants with hysteresis to prevent flickering
    // Hysteresis: Use different thresholds for showing vs hiding to create a buffer zone
    private static final float DISTANCE_SHOW_PLAYER_MODEL = 1.2f;  // Switch from first-person to third-person
    private static final float DISTANCE_HIDE_PLAYER_MODEL = 0.8f;  // Switch from third-person to first-person
    // Legacy threshold (kept for reference, but hysteresis values are used in logic)
    public static final float PLAYER_RENDER_THRESHOLD = 1.0f;

    // Camera state
    private boolean cameraActive = false;
    private Vec3d cameraPosition = Vec3d.ZERO;
    private float cameraYaw = 0f;
    private float cameraPitch = 0f;
    private boolean shouldRenderHands = true;
    private boolean shouldRenderPlayerModel = true;
    private boolean disableChunkCulling = false;
    private Entity originalCameraEntity = null;
    private boolean originalChunkCulling = true;

    // Interpolated player position (updated once per frame for consistent rendering decisions)
    private Vec3d interpolatedPlayerPosition = null;

    // Hysteresis state for player model visibility
    private boolean isPlayerModelCurrentlyVisible = true;

    // Rotation easing state
    private float targetYaw = 0f;      // Where mouse wants us to look
    private float targetPitch = 0f;

    // Movement state
    private Vec3d cameraVelocity = Vec3d.ZERO;
    
    private CameraSystem() {
        // Private constructor for singleton
    }
    
    /**
     * Get the singleton instance of the camera system
     */
    public static CameraSystem getInstance() {
        if (instance == null) {
            instance = new CameraSystem();
        }
        return instance;
    }
    
    /**
     * Activates the custom camera for the given mode
     */
    public void activateCamera(CameraMode mode) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        // Get the current camera if available
        Camera currentCamera = mc.gameRenderer.getCamera();
        Vec3d currentCameraPos = null;
        float currentYaw = 0;
        float currentPitch = 0;

        // Capture current camera position if possible
        if (currentCamera != null) {
            currentCameraPos = currentCamera.getPos();
            currentYaw = currentCamera.getYaw();
            currentPitch = currentCamera.getPitch();
            // logging removed
        }

        // Only activate if not already active
        if (!cameraActive) {
            // Store original state
            originalCameraEntity = mc.getCameraEntity();
            originalChunkCulling = mc.chunkCullingEnabled;
            
            // Initialize camera position and rotation from either current camera (if available)
            // or from the player position
            if (currentCameraPos != null) {
                // Use actual current camera position if available
                cameraPosition = currentCameraPos;
                cameraYaw = currentYaw;
                cameraPitch = currentPitch;
                targetYaw = currentYaw;      // Initialize target to current
                targetPitch = currentPitch;  // Initialize target to current
            } else if (originalCameraEntity != null) {
                // Fallback to entity position
                cameraPosition = originalCameraEntity.getEyePos();
                cameraYaw = originalCameraEntity.getYaw();
                cameraPitch = originalCameraEntity.getPitch();
                targetYaw = cameraYaw;       // Initialize target to current
                targetPitch = cameraPitch;   // Initialize target to current
            }
            
            // Set camera flags based on mode
            shouldRenderHands = !mode.hideHands;
            shouldRenderPlayerModel = mode.showPlayerModel;
            disableChunkCulling = mode.disableChunkCulling;
            
            // Apply chunk culling setting
            mc.chunkCullingEnabled = !disableChunkCulling;

            // Use a dedicated camera entity for free camera, otherwise detach
            if (mode == CameraMode.FREE_CAMERA) {
                ninja.trek.util.CameraEntity.setCameraState(true);
                // Immediately sync the ghost to our position
                syncCameraEntity();
            } else {
                mc.setCameraEntity(null);
            }
            
            cameraActive = true;
            
            // Explicitly apply position/rotation only if not using the dedicated camera entity
            if (currentCamera != null && mode != CameraMode.FREE_CAMERA) {
                ((CameraAccessor) currentCamera).invokesetPos(cameraPosition);
                ((CameraAccessor) currentCamera).invokeSetRotation(cameraYaw, cameraPitch);
            }
        } else {
            // Update settings if camera is already active
            shouldRenderHands = !mode.hideHands;
            shouldRenderPlayerModel = mode.showPlayerModel;
            
            if (disableChunkCulling != mode.disableChunkCulling) {
                disableChunkCulling = mode.disableChunkCulling;
                mc.chunkCullingEnabled = !disableChunkCulling;
            }
        }
    }
    
    /**
     * Deactivates the custom camera and restores original state
     */
    public void deactivateCamera() {
        if (!cameraActive) return;

        MinecraftClient mc = MinecraftClient.getInstance();

        // If using dedicated camera entity, disable it (restores chunk culling/camera entity)
        if (ninja.trek.util.CameraEntity.getCamera() != null) {
            ninja.trek.util.CameraEntity.setCameraState(false);
        } else if (mc != null) {
            // Restore original settings
            if (originalCameraEntity != null) {
                mc.setCameraEntity(originalCameraEntity);
            } else if (mc.player != null) {
                mc.setCameraEntity(mc.player);
            }
            mc.chunkCullingEnabled = originalChunkCulling;
            if (mc.gameRenderer instanceof ninja.trek.mixin.client.FovAccessor) {
                ((ninja.trek.mixin.client.FovAccessor) mc.gameRenderer).setFovModifier(1.0f);
            }
        }

        // Reset all camera state
        cameraActive = false;
        cameraVelocity = Vec3d.ZERO;
        shouldRenderHands = true;
        shouldRenderPlayerModel = true;
        disableChunkCulling = false;
        originalCameraEntity = null;
        interpolatedPlayerPosition = null;
        isPlayerModelCurrentlyVisible = true;
    }

    /**
     * Updates the camera position and rotation immediately.
     */
    public void updateCamera(Camera camera) {
        if (camera == null) return;
        if (!cameraActive) {
            return;
        }

        // Always apply our state to the Camera object
        // CameraEntity is just a ghost for chunk rendering
        ((CameraAccessor) camera).invokesetPos(cameraPosition);
        ((CameraAccessor) camera).invokeSetRotation(cameraYaw, cameraPitch);
    }

    /**
     * Handles keyboard movement input for the camera.
     * @return true if any movement occurred.
     */
    public boolean handleMovementInput(float baseSpeed, float acceleration, float deceleration) {
        if (!cameraActive) return false;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return false;

        Vec3d targetVelocity = calculateTargetVelocity(mc, baseSpeed);
        boolean isMoved = false;

        if (targetVelocity.lengthSquared() > 0.0001) {
            cameraVelocity = cameraVelocity.add(targetVelocity.subtract(cameraVelocity).multiply(acceleration));
            isMoved = true;
        } else {
            cameraVelocity = cameraVelocity.multiply(1.0 - deceleration);
            if (cameraVelocity.lengthSquared() < 0.0001) {
                cameraVelocity = Vec3d.ZERO;
            } else {
                isMoved = true;
            }
        }

        cameraPosition = cameraPosition.add(cameraVelocity);

        Camera camera = mc.gameRenderer.getCamera();
        if (camera != null) {
            updateCamera(camera);
        }
        return isMoved;
    }

    /**
     * Calculates the target velocity from input.
     */
    private Vec3d calculateTargetVelocity(MinecraftClient mc, float baseSpeed) {
        boolean isFreeMode = CameraController.currentKeyMoveMode == AbstractMovementSettings.POST_MOVE_KEYS.MOVE_CAMERA_FREE;

        double x = 0, y = 0, z = 0;
        if (mc.options.forwardKey.isPressed()) z += 1.0;
        if (mc.options.backKey.isPressed()) z -= 1.0;
        if (mc.options.leftKey.isPressed()) x += 1.0;
        if (mc.options.rightKey.isPressed()) x -= 1.0;
        if (mc.options.jumpKey.isPressed()) y += 1.0;
        if (mc.options.sneakKey.isPressed()) y -= 1.0;

        if (x == 0 && y == 0 && z == 0) return Vec3d.ZERO;

        if (mc.options.sprintKey.isPressed()) baseSpeed *= 3.0f;

        if ((x != 0 && z != 0) || (x != 0 && y != 0) || (z != 0 && y != 0)) {
            double len = Math.sqrt(x * x + y * y + z * z);
            x /= len; y /= len; z /= len;
        }

        Vec3d velocity;
        if (CameraController.currentKeyMoveMode == AbstractMovementSettings.POST_MOVE_KEYS.MOVE_CAMERA_FLAT) {
            double xFactor = Math.sin(cameraYaw * Math.PI / 180.0);
            double zFactor = Math.cos(cameraYaw * Math.PI / 180.0);
            double moveX = (x * zFactor - z * xFactor);
            double moveZ = (z * zFactor + x * xFactor);
            velocity = new Vec3d(moveX, y, moveZ);
        } else if (isFreeMode) {
            double yawRad = Math.toRadians(cameraYaw);
            double pitchRad = Math.toRadians(cameraPitch);
            Vec3d forward = new Vec3d(
                -MathHelper.sin((float) yawRad) * MathHelper.cos((float) pitchRad),
                -MathHelper.sin((float) pitchRad),
                MathHelper.cos((float) yawRad) * MathHelper.cos((float) pitchRad)
            );
            Vec3d right = forward.crossProduct(new Vec3d(0.0, 1.0, 0.0));
            if (right.lengthSquared() < 1.0E-6) {
                right = new Vec3d(-MathHelper.cos((float) yawRad), 0.0, -MathHelper.sin((float) yawRad));
            } else {
                right = right.normalize();
            }
            Vec3d up = right.crossProduct(forward).normalize();
            velocity = forward.multiply(z).add(right.multiply(-x)).add(up.multiply(y));
        } else {
            velocity = new Vec3d(x, y, z);
        }

        return velocity.lengthSquared() > 0.0001 ? velocity.normalize().multiply(baseSpeed) : Vec3d.ZERO;
    }

    /**
     * Mouse rotation update - accumulates into target angles for easing.
     */
    public void updateRotation(double deltaX, double deltaY, double sensitivity) {
        if (!cameraActive) return;

        // Accumulate into target angles (not current angles)
        targetYaw += deltaX * sensitivity;
        targetPitch = (float) MathHelper.clamp(targetPitch - deltaY * sensitivity, -90.0f, 90.0f);

        // Normalize targetYaw
        while (targetYaw > 360.0f) targetYaw -= 360.0f;
        while (targetYaw < 0.0f) targetYaw += 360.0f;
    }

    /**
     * Applies rotation easing from current angles toward target angles.
     * Similar to FollowMovement.easedAngle() - calculates error and applies easing factor.
     */
    public void applyRotationEasing(float deltaSeconds) {
        if (!cameraActive) return;

        ninja.trek.config.FreeCamSettings settings = ninja.trek.config.GeneralMenuSettings.getFreeCamSettings();
        float easingFactor = settings.getRotationEasing();
        float speedLimit = settings.getRotationSpeedLimit();

        // Ease yaw toward target
        float yawError = targetYaw - cameraYaw;
        // Normalize error to [-180, 180] for shortest rotation path
        while (yawError > 180) yawError -= 360;
        while (yawError < -180) yawError += 360;

        float yawSpeed = yawError * easingFactor;
        float maxYawChange = speedLimit * deltaSeconds;
        if (Math.abs(yawSpeed) > maxYawChange) {
            yawSpeed = Math.signum(yawSpeed) * maxYawChange;
        }
        cameraYaw += yawSpeed;

        // Normalize cameraYaw
        while (cameraYaw > 360.0f) cameraYaw -= 360.0f;
        while (cameraYaw < 0.0f) cameraYaw += 360.0f;

        // Ease pitch toward target
        float pitchError = targetPitch - cameraPitch;
        float pitchSpeed = pitchError * easingFactor;
        float maxPitchChange = speedLimit * deltaSeconds;
        if (Math.abs(pitchSpeed) > maxPitchChange) {
            pitchSpeed = Math.signum(pitchSpeed) * maxPitchChange;
        }
        cameraPitch += pitchSpeed;
    }

    /**
     * Syncs the ghost CameraEntity to match CameraSystem's state.
     * CameraEntity is just a dummy for chunk rendering.
     */
    public void syncCameraEntity() {
        ninja.trek.util.CameraEntity camEnt = ninja.trek.util.CameraEntity.getCamera();
        if (camEnt != null) {
            camEnt.setPos(cameraPosition.x, cameraPosition.y, cameraPosition.z);
            camEnt.setCameraRotations(cameraYaw, cameraPitch);
            camEnt.setVelocity(Vec3d.ZERO); // Prevent physics interference
        }
    }

    public void setCameraPosition(Vec3d position) {
        if (position == null) return;
        this.cameraPosition = position;
        if (cameraActive) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null) {
                Camera camera = mc.gameRenderer.getCamera();
                if (camera != null) ((CameraAccessor) camera).invokesetPos(cameraPosition);
            }
        }
    }

    public void setCameraRotation(float yaw, float pitch) {
        this.cameraYaw = yaw;
        this.cameraPitch = pitch;
        this.targetYaw = yaw;      // Keep target in sync when setting rotation
        this.targetPitch = pitch;  // Keep target in sync when setting rotation

        if (cameraActive) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null) {
                Camera camera = mc.gameRenderer.getCamera();
                if (camera != null) ((CameraAccessor) camera).invokeSetRotation(cameraYaw, cameraPitch);
            }
        }
    }

    public Vec3d getCameraPosition() { return cameraPosition; }
    public float getCameraYaw() { return cameraYaw; }
    public float getCameraPitch() { return cameraPitch; }
    public boolean isCameraActive() { return cameraActive; }

    public void resetVelocity() {
        this.cameraVelocity = Vec3d.ZERO;
    }

    /**
     * Updates the cached interpolated player position.
     * This should be called once per frame BEFORE any rendering decisions are made.
     *
     * @param position The player's interpolated eye position from getCameraPosVec(tickDelta)
     */
    public void updateInterpolatedPlayerPosition(Vec3d position) {
        this.interpolatedPlayerPosition = position;
    }

    /**
     * Gets the player position for rendering decisions.
     * ALWAYS uses interpolated position to match what's visually on screen.
     *
     * @return The interpolated player position, or a fallback if not available
     */
    private Vec3d getPlayerPositionForRendering() {
        if (interpolatedPlayerPosition != null) {
            return interpolatedPlayerPosition;
        }

        // Fallback: use raw position (should rarely happen)
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            return mc.player.getEyePos();
        }

        return Vec3d.ZERO;
    }

    /**
     * Gets the visual distance from camera to player (using interpolated positions).
     * Use this for any distance-based rendering decisions.
     *
     * @return The distance in blocks between camera and player
     */
    public double getVisualDistanceToPlayer() {
        Vec3d playerPos = getPlayerPositionForRendering();
        return cameraPosition.distanceTo(playerPos);
    }

    public boolean shouldRenderHands() {
        if (!shouldRenderHands) return false;
        if (!cameraActive) return shouldRenderHands;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            // Hands and player model are mutually exclusive states:
            // - First person (close): Show hands, hide player model
            // - Third person (far): Hide hands, show player model
            // Use the OPPOSITE of player model visibility for consistency with hysteresis
            return !isPlayerModelCurrentlyVisible;
        }
        return shouldRenderHands;
    }

    public boolean shouldRenderPlayerModel() {
        if (!shouldRenderPlayerModel) return false;
        if (!cameraActive) return shouldRenderPlayerModel;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            // Use interpolated position for consistent rendering
            double distance = getVisualDistanceToPlayer();

            // Hysteresis: Use different thresholds depending on current state
            // This prevents flickering when distance oscillates around the threshold
            if (isPlayerModelCurrentlyVisible) {
                // Currently showing model - only hide if we get very close
                if (distance < DISTANCE_HIDE_PLAYER_MODEL) {
                    isPlayerModelCurrentlyVisible = false;
                }
            } else {
                // Currently hiding model - only show if we get far enough away
                if (distance >= DISTANCE_SHOW_PLAYER_MODEL) {
                    isPlayerModelCurrentlyVisible = true;
                }
            }

            return isPlayerModelCurrentlyVisible;
        }
        return shouldRenderPlayerModel;
    }

    public void setShouldRenderHands(boolean renderHands) {
        this.shouldRenderHands = renderHands;
    }

    

    public static class CameraMode {
        public final boolean hideHands;
        public final boolean showPlayerModel;
        public final boolean disableChunkCulling;
        public CameraMode(boolean hideHands, boolean showPlayerModel, boolean disableChunkCulling) {
            this.hideHands = hideHands;
            this.showPlayerModel = showPlayerModel;
            this.disableChunkCulling = disableChunkCulling;
        }
        public static final CameraMode THIRD_PERSON = new CameraMode(true, true, true);
        public static final CameraMode FREE_CAMERA = new CameraMode(true, true, true);
        public static final CameraMode FIRST_PERSON = new CameraMode(false, false, false);
        public static final CameraMode NODE_DRIVEN = new CameraMode(true, true, true);
    }

}
