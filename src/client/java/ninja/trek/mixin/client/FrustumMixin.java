package ninja.trek.mixin.client;

import net.minecraft.client.render.Frustum;
import net.minecraft.util.math.Box;
import ninja.trek.OrthographicCameraManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * This mixin completely disables frustum culling for orthographic camera mode
 * by making all boxes visible regardless of their position relative to the frustum.
 */
@Mixin(Frustum.class)
public class FrustumMixin {
    
    /**
     * Force frustum.isVisible(Box) to always return true in orthographic mode
     */
    @Inject(method = "isVisible(Lnet/minecraft/util/math/Box;)Z", at = @At("HEAD"), cancellable = true)
    private void alwaysVisibleInOrthoMode(Box box, CallbackInfoReturnable<Boolean> cir) {
        // If we're in orthographic mode, everything is always visible
        if (OrthographicCameraManager.isOrthographicMode()) {
            cir.setReturnValue(true);
        }
    }
}