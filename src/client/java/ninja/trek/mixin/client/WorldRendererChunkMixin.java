package ninja.trek.mixin.client;

import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.BlockPos;
import ninja.trek.CraneshotClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ensures all chunks are considered ready for rendering in orthographic mode
 */
@Mixin(WorldRenderer.class)
public class WorldRendererChunkMixin {
    
    /**
     * When in orthographic mode, we want to ensure that chunks are always
     * considered ready for rendering regardless of their actual state.
     * This helps ensure consistent rendering in orthographic camera mode.
     */
    @Inject(method = "isRenderingReady", at = @At("HEAD"), cancellable = true)
    private void alwaysRenderInOrthoMode(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (CraneshotClient.MOVEMENT_MANAGER.isOrthographicMode()) {
            // In orthographic mode, we want to render everything
            cir.setReturnValue(true);
        }
    }
}