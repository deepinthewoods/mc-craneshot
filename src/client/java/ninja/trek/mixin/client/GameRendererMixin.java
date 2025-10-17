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
        // Enforce distance-based gating universally based on camera distance
        if (this.client != null && this.client.player != null) {
            Camera cam = this.client.gameRenderer.getCamera();
            if (cam != null) {
                double dist = cam.getPos().distanceTo(this.client.player.getEyePos());
                if (dist >= CameraSystem.PLAYER_RENDER_THRESHOLD) {
                    // Lightweight proof (guarded) to confirm firing without spamming
                    if (Math.random() < 0.01) {
                        Craneshot.LOGGER.debug("Cancelling hand render: distance {} >= threshold {}", dist, CameraSystem.PLAYER_RENDER_THRESHOLD);
                    }
                    // Force third-person back so the player body renders when hands are hidden
                    if (this.client.options.getPerspective() != Perspective.THIRD_PERSON_BACK) {
                        craneshotPrevPerspective = this.client.options.getPerspective();
                        this.client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                        craneshotForcedThirdPerson = true;
                    }
                    ci.cancel();
                    return;
                }
                // If we previously forced third-person and we're now within threshold, restore perspective
                if (craneshotForcedThirdPerson) {
                    this.client.options.setPerspective(craneshotPrevPerspective);
                    craneshotForcedThirdPerson = false;
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


