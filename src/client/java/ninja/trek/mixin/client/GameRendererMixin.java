package ninja.trek.mixin.client;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin implements FovAccessor {
    @Shadow private float zoom;
    @Shadow private float zoomX;
    @Shadow private float zoomY;

    @Unique
    private float customFov = 70.0F;

    @Inject(method = "getFov(Lnet/minecraft/client/render/Camera;FZ)D", at = @At("HEAD"), cancellable = true)
    private void onGetFov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
        if (changingFov) {
            cir.setReturnValue((double)customFov);
        }
    }

    @Override
    public void setCustomFov(float fov) {
        this.customFov = fov;
    }

    @Override
    public float getCustomFov() {
        return this.customFov;
    }

    @Override
    public void setZoom(float zoom, float x, float y) {
        this.zoom = zoom;
        this.zoomX = x;
        this.zoomY = y;
    }
}