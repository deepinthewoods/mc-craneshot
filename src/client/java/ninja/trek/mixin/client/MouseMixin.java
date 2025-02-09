package ninja.trek.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import ninja.trek.Craneshot;
import ninja.trek.CraneshotClient;
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
    @Shadow private double eventDeltaVerticalWheel;
    private double capturedDeltaX;
    private double capturedDeltaY;

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();

        // Always allow scrolling if any screen is open (game menu, mod menu, etc)
        if (client.currentScreen != null) {
            return;
        }

        // Otherwise, check if we should intercept
        boolean shouldIntercept = CraneshotClient.MOVEMENT_MANAGER.hasActiveMovement();

        // Also intercept if any camera slot key or select movement key is pressed
        if (!shouldIntercept) {
            for (int i = 0; i < CraneshotClient.cameraKeyBinds.length; i++) {
                if (CraneshotClient.cameraKeyBinds[i].isPressed()) {
                    shouldIntercept = true;
                    break;
                }
            }
            if (CraneshotClient.selectMovementType.isPressed()) {
                shouldIntercept = true;
            }
        }

        // If we should intercept this scroll, store the value BEFORE cancelling
        if (shouldIntercept) {
            this.eventDeltaVerticalWheel = vertical * 15.0; // Match Minecraft's scroll multiplier
            ci.cancel();
        }
    }

    @Inject(method = "updateMouse", at = @At("HEAD"), cancellable = true)
    private void onUpdateMouse(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
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