package ninja.trek.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import ninja.trek.CraneshotClient;
import ninja.trek.camera.CameraSystem;
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
    @Inject(method = "shouldRenderBlockOutline", at = @At("RETURN"), cancellable = true)
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
    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    private void onRenderHand(Camera camera, float tickDelta, Matrix4f matrix4f, CallbackInfo ci) {
        CameraSystem cameraSystem = CameraSystem.getInstance();
        
        // Cancel hand rendering if camera system is active and says not to render hands
        if (cameraSystem.isCameraActive() && !cameraSystem.shouldRenderHands()) {
            ci.cancel();
        }
    }
}