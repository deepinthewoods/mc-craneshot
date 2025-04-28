package ninja.trek.mixin.client;

import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import ninja.trek.CameraController;
import ninja.trek.OrthographicCameraManager;
import ninja.trek.camera.CameraSystem;
import ninja.trek.cameramovements.AbstractMovementSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    @Shadow private double lastCameraX;
    @Shadow private double lastCameraY;
    @Shadow private double lastCameraZ;
    @Shadow private double lastCameraPitch;
    
    // Remove the shadow for needsTerrainUpdate since it doesn't exist in WorldRenderer
    @Unique
    private boolean needsTerrainUpdate;

    @Unique
    private long lastUpdateTime = 0;
    @Unique
    private static final long UPDATE_INTERVAL = 500; // Half second between forced updates
    
    /**
     * Sets lastCameraPitch to -1000 if we're in the "out" phase of a movement with projection set to ORTHO.
     * This ensures proper terrain rendering during orthographic camera transitions.
     */
    @Inject(
            method = "setupTerrain(Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/Frustum;ZZ)V",
            at = @At("HEAD")
    )
    private void resetLastCameraPitchInOrthoOut(Camera camera, Frustum frustum, boolean hasForcedFrustum, boolean spectator, CallbackInfo ci) {
        // Check if orthographic mode is active
        if (OrthographicCameraManager.isOrthographicMode()) {
            // Check if we're in the "out" phase of movement
            boolean isOutPhase = 
                CameraController.currentEndTarget == AbstractMovementSettings.END_TARGET.HEAD_BACK ||
                CameraController.currentEndTarget == AbstractMovementSettings.END_TARGET.VELOCITY_BACK ||
                CameraController.currentEndTarget == AbstractMovementSettings.END_TARGET.FIXED_BACK;
                
            if (isOutPhase) {
                // Set lastCameraPitch to -1000 to force terrain update
                this.lastCameraPitch = -1000;
            }
        }
    }

//    /**
//     * Improves chunk rendering in special camera modes by:
//     * 1. Updating frustum position
//     * 2. Forcing chunk rebuilding when camera moves significantly
//     * 3. Scheduling terrain updates in orthographic mode
//     */
//    @Inject(
//            method = "setupTerrain",
//            at = @At("HEAD")
//    )
//    private void onSetupTerrainStart(Camera camera, Frustum frustum, boolean hasForcedFrustum, boolean spectator, CallbackInfo ci) {
//        // Instead of setting needsTerrainUpdate directly, call scheduleTerrainUpdate() method
//        if (OrthographicCameraManager.isOrthographicMode()) {
//            WorldRenderer worldRenderer = (WorldRenderer)(Object)this;
//            worldRenderer.scheduleTerrainUpdate();
//        }
//
//        // Check if using camera system
//        CameraSystem cameraSystem = CameraSystem.getInstance();
//        boolean isCameraSystemActive = cameraSystem.isCameraActive();
//
//        // Check for legacy camera modes
//        boolean isLegacyCameraActive =
//            CameraController.currentKeyMoveMode == AbstractMovementSettings.POST_MOVE_KEYS.MOVE_CAMERA_FREE ||
//            CameraController.currentKeyMoveMode == AbstractMovementSettings.POST_MOVE_KEYS.MOVE_CAMERA_FLAT ||
//            OrthographicCameraManager.isOrthographicMode() ||
//            CameraController.currentEndTarget == AbstractMovementSettings.END_TARGET.HEAD_BACK ||
//            CameraController.currentEndTarget == AbstractMovementSettings.END_TARGET.VELOCITY_BACK ||
//            CameraController.currentEndTarget == AbstractMovementSettings.END_TARGET.FIXED_BACK;
//
//        if (isCameraSystemActive || isLegacyCameraActive) {
//            // Get the camera position
//            Vec3d camPos = camera.getPos();
//
//            // Update the frustum to use the camera position
//            frustum.setPosition(camPos.x, camPos.y, camPos.z);
//
//            // For orthographic mode, we need special handling to render more chunks
//            boolean isOrthographic = OrthographicCameraManager.isOrthographicMode();
//
//            // Check if enough time has passed since last update for chunk rebuilding
//            long currentTime = System.currentTimeMillis();
//
//            // Force more frequent updates in orthographic mode
//            long interval = isOrthographic ? UPDATE_INTERVAL / 2 : UPDATE_INTERVAL;
//            boolean timeToUpdate = (currentTime - lastUpdateTime > interval);
//
//            // Orthographic mode needs more aggressive chunk loading
//            double movementThreshold = isOrthographic ? 4.0 : 12.0;
//            double dx = Math.abs(camPos.x - lastCameraX);
//            double dy = Math.abs(camPos.y - lastCameraY);
//            double dz = Math.abs(camPos.z - lastCameraZ);
//            boolean movedSignificantly = (dx > movementThreshold || dy > movementThreshold || dz > movementThreshold);
//
//            if (timeToUpdate && (movedSignificantly || isOrthographic)) {
//                // Update the last time we detected significant movement
//                lastUpdateTime = currentTime;
//
//                // Mark chunks for rebuild
//                WorldRenderer worldRenderer = (WorldRenderer)(Object)this;
//                int chunkX = (int)(camPos.x) >> 4;
//                int chunkZ = (int)(camPos.z) >> 4;
//
//                // Render more distant chunks in orthographic mode
//                int radius = isOrthographic ? 8 : 3;
//
//                for (int cx = chunkX - radius; cx <= chunkX + radius; cx++) {
//                    for (int cz = chunkZ - radius; cz <= chunkZ + radius; cz++) {
//                        // Only rebuild chunks that are closer in non-orthographic mode
//                        if (!isOrthographic || Math.abs(cx - chunkX) <= 3 || Math.abs(cz - chunkZ) <= 3) {
//                            for (int cy = 0; cy < 16; cy++) {
//                                worldRenderer.scheduleChunkRender(cx, cy, cz);
//                            }
//                        }
//                    }
//                }
//
//                // Force terrain update to make chunks render immediately
//                if (isOrthographic) {
//                    // Use the appropriate method instead of the field
//                    worldRenderer.scheduleTerrainUpdate();
//
//                    // Ensure chunk culling is disabled in orthographic mode
//                    // This prevents problems with missing chunks
//                    MinecraftClient.getInstance().chunkCullingEnabled = false;
//                }
//            }
//        }
//    }
//
    /**
     * Modifies the chunk builder's camera position
     */
    @ModifyArg(
            method = "setupTerrain",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/chunk/ChunkBuilder;setCameraPosition(Lnet/minecraft/util/math/Vec3d;)V"
            ),
            index = 0
    )
    private Vec3d modifyChunkBuilderCameraPosition(Vec3d original) {
        // Use the current camera position
        CameraSystem cameraSystem = CameraSystem.getInstance();
        if (cameraSystem.isCameraActive()) {
            return cameraSystem.getCameraPosition();
        }
        
        // Legacy support
        boolean isLegacyCameraActive = 
            CameraController.currentKeyMoveMode == AbstractMovementSettings.POST_MOVE_KEYS.MOVE_CAMERA_FREE ||
            CameraController.currentKeyMoveMode == AbstractMovementSettings.POST_MOVE_KEYS.MOVE_CAMERA_FLAT ||
            OrthographicCameraManager.isOrthographicMode() ||
            CameraController.currentEndTarget == AbstractMovementSettings.END_TARGET.HEAD_BACK ||
            CameraController.currentEndTarget == AbstractMovementSettings.END_TARGET.VELOCITY_BACK ||
            CameraController.currentEndTarget == AbstractMovementSettings.END_TARGET.FIXED_BACK;
            
        if (isLegacyCameraActive && CameraController.freeCamPosition != null) {
            return CameraController.freeCamPosition;
        }
        
        return original;
    }
}