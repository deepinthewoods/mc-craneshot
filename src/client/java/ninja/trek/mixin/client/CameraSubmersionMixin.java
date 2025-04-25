package ninja.trek.mixin.client;

import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.render.Camera;
import net.minecraft.client.MinecraftClient;
import ninja.trek.CameraController;
import ninja.trek.OrthographicCameraManager;
import ninja.trek.camera.CameraSystem;
import ninja.trek.cameramovements.AbstractMovementSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public class CameraSubmersionMixin {
    /**
     * Prevents fluid fog when in freecam mode with shift held
     */
    @Inject(method = "getSubmersionType", at = @At("HEAD"), cancellable = true)
    private void disableFluidFog(CallbackInfoReturnable<CameraSubmersionType> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.options != null && client.options.sneakKey.isPressed()) {
            // Check if camera system is active
            CameraSystem cameraSystem = CameraSystem.getInstance();
            if (cameraSystem.isCameraActive()) {
                cir.setReturnValue(CameraSubmersionType.NONE);
                return;
            }
            
            // Legacy camera mode check
            boolean isSpecialCameraMode = 
                CameraController.currentKeyMoveMode == AbstractMovementSettings.POST_MOVE_KEYS.MOVE_CAMERA_FREE ||
                CameraController.currentKeyMoveMode == AbstractMovementSettings.POST_MOVE_KEYS.MOVE_CAMERA_FLAT ||
                OrthographicCameraManager.isOrthographicMode() ||
                CameraController.currentEndTarget == AbstractMovementSettings.END_TARGET.HEAD_BACK ||
                CameraController.currentEndTarget == AbstractMovementSettings.END_TARGET.VELOCITY_BACK ||
                CameraController.currentEndTarget == AbstractMovementSettings.END_TARGET.FIXED_BACK;
                
            if (isSpecialCameraMode) {
                cir.setReturnValue(CameraSubmersionType.NONE);
            }
        }
    }
}