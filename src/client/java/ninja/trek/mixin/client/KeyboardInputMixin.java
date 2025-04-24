package ninja.trek.mixin.client;

import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import ninja.trek.IKeyboardInputMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input implements IKeyboardInputMixin {
    private boolean disabled = false;
    private PlayerInput savedInput;
    private Vec2f savedMovementVector;

    @Override
    public void setDisabled(boolean disabled) {
        if (this.disabled != disabled) {
            if (disabled) {
                // Store current state when disabling
                this.savedInput = this.playerInput;
                this.savedMovementVector = this.movementVector;

                // Immediately clear all movement
                this.playerInput = PlayerInput.DEFAULT;
                this.movementVector = Vec2f.ZERO;
            }
            this.disabled = disabled;
        }
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void onTick(CallbackInfo ci) {
        if (disabled) {
            // Force all movement values to zero
            this.playerInput = PlayerInput.DEFAULT;
            this.movementVector = Vec2f.ZERO;
            ci.cancel();
        } else if (savedInput != null) {
            // Restore saved state when not disabled
            this.playerInput = this.savedInput;
            this.movementVector = this.savedMovementVector;
        }
    }
}