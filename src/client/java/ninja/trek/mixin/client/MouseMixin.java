package ninja.trek.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import ninja.trek.Craneshot;
import ninja.trek.CraneshotClient;
import ninja.trek.IMouseMixin;
import ninja.trek.MouseInterceptor;
import ninja.trek.cameramovements.AbstractMovementSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseMixin implements IMouseMixin {
    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;

    private double capturedDeltaX;
    private double capturedDeltaY;
    private double lastScrollValue;

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();

        // Always allow scrolling if any screen is open (game menu, mod menu, etc)
        if (client.screen != null) {
            return;
        }

        // Otherwise, check if we should intercept
        boolean shouldIntercept = false;
        if (CraneshotClient.MOVEMENT_MANAGER.hasActiveMovement()) {
            AbstractMovementSettings.SCROLL_WHEEL scrollMode =
                    CraneshotClient.MOVEMENT_MANAGER.getActiveMouseWheelMode();
            shouldIntercept = scrollMode != AbstractMovementSettings.SCROLL_WHEEL.NONE;
        }

        // Also intercept if any camera slot key or select movement key is pressed
        if (!shouldIntercept) {
            for (int i = 0; i < CraneshotClient.cameraKeyBinds.length; i++) {
                if (CraneshotClient.cameraKeyBinds[i].isDown()) {
                    shouldIntercept = true;
                    break;
                }
            }
            if (CraneshotClient.selectMovementType.isDown()) {
                shouldIntercept = true;
            }
        }

        // If we should intercept this scroll, store the value BEFORE cancelling
        if (shouldIntercept) {
            this.lastScrollValue = vertical * 15.0; // Match Minecraft's scroll multiplier
            ci.cancel();
        }
    }

    @Inject(method = "handleAccumulatedMovement", at = @At("HEAD"), cancellable = true)
    private void onUpdateMouse(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();

        // Adjust mouse sensitivity based on zoom level BEFORE any other processing
        if (client.screen == null && !MouseInterceptor.isIntercepting()) {
            if (CraneshotClient.MOVEMENT_MANAGER != null) {
                ninja.trek.cameramovements.movements.ZoomMovement zoomMovement =
                    CraneshotClient.MOVEMENT_MANAGER.getActiveZoomOverlay();

                // Check if zoom overlay is active
                if (zoomMovement != null) {
                    // Get the current FOV multiplier (e.g., 0.1 for 10x zoom)
                    float fovMultiplier = zoomMovement.getTargetZoomFov();

                    // Scale mouse deltas by FOV multiplier to reduce sensitivity when zoomed in
                    accumulatedDX *= fovMultiplier;
                    accumulatedDY *= fovMultiplier;
                }
            }
        }

        // Handle mouse interception for camera control
        if (MouseInterceptor.isIntercepting() && client.screen == null) {
            capturedDeltaX = accumulatedDX;
            capturedDeltaY = accumulatedDY;
            accumulatedDX = 0;
            accumulatedDY = 0;
            ci.cancel();
        }
    }

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void onMouseButton(long window, net.minecraft.client.input.MouseButtonInfo input, int action, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (MouseInterceptor.isIntercepting() && client.screen == null) {
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

    @Override
    public double getLastScrollValue() {
        return lastScrollValue;
    }

    @Override
    public void setLastScrollValue(double value) {
        this.lastScrollValue = value;
    }
}
