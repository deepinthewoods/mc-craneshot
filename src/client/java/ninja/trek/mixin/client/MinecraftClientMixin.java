package ninja.trek.mixin.client;

import net.minecraft.client.MinecraftClient;
import ninja.trek.CameraController;
import ninja.trek.OrthographicCameraManager;
import ninja.trek.camera.CameraSystem;
import ninja.trek.cameramovements.AbstractMovementSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    @Shadow public boolean chunkCullingEnabled;
    private boolean wasChunkCullingEnabled = true;

    /**
     * Disables chunk culling for special camera modes to ensure all chunks render correctly
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void updateChunkCullingOnTickStart(CallbackInfo ci) {
        // For orthographic mode, we disable culling at the start of every tick
        // This ensures it's always disabled, even if something else tries to enable it
        if (OrthographicCameraManager.isOrthographicMode()) {
            if (this.chunkCullingEnabled) {
                wasChunkCullingEnabled = true;
                this.chunkCullingEnabled = false;
            }
        }
    }

    /**
     * Disables chunk culling for special camera modes to ensure all chunks render correctly
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void updateChunkCulling(CallbackInfo ci) {
        // Check if we need to force chunk culling off
        boolean shouldDisableCulling = false;
        
        // Check new camera system state
        CameraSystem cameraSystem = CameraSystem.getInstance();
        if (cameraSystem.isCameraActive()) {
            shouldDisableCulling = true;
        }
        
        // Check orthographic mode - this should ALWAYS disable culling
        if (OrthographicCameraManager.isOrthographicMode()) {
            shouldDisableCulling = true;
        }
        
        // Check legacy camera modes
        boolean isLegacyCameraActive = 
            CameraController.currentKeyMoveMode == AbstractMovementSettings.POST_MOVE_KEYS.MOVE_CAMERA_FREE ||
            CameraController.currentKeyMoveMode == AbstractMovementSettings.POST_MOVE_KEYS.MOVE_CAMERA_FLAT ||
            CameraController.currentEndTarget == AbstractMovementSettings.END_TARGET.HEAD_BACK ||
            CameraController.currentEndTarget == AbstractMovementSettings.END_TARGET.VELOCITY_BACK ||
            CameraController.currentEndTarget == AbstractMovementSettings.END_TARGET.FIXED_BACK;
            
        if (isLegacyCameraActive) {
            shouldDisableCulling = true;
        }
        
        // Disable chunk culling when needed
        if (shouldDisableCulling) {
            if (this.chunkCullingEnabled) {
                wasChunkCullingEnabled = true;
                this.chunkCullingEnabled = false;
            }
        } else if (!this.chunkCullingEnabled && wasChunkCullingEnabled) {
            // Reset to default when not in special camera mode
            this.chunkCullingEnabled = true;
        }
    }
}