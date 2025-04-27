package ninja.trek.camera;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import ninja.trek.CameraController;
import ninja.trek.OrthographicCameraManager;
import ninja.trek.cameramovements.AbstractMovementSettings;
import ninja.trek.mixin.client.CameraAccessor;
import org.jetbrains.annotations.Nullable;

/**
 * Central class that manages all camera behavior.
 * This class coordinates camera position, rotation, rendering, and collision.
 */
public class CameraSystem {
    private static CameraSystem instance;
    
    // Rendering threshold constant - if camera is closer than this, render arms; otherwise render body model
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
        
        ninja.trek.Craneshot.LOGGER.info("Activating camera in mode: {}", mode);
        
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
            ninja.trek.Craneshot.LOGGER.info("Current camera position: {} {} {}",
                currentCameraPos.getX(), currentCameraPos.getY(), currentCameraPos.getZ());
        }
        
        // Only activate if not already active
        if (!cameraActive) {
            // Store original state
            originalCameraEntity = mc.getCameraEntity();
            originalChunkCulling = mc.chunkCullingEnabled;
            
            ninja.trek.Craneshot.LOGGER.info("Original camera entity: {}", originalCameraEntity);
            
            // Initialize camera position and rotation from either current camera (if available)
            // or from the player position
            if (currentCameraPos != null) {
                // Use actual current camera position if available
                cameraPosition = currentCameraPos;
                cameraYaw = currentYaw;
                cameraPitch = currentPitch;
            } else if (originalCameraEntity != null) {
                // Fallback to entity position
                cameraPosition = originalCameraEntity.getEyePos();
                cameraYaw = originalCameraEntity.getYaw();
                cameraPitch = originalCameraEntity.getPitch();
            }
            
            ninja.trek.Craneshot.LOGGER.info("Initial camera position: {} {} {}", 
                cameraPosition.getX(), cameraPosition.getY(), cameraPosition.getZ());
            ninja.trek.Craneshot.LOGGER.info("Initial camera rotation: {} {}", cameraYaw, cameraPitch);
            
            // Set camera flags based on mode
            shouldRenderHands = !mode.hideHands;
            shouldRenderPlayerModel = mode.showPlayerModel;
            disableChunkCulling = mode.disableChunkCulling;
            
            // Apply chunk culling setting
            mc.chunkCullingEnabled = !disableChunkCulling;
            
            // Set the camera entity to null to detach from player
            ninja.trek.Craneshot.LOGGER.info("Setting camera entity to null");
            mc.setCameraEntity(null);
            
            cameraActive = true;
            
            // Explicitly apply position and rotation to ensure immediate update
            if (currentCamera != null) {
                ninja.trek.Craneshot.LOGGER.info("Initial camera update");
                ((CameraAccessor) currentCamera).invokesetPos(cameraPosition);
                ((CameraAccessor) currentCamera).invokeSetRotation(cameraYaw, cameraPitch);
            }
        } else {
            // Update settings if camera is already active
            ninja.trek.Craneshot.LOGGER.info("Camera already active, updating settings");
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
        
        ninja.trek.Craneshot.LOGGER.info("Deactivating camera system");
        
        // Restore original settings
        if (mc != null) {
            // Force reset to original camera entity if available
            if (originalCameraEntity != null) {
                ninja.trek.Craneshot.LOGGER.info("Restoring original camera entity: {}", originalCameraEntity);
                mc.setCameraEntity(originalCameraEntity);
            } else if (mc.player != null) {
                // Fall back to player if original entity is null
                ninja.trek.Craneshot.LOGGER.info("Setting camera entity to player");
                mc.setCameraEntity(mc.player);
            }
            
            // Restore chunk culling
            mc.chunkCullingEnabled = originalChunkCulling;
            
            // Reset FOV to default
            if (mc.gameRenderer instanceof ninja.trek.mixin.client.FovAccessor) {
                ((ninja.trek.mixin.client.FovAccessor) mc.gameRenderer).setFovModifier(1.0f);
            }
        }
        
        // Reset all camera state
        cameraActive = false;
        cameraVelocity = Vec3d.ZERO;
        
        // Explicitly reset camera position to the player position if possible
        if (mc != null && mc.player != null) {
            cameraPosition = mc.player.getEyePos();
            cameraYaw = mc.player.getYaw();
            cameraPitch = mc.player.getPitch();
        }
        
        ninja.trek.Craneshot.LOGGER.info("Final camera position: {} {} {}", 
            cameraPosition.getX(), cameraPosition.getY(), cameraPosition.getZ());
        ninja.trek.Craneshot.LOGGER.info("Final camera rotation: {} {}", cameraYaw, cameraPitch);
        
        // Apply updated camera settings to the actual camera if available
        if (mc != null && mc.gameRenderer != null && mc.gameRenderer.getCamera() != null) {
            Camera camera = mc.gameRenderer.getCamera();
            
            // Force the camera to use the player's position
            if (mc.player != null) {
                ((CameraAccessor) camera).invokesetPos(mc.player.getEyePos());
                ((CameraAccessor) camera).invokeSetRotation(mc.player.getYaw(), mc.player.getPitch());
            }
        }
        
        // Mark chunks for rebuild to fix any rendering issues
        if (mc != null && mc.worldRenderer != null) {
            int chunkX = MathHelper.floor(cameraPosition.getX()) >> 4;
            int chunkZ = MathHelper.floor(cameraPosition.getZ()) >> 4;
            for (int x = chunkX - 3; x <= chunkX + 3; x++) {
                for (int z = chunkZ - 3; z <= chunkZ + 3; z++) {
                    for (int y = 0; y < 16; y++) {
                        mc.worldRenderer.scheduleChunkRender(x, y, z);
                    }
                }
            }
        }
        
        // Clear the original entity reference
        originalCameraEntity = null;
    }
    
    /**
     * Updates the camera position and rotation
     * @param camera The Minecraft camera instance
     */
    public void updateCamera(Camera camera) {
        if (!cameraActive || camera == null) return;
        
        MinecraftClient mc = MinecraftClient.getInstance();
        
        // Ensure we're not using player as camera entity
        if (mc.getCameraEntity() != null) {
            ninja.trek.Craneshot.LOGGER.debug("Resetting camera entity to null");
            mc.setCameraEntity(null);
        }
        
        // Get the current camera position for logging
        Vec3d currentPos = ((CameraAccessor) camera).getPos();
        float currentYaw = camera.getYaw();
        float currentPitch = camera.getPitch();
        
        // Log current and target positions
        ninja.trek.Craneshot.LOGGER.debug("Current camera pos: {} {} {}, target: {} {} {}",
            currentPos.getX(), currentPos.getY(), currentPos.getZ(),
            cameraPosition.getX(), cameraPosition.getY(), cameraPosition.getZ());
        
        // Apply position and rotation to the camera
        ((CameraAccessor) camera).invokesetPos(cameraPosition);
        ((CameraAccessor) camera).invokeSetRotation(cameraYaw, cameraPitch);
        
        // Verify that our changes actually took effect
        Vec3d actualPos = ((CameraAccessor) camera).getPos();
        if (!actualPos.equals(cameraPosition)) {
            ninja.trek.Craneshot.LOGGER.warn("Camera position not updated correctly! Expected: {} but got: {}", 
                cameraPosition, actualPos);
        }
    }
    
    /**
     * Handles keyboard movement input for the camera
     */
    public void handleMovementInput(float baseSpeed, float acceleration, float deceleration) {
        if (!cameraActive) return;
        
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        
        // Calculate target velocity based on input
        Vec3d targetVelocity = calculateTargetVelocity(mc, baseSpeed);
        
        // Apply acceleration/deceleration
        if (targetVelocity.lengthSquared() > 0.0001) {
            // Accelerate toward target velocity
            cameraVelocity = cameraVelocity.add(
                targetVelocity.subtract(cameraVelocity).multiply(acceleration));
        } else {
            // Decelerate when no input
            cameraVelocity = cameraVelocity.multiply(1.0 - deceleration);
            // Zero out very small velocities to prevent drift
            if (cameraVelocity.lengthSquared() < 0.0001) {
                cameraVelocity = Vec3d.ZERO;
            }
        }
        
        // Apply velocity to position
        cameraPosition = cameraPosition.add(cameraVelocity);
        
        // Immediately update camera if available
        Camera camera = mc.gameRenderer.getCamera();
        if (camera != null) {
            updateCamera(camera);
        }
    }
    
    /**
     * Calculates the target velocity based on keyboard input
     */
    private Vec3d calculateTargetVelocity(MinecraftClient mc, float baseSpeed) {
        boolean isOrtho = OrthographicCameraManager.isOrthographicMode();
        boolean isFreeMode = CameraController.currentKeyMoveMode == AbstractMovementSettings.POST_MOVE_KEYS.MOVE_CAMERA_FREE;
        
        double x = 0, y = 0, z = 0;
        
        // Get keyboard input
        if (mc.options.forwardKey.isPressed()) z += 1.0;
        if (mc.options.backKey.isPressed()) z -= 1.0;
        if (mc.options.leftKey.isPressed()) x += 1.0;
        if (mc.options.rightKey.isPressed()) x -= 1.0;
        if (mc.options.jumpKey.isPressed()) y += 1.0;
        if (mc.options.sneakKey.isPressed()) y -= 1.0;
        
        // Check if any movement keys are pressed
        if (x == 0 && y == 0 && z == 0) {
            return Vec3d.ZERO;
        }
        
        // Apply sprint multiplier
        if (mc.options.sprintKey.isPressed()) {
            baseSpeed *= 3.0f;
        }
        
        // Normalize if moving in multiple directions
        if ((x != 0 && z != 0) || (x != 0 && y != 0) || (z != 0 && y != 0)) {
            double length = Math.sqrt(x * x + y * y + z * z);
            x /= length;
            y /= length;
            z /= length;
        }
        
        Vec3d velocity;
        
        // For orthographic or flat movement mode, use camera-relative movement on XZ plane
        if (isOrtho || CameraController.currentKeyMoveMode == AbstractMovementSettings.POST_MOVE_KEYS.MOVE_CAMERA_FLAT) {
            double xFactor = Math.sin(cameraYaw * Math.PI / 180.0);
            double zFactor = Math.cos(cameraYaw * Math.PI / 180.0);
            
            double moveX = (x * zFactor - z * xFactor);
            double moveZ = (z * zFactor + x * xFactor);
            
            velocity = new Vec3d(moveX, y, moveZ);
        }
        // For free movement, use full camera-relative movement
        else if (isFreeMode) {
            double xFactor = Math.sin(cameraYaw * Math.PI / 180.0);
            double zFactor = Math.cos(cameraYaw * Math.PI / 180.0);
            double pitchFactor = Math.sin(cameraPitch * Math.PI / 180.0);
            
            double moveX = (x * zFactor - z * xFactor);
            double moveY = y;
            double moveZ = (z * zFactor + x * xFactor);
            
            if (Math.abs(cameraPitch) > 30 && isFreeMode) {
                // Adjust vertical movement based on pitch in free mode
                moveY -= z * pitchFactor * 0.5;
            }
            
            velocity = new Vec3d(moveX, moveY, moveZ);
        }
        // Default to simple movement
        else {
            velocity = new Vec3d(x, y, z);
        }
        
        // Normalize and apply speed
        if (velocity.lengthSquared() > 0.0001) {
            return velocity.normalize().multiply(baseSpeed);
        }
        
        return Vec3d.ZERO;
    }
    
    /**
     * Updates the camera rotation based on mouse movement
     */
    public void updateRotation(double deltaX, double deltaY, double sensitivity) {
        if (!cameraActive) return;
        
        // Apply mouse movement to rotation
        cameraYaw += deltaX * sensitivity;
        // Normalize yaw to prevent floating-point issues after extended rotation
        while (cameraYaw > 360.0f) cameraYaw -= 360.0f;
        while (cameraYaw < 0.0f) cameraYaw += 360.0f;
        
        cameraPitch = (float) MathHelper.clamp(cameraPitch - deltaY * sensitivity, -90.0f, 90.0f);
        
        // Immediately update camera if available
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null) {
            Camera camera = mc.gameRenderer.getCamera();
            if (camera != null) {
                updateCamera(camera);
            }
        }
    }
    
    /**
     * Sets the camera position directly
     */
    public void setCameraPosition(Vec3d position) {
        if (position == null) {
            ninja.trek.Craneshot.LOGGER.warn("Attempted to set camera position to null!");
            return;
        }
        
        this.cameraPosition = position;
        
        // Log position for debugging
        ninja.trek.Craneshot.LOGGER.debug("CameraSystem.setCameraPosition: {} {} {}", 
            position.getX(), position.getY(), position.getZ());
        
        // Apply changes immediately if camera is active
        if (cameraActive) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null) {
                Camera camera = mc.gameRenderer.getCamera();
                if (camera != null) {
                    ((CameraAccessor) camera).invokesetPos(cameraPosition);
                }
            }
        }
    }
    
    /**
     * Sets the camera rotation directly
     */
    public void setCameraRotation(float yaw, float pitch) {
        this.cameraYaw = yaw;
        this.cameraPitch = pitch;
        
        // Log rotation for debugging
        ninja.trek.Craneshot.LOGGER.debug("CameraSystem.setCameraRotation: {} {}", yaw, pitch);
        
        // Apply changes immediately if camera is active
        if (cameraActive) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null) {
                Camera camera = mc.gameRenderer.getCamera();
                if (camera != null) {
                    ((CameraAccessor) camera).invokeSetRotation(cameraYaw, cameraPitch);
                }
            }
        }
    }
    
    /**
     * Get the current camera position
     */
    public Vec3d getCameraPosition() {
        return cameraPosition;
    }
    
    /**
     * Get the current camera yaw
     */
    public float getCameraYaw() {
        return cameraYaw;
    }
    
    /**
     * Get the current camera pitch
     */
    public float getCameraPitch() {
        return cameraPitch;
    }
    
    /**
     * Check if the camera is currently active
     */
    public boolean isCameraActive() {
        return cameraActive;
    }
    
    /**
     * Check if hands should be rendered
     * Takes into account both the camera mode setting and distance threshold
     */
    public boolean shouldRenderHands() {
        if (!shouldRenderHands) return false;
        
        // Check camera distance to player
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && cameraActive) {
            double distanceToPlayer = cameraPosition.distanceTo(mc.player.getEyePos());
            // Render hands only when closer than the threshold
            return distanceToPlayer < PLAYER_RENDER_THRESHOLD;
        }
        
        return shouldRenderHands;
    }
    
    /**
     * Check if player model should be rendered
     * Takes into account both the camera mode setting and distance threshold
     */
    public boolean shouldRenderPlayerModel() {
        if (!shouldRenderPlayerModel) return false;
        
        // Check camera distance to player
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && cameraActive) {
            double distanceToPlayer = cameraPosition.distanceTo(mc.player.getEyePos());
            // Render player model only when farther than the threshold
            return distanceToPlayer >= PLAYER_RENDER_THRESHOLD;
        }
        
        return shouldRenderPlayerModel;
    }
    
    /**
     * Set whether to render hands
     */
    public void setShouldRenderHands(boolean renderHands) {
        this.shouldRenderHands = renderHands;
    }
    
    /**
     * Camera mode defines how the camera should behave
     */
    public static class CameraMode {
        public final boolean hideHands;
        public final boolean showPlayerModel;
        public final boolean disableChunkCulling;
        
        public CameraMode(boolean hideHands, boolean showPlayerModel, boolean disableChunkCulling) {
            this.hideHands = hideHands;
            this.showPlayerModel = showPlayerModel;
            this.disableChunkCulling = disableChunkCulling;
        }
        
        // Predefined camera modes
        public static final CameraMode ORTHOGRAPHIC = new CameraMode(
            true,     // Hide hands
            true,     // Show player model
            true      // Disable chunk culling
        );
        
        public static final CameraMode THIRD_PERSON = new CameraMode(
            true,     // Hide hands
            true,     // Show player model
            true      // Disable chunk culling
        );
        
        public static final CameraMode FREE_CAMERA = new CameraMode(
            true,     // Hide hands
            false,    // Don't show player model
            true      // Disable chunk culling
        );
        
        public static final CameraMode FIRST_PERSON = new CameraMode(
            false,    // Show hands
            false,    // Don't show player model
            false     // Normal chunk culling
        );
    }
}