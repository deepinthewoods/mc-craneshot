package ninja.trek.mixin.client;

import net.minecraft.client.Mouse;
import ninja.trek.CraneshotClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseMixin {
    @Shadow
    private double eventDeltaVerticalWheel;
    @Shadow private double cursorDeltaX;
    @Shadow private double cursorDeltaY;

    private boolean interceptMouse = false;
    private double lastDeltaX;
    private double lastDeltaY;

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void onScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (CraneshotClient.selectMovementType.isPressed() ||
                CraneshotClient.cameraKeyBinds[0].isPressed() ||
                CraneshotClient.cameraKeyBinds[1].isPressed() ||
                CraneshotClient.cameraKeyBinds[2].isPressed()) {

            eventDeltaVerticalWheel = vertical;
            ci.cancel();
        }
    }

    @Inject(method = "updateMouse", at = @At("HEAD"), cancellable = true)
    private void onUpdateMouse(double timeDelta, CallbackInfo ci) {
        if (interceptMouse) {
            lastDeltaX = cursorDeltaX;
            lastDeltaY = cursorDeltaY;
            ci.cancel();
        }
    }

    public void setInterceptMouse(boolean intercept) {
        this.interceptMouse = intercept;
    }

    public double getLastDeltaX() {
        return lastDeltaX;
    }

    public double getLastDeltaY() {
        return lastDeltaY;
    }
}