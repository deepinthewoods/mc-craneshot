package ninja.trek.mixin.client;

import net.minecraft.client.render.GameRenderer;
import ninja.trek.OrthographicCameraManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin for GameRenderer to handle block outline rendering in orthographic mode
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
    
    /**
     * Control block outline rendering in orthographic mode
     * This injection targets the private shouldRenderBlockOutline method
     */
    @Inject(method = "shouldRenderBlockOutline", at = @At("RETURN"), cancellable = true)
    private void forceRenderBlockOutline(CallbackInfoReturnable<Boolean> cir) {
        // In orthographic mode, we want to ensure block outlines are visible
        // This helps with block selection and visualization
        if (OrthographicCameraManager.isOrthographicMode()) {
            // Enable block outlines in orthographic mode
            cir.setReturnValue(true);
        }
    }
}