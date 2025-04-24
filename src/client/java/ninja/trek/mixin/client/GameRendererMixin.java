package ninja.trek.mixin.client;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ninja.trek.CraneshotClient;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Unique
    private double customFov = 0.0;

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void onGetFov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        if (CraneshotClient.MOVEMENT_MANAGER != null &&
                CraneshotClient.MOVEMENT_MANAGER.hasActiveMovement()) {
            // Get original FOV and add our custom adjustment
            double originalFov = cir.getReturnValue();
            double newFov = originalFov + this.customFov;
            cir.setReturnValue((float)newFov);
        }
    }

    @Unique
    public void setCustomFov(double fov) {
        // Store the FOV adjustment directly as a double
        this.customFov = fov - 70.0;
    }
}