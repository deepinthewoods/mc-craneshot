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
    @Shadow private double eventDeltaVerticalWheel;
    @Shadow private double cursorDeltaX;
    @Shadow private double cursorDeltaY;

    private boolean interceptMouse = false;
    private double lastDeltaX;
    private double lastDeltaY;

    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void onMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
        if (interceptMouse) {
            ci.cancel();
        }
    }

    @Inject(method = "onCursorPos", at = @At("HEAD"))
    private void onCursorPos(long window, double x, double y, CallbackInfo ci) {
        if (!interceptMouse) {
            lastDeltaX = cursorDeltaX;
            lastDeltaY = cursorDeltaY;
        }
    }

    @Inject(method = "updateMouse", at = @At("HEAD"), cancellable = true)
    private void onUpdateMouse(CallbackInfo ci) {
        if (interceptMouse) {
            cursorDeltaX = 0;
            cursorDeltaY = 0;
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