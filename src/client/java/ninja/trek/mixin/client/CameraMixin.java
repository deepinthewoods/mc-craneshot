package ninja.trek.mixin.client;

import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import net.minecraft.client.MinecraftClient;
import ninja.trek.CameraController;
import ninja.trek.CraneshotClient;
import ninja.trek.camera.CameraSystem;
import ninja.trek.Craneshot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// CameraMixin.java
@Mixin(Camera.class)
public class CameraMixin {
    private boolean wasCustomCameraActive = false;
    
    @Inject(method = "update", at = @At("TAIL"))
    private void onCameraUpdate(BlockView area, Entity focusedEntity, boolean thirdPerson,
                                boolean inverseView, float tickDelta, CallbackInfo ci) {
        CameraSystem cameraSystem = CameraSystem.getInstance();
        boolean isCustomCameraActive = cameraSystem.isCameraActive();
        
        // Detect transitions between active and inactive camera
        if (wasCustomCameraActive && !isCustomCameraActive) {
            // The camera just became inactive - force an update to restore default behavior
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.player != null) {
                Craneshot.LOGGER.info("CameraMixin: custom->vanilla transition detected; forcing camera entity to player. current={} focused={}",
                        client.getCameraEntity(), focusedEntity);
                // Force set camera entity to player
                client.setCameraEntity(client.player);
            }
        } else if (!wasCustomCameraActive && isCustomCameraActive) {
            // The camera just became active
            MinecraftClient client = MinecraftClient.getInstance();
            Craneshot.LOGGER.info("CameraMixin: vanilla->custom transition detected; currentCamEnt={}",
                    client != null ? client.getCameraEntity() : null);
        }
        
        // Extra per-frame debug context (lightweight)
        try {
            Camera cam = (Camera)(Object)this;
            Craneshot.LOGGER.debug(
                "CameraMixin.update tail: customActive={} camPos=({}, {}, {}) yaw={} pitch={} focused={} tickDelta={}",
                isCustomCameraActive,
                String.format("%.3f", cam.getPos().x),
                String.format("%.3f", cam.getPos().y),
                String.format("%.3f", cam.getPos().z),
                String.format("%.2f", cam.getYaw()),
                String.format("%.2f", cam.getPitch()),
                focusedEntity,
                String.format("%.3f", tickDelta)
            );
        } catch (Throwable ignore) { }

        // Remember the current state for next time
        wasCustomCameraActive = isCustomCameraActive;
        
        // Pass to the controller to handle camera updates
        CraneshotClient.CAMERA_CONTROLLER.handleCameraUpdate(area, focusedEntity, thirdPerson, inverseView, tickDelta, (Camera)(Object)this);
    }
}
