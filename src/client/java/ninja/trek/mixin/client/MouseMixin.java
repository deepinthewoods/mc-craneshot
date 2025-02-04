package ninja.trek.mixin.client;

import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ninja.trek.CraneshotClient;

@Mixin(Mouse.class)
public class MouseMixin {
    @Shadow private double eventDeltaVerticalWheel;

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
}