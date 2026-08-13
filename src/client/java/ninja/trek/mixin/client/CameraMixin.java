package ninja.trek.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
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

    @Inject(method = "update(Lnet/minecraft/client/DeltaTracker;)V", at = @At("TAIL"))
    private void onCameraUpdate(DeltaTracker deltaTracker, CallbackInfo ci) {
        CameraSystem cameraSystem = CameraSystem.getInstance();
        boolean isCustomCameraActive = cameraSystem.isCameraActive();
        Minecraft client = Minecraft.getInstance();

        // Detect transitions between active and inactive camera
        if (wasCustomCameraActive && !isCustomCameraActive) {
            if (client != null && client.player != null) {
                client.setCameraEntity(client.player);
            }
        }

        wasCustomCameraActive = isCustomCameraActive;

        if (client == null || client.level == null) return;
        Entity focusedEntity = client.getCameraEntity();
        if (focusedEntity == null) return;

        CameraType cameraType = client.options.getCameraType();
        float tickDelta = deltaTracker.getGameTimeDeltaPartialTick(true);
        float frameSeconds = deltaTracker.getRealtimeDeltaTicks() / 20.0f;
        CraneshotClient.CAMERA_CONTROLLER.handleCameraUpdate(
                client.level,
                focusedEntity,
                !cameraType.isFirstPerson(),
                cameraType.isMirrored(),
                tickDelta,
                frameSeconds,
                (Camera) (Object) this
        );
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
