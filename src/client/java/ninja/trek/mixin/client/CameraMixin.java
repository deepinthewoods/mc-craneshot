package ninja.trek.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import ninja.trek.CraneshotClient;
import ninja.trek.camera.CameraSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public class CameraMixin {
    private boolean wasCustomCameraActive = false;
    private float previousTickDelta = 0.0f;

    @Inject(method = "setup", at = @At("TAIL"))
    private void onCameraUpdate(Level area, Entity focusedEntity, boolean thirdPerson,
                                boolean inverseView, float tickDelta, CallbackInfo ci) {
        CameraSystem cameraSystem = CameraSystem.getInstance();
        boolean isCustomCameraActive = cameraSystem.isCameraActive();

        // Detect transitions between active and inactive camera
        if (wasCustomCameraActive && !isCustomCameraActive) {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.player != null) {
                client.setCameraEntity(client.player);
            }
        }

        wasCustomCameraActive = isCustomCameraActive;

        // Compute per-frame delta seconds using successive tickDelta values
        float deltaTicks = tickDelta - previousTickDelta;
        if (deltaTicks < 0.0f) {
            deltaTicks += 1.0f;
        }
        float frameSeconds = deltaTicks / 20.0f;
        previousTickDelta = tickDelta;
        CraneshotClient.CAMERA_CONTROLLER.handleCameraUpdate(area, focusedEntity, thirdPerson, inverseView, tickDelta, frameSeconds, (Camera)(Object)this);
    }

    /**
     * Override isDetached() to control hand/body rendering based on camera distance.
     * When our camera is active: far = detached (body renders, hands hidden),
     * close = not detached (hands render, body not in render list).
     */
    @Inject(method = "isDetached", at = @At("RETURN"), cancellable = true)
    private void onIsDetached(CallbackInfoReturnable<Boolean> cir) {
        CameraSystem cs = CameraSystem.getInstance();
        if (cs.isCameraActive()) {
            cir.setReturnValue(cs.isEffectivelyDetached());
        }
    }
}
