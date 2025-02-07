package ninja.trek.mixin.client;

import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.option.GameOptions;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {
    @Shadow
    @Final
    private GameOptions settings;
    private boolean disabled = false;
    private float savedForward, savedSideways;
    private boolean savedJumping, savedSneaking;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void onTick(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        if (disabled) {
            this.movementForward = savedForward;
            this.movementSideways = savedSideways;
            this.jumping = savedJumping;
            this.sneaking = savedSneaking;
            ci.cancel();
        }
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
        if (disabled) {
            this.savedForward = this.movementForward;
            this.savedSideways = this.movementSideways;
            this.savedJumping = this.jumping;
            this.savedSneaking = this.sneaking;
        }
    }
}