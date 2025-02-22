package ninja.trek.mixin.client;

import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import ninja.trek.IKeyboardInputMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input implements IKeyboardInputMixin {
    private boolean disabled = false;
    private float savedForward, savedSideways;
    private boolean savedJumping, savedSneaking;
    private boolean savedSprinting;  // Add sprinting state

    @Override
    public void setDisabled(boolean disabled) {
        if (this.disabled != disabled) {
            if (disabled) {
                // Store current state when disabling
                this.savedForward = this.movementForward;
                this.savedSideways = this.movementSideways;
                this.savedJumping = this.jumping;
                this.savedSneaking = this.sneaking;


                // Immediately clear all movement
                this.movementForward = 0;
                this.movementSideways = 0;
                this.jumping = false;
                this.sneaking = false;

            }
            this.disabled = disabled;
        }
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void onTick(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        if (disabled) {
            // Force all movement values to zero
            this.movementForward = 0;
            this.movementSideways = 0;
            this.jumping = false;
            this.sneaking = false;

            ci.cancel();
        } else {
            // Restore saved state when not disabled
            this.movementForward = savedForward;
            this.movementSideways = savedSideways;
            this.jumping = savedJumping;
            this.sneaking = savedSneaking;

        }
    }
}