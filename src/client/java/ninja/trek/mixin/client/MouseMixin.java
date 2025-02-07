// File: client/java/ninja/trek/mixin/client/MouseMixin.java
package ninja.trek.mixin.client;

import net.minecraft.client.Mouse;
import ninja.trek.IMouseMixin;
import ninja.trek.MouseInterceptor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseMixin implements IMouseMixin {
    @Shadow private double cursorDeltaX;
    @Shadow private double cursorDeltaY;

    // Remove the static field and methods from here.

    // Instance fields for captured values.
    private double capturedDeltaX;
    private double capturedDeltaY;

    @Inject(method = "updateMouse", at = @At("HEAD"), cancellable = true)
    private void onUpdateMouse(CallbackInfo ci) {
        if (MouseInterceptor.isIntercepting()) { // use the helper class instead
            // Capture the deltas before they're cleared
            capturedDeltaX = cursorDeltaX;
            capturedDeltaY = cursorDeltaY;
            // Clear the actual deltas to prevent normal camera movement
            cursorDeltaX = 0;
            cursorDeltaY = 0;
            ci.cancel();
        }
    }

    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void onMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
        if (MouseInterceptor.isIntercepting()) { // use the helper class
            ci.cancel();
        }
    }

    @Override
    public double getCapturedDeltaX() {
        return capturedDeltaX;
    }

    @Override
    public double getCapturedDeltaY() {
        return capturedDeltaY;
    }
}
