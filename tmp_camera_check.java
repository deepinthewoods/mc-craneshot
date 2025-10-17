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

    /**
     * Updates the camera position and rotation immediately.
     */
    public void updateCamera(Camera camera) {
        if (!cameraActive || camera == null) return;

        // When using the dedicated camera entity, vanilla handles transform; skip manual override
        if (ninja.trek.util.CameraEntity.getCamera() != null) {
            return;
        }

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
            double xFactor = Math.sin(cameraYaw * Math.PI / 180.0);
            double zFactor = Math.cos(cameraYaw * Math.PI / 180.0);
            double pitchFactor = Math.sin(cameraPitch * Math.PI / 180.0);
            double moveX = (x * zFactor - z * xFactor);
            double moveY = y;
            double moveZ = (z * zFactor + x * xFactor);
            if (Math.abs(cameraPitch) > 30) {
                moveY -= z * pitchFactor * 0.5;
            }
            velocity = new Vec3d(moveX, moveY, moveZ);
        } else {
            velocity = new Vec3d(x, y, z);
        }

        return velocity.lengthSquared() > 0.0001 ? velocity.normalize().multiply(baseSpeed) : Vec3d.ZERO;
    }

    /**
     * Mouse rotation update.
     */
    public void updateRotation(double deltaX, double deltaY, double sensitivity) {
        if (!cameraActive) return;
        cameraYaw += deltaX * sensitivity;
        while (cameraYaw > 360.0f) cameraYaw -= 360.0f;
        while (cameraYaw < 0.0f) cameraYaw += 360.0f;
        cameraPitch = (float) MathHelper.clamp(cameraPitch - deltaY * sensitivity, -90.0f, 90.0f);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null) {
            Camera camera = mc.gameRenderer.getCamera();
            if (camera != null) updateCamera(camera);
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

    public boolean shouldRenderHands() {
        if (!shouldRenderHands) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && cameraActive) {
            double d = cameraPosition.distanceTo(mc.player.getEyePos());
            return d < PLAYER_RENDER_THRESHOLD;
        }
        return shouldRenderHands;
    }

    public boolean shouldRenderPlayerModel() {
        if (!shouldRenderPlayerModel) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && cameraActive) {
            double d = cameraPosition.distanceTo(mc.player.getEyePos());
            return d >= PLAYER_RENDER_THRESHOLD;
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
        public static final CameraMode FREE_CAMERA = new CameraMode(true, false, true);
        public static final CameraMode FIRST_PERSON = new CameraMode(false, false, false);
    }

