package ninja.trek.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.option.Perspective;
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
 * Mixin for GameRenderer to handle block outline rendering 
 * and to control hand rendering based on camera distance
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Shadow @Final private MinecraftClient client;
    @org.spongepowered.asm.mixin.Unique
    private boolean craneshotForcedThirdPerson = false;
    @org.spongepowered.asm.mixin.Unique
    private Perspective craneshotPrevPerspective = Perspective.FIRST_PERSON;
    
    /**
     * Control block outline rendering 
     * This injection targets the private shouldRenderBlockOutline method
     */
    
    
    /**
     * Control hand rendering based on camera distance threshold
     * If camera is closer than threshold, render hands; if further, don't render hands
     */
    @Inject(method = "renderHand(FZLorg/joml/Matrix4f;)V", at = @At("HEAD"), cancellable = true)
    private void onRenderHand(float tickDelta, boolean renderHand, Matrix4f matrix4f, CallbackInfo ci) {
        // Respect camera system flags only; do not force perspective changes
        CameraSystem cameraSystem = CameraSystem.getInstance();
        if (cameraSystem.isCameraActive() && !cameraSystem.shouldRenderHands()) {
            ci.cancel();
            return;
        }
    }
}


