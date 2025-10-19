package ninja.trek.mixin.client;

import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import net.minecraft.client.MinecraftClient;
import ninja.trek.CameraController;
import ninja.trek.CraneshotClient;
import ninja.trek.camera.CameraSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// CameraMixin.java
@Mixin(Camera.class)
public class CameraMixin {
    private boolean wasCustomCameraActive = false;
    private float previousTickDelta = 0.0f;
    
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
                // Force set camera entity to player
                client.setCameraEntity(client.player);
            }
        } else if (!wasCustomCameraActive && isCustomCameraActive) {
            // The camera just became active
            MinecraftClient client = MinecraftClient.getInstance();
        }
        // No per-frame debug logging here; rely on targeted Diag logs

        // Remember the current state for next time
        wasCustomCameraActive = isCustomCameraActive;
        
        // Compute per-frame delta seconds using successive tickDelta values
        // tickDelta ranges [0,1) within each tick; difference approximates frame time in ticks
        float deltaTicks = tickDelta - previousTickDelta;
        if (deltaTicks < 0.0f) {
            // Tick boundary: wrap around
            deltaTicks += 1.0f;
        }
        float frameSeconds = deltaTicks / 20.0f;
        previousTickDelta = tickDelta;
        CraneshotClient.CAMERA_CONTROLLER.handleCameraUpdate(area, focusedEntity, thirdPerson, inverseView, frameSeconds, (Camera)(Object)this);
    }
}
