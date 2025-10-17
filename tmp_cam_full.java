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

            // Use a dedicated camera entity for free camera, otherwise detach
            if (mode == CameraMode.FREE_CAMERA) {
                ninja.trek.util.CameraEntity.setCameraState(true);
            } else {
                ninja.trek.Craneshot.LOGGER.info("Detaching camera from player (no entity override)");
                mc.setCameraEntity(null);
            }
            
            cameraActive = true;
            
            // Explicitly apply position/rotation only if not using the dedicated camera entity
            if (currentCamera != null && mode != CameraMode.FREE_CAMERA) {
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

        // If using dedicated camera entity, disable it (restores chunk culling/camera entity)
        if (ninja.trek.util.CameraEntity.getCamera() != null) {
            ninja.trek.util.CameraEntity.setCameraState(false);
        } else if (mc != null) {
            // Restore original settings
            if (originalCameraEntity != null) {
                ninja.trek.Craneshot.LOGGER.info("Restoring original camera entity: {}", originalCameraEntity);
                mc.setCameraEntity(originalCameraEntity);
            } else if (mc.player != null) {
                ninja.trek.Craneshot.LOGGER.info("Setting camera entity to player");
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
    }


