package ninja.trek.mixin.client;

import net.minecraft.client.MinecraftClient;
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

    private double capturedDeltaX;
    private double capturedDeltaY;

    @Inject(method = "updateMouse", at = @At("HEAD"), cancellable = true)
    private void onUpdateMouse(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        // Only intercept mouse movement if we're intercepting AND not in a screen
        if (MouseInterceptor.isIntercepting() && client.currentScreen == null) {
            capturedDeltaX = cursorDeltaX;
            capturedDeltaY = cursorDeltaY;
            cursorDeltaX = 0;
            cursorDeltaY = 0;
            ci.cancel();
        }
    }

    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void onMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        // Only intercept mouse clicks if we're intercepting AND not in a screen
        if (MouseInterceptor.isIntercepting() && client.currentScreen == null) {
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