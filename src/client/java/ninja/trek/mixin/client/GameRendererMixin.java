package ninja.trek.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Camera;
import ninja.trek.CraneshotClient;
import ninja.trek.camera.CameraSystem;
import ninja.trek.Craneshot;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin for GameRenderer to handle block outline rendering in orthographic mode
 * and to control hand rendering based on camera distance
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
    
    @Shadow @Final private MinecraftClient client;
    
    /**
     * Control block outline rendering in orthographic mode
     * This injection targets the private shouldRenderBlockOutline method
     */
    @Inject(method = "shouldRenderBlockOutline()Z", at = @At("RETURN"), cancellable = true)
    private void forceRenderBlockOutline(CallbackInfoReturnable<Boolean> cir) {
        // In orthographic mode, we want to ensure block outlines are visible
        // This helps with block selection and visualization
        if (CraneshotClient.MOVEMENT_MANAGER.isOrthographicMode()) {
            // Enable block outlines in orthographic mode
            cir.setReturnValue(true);
        }
    }
    
    /**
     * Control hand rendering based on camera distance threshold
     * If camera is closer than threshold, render hands; if further, don't render hands
     */
    @Inject(method = "renderHand(FZLorg/joml/Matrix4f;)V", at = @At("HEAD"), cancellable = true)
    private void onRenderHand(float tickDelta, boolean renderHand, Matrix4f matrix4f, CallbackInfo ci) {
        // Always enforce distance-based gating: if camera is farther than threshold, don't render hands
        if (this.client != null && this.client.player != null) {
            Camera cam = this.client.gameRenderer.getCamera();
            if (cam != null) {
                double dist = cam.getPos().distanceTo(this.client.player.getEyePos());
                if (dist >= CameraSystem.PLAYER_RENDER_THRESHOLD) {
                    // Lightweight proof (guarded) to confirm firing without spamming
                    if (Math.random() < 0.01) {
                        Craneshot.LOGGER.debug("Cancelling hand render: distance {} >= threshold {}", dist, CameraSystem.PLAYER_RENDER_THRESHOLD);
                    }
                    ci.cancel();
                    return;
                }
            }
        }

        // Preserve camera-system explicit overrides (e.g., free-cam hiding hands entirely)
        CameraSystem cameraSystem = CameraSystem.getInstance();
        if (cameraSystem.isCameraActive() && !cameraSystem.shouldRenderHands()) {
            ci.cancel();
        }
    }
}
