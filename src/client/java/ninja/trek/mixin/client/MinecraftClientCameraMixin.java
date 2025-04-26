package ninja.trek.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import ninja.trek.CameraController;
import ninja.trek.camera.CameraSystem;
import ninja.trek.cameramovements.AbstractMovementSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts setCameraEntity calls in MinecraftClient to prevent vanilla from taking control
 */
@Mixin(MinecraftClient.class)
public class MinecraftClientCameraMixin {

    /**
     * This critical mixin prevents the vanilla game from setting the camera entity
     * when our custom camera system should be in control.
     */
    @Inject(method = "setCameraEntity", at = @At("HEAD"), cancellable = true)
    private void onSetCameraEntity(Entity entity, CallbackInfo ci) {
        CameraSystem cameraSystem = CameraSystem.getInstance();
        
        // If camera should be in ANY custom mode, prevent vanilla from setting the camera entity
        if (isCustomCameraActive()) {
            if (entity != null) {
                ci.cancel(); // Prevent the vanilla method from executing
                
                // Force to null if needed
                MinecraftClient mc = (MinecraftClient)(Object)this;
                if (mc.getCameraEntity() != null) {
                    // We have to use reflection or something similar here 
                    // since we can't directly set the field as it's private
                    // For now, we'll leave this part and rely on the camera update cycle
                    ninja.trek.Craneshot.LOGGER.info("Blocking attempt to set camera entity to: {}", entity);
                }
            }
        }
    }
    
    /**
     * Determine if our custom camera control should be active
     */
    private boolean isCustomCameraActive() {
        // Check if camera system is already active
        if (CameraSystem.getInstance().isCameraActive()) {
            return true;
        }
        
        // Check for free camera movement modes
        boolean isFreeCameraMode = 
            CameraController.currentKeyMoveMode == AbstractMovementSettings.POST_MOVE_KEYS.MOVE_CAMERA_FREE || 
            CameraController.currentKeyMoveMode == AbstractMovementSettings.POST_MOVE_KEYS.MOVE_CAMERA_FLAT;
            
        // Check for mouse rotation mode
        boolean isMouseRotationMode = 
            CameraController.currentMouseMoveMode == AbstractMovementSettings.POST_MOVE_MOUSE.ROTATE_CAMERA;
            
        return isFreeCameraMode || isMouseRotationMode;
    }
}
