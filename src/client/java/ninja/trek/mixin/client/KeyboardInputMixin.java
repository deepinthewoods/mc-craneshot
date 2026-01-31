package ninja.trek.mixin.client;

import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import ninja.trek.IKeyboardInputMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput implements IKeyboardInputMixin {
    private boolean disabled = false;
    private Input savedInput;
    private Vec2 savedMovementVector;

    @Override
    public void setDisabled(boolean disabled) {
        if (this.disabled != disabled) {
            if (disabled) {
                // Store current state when disabling
                this.savedInput = this.keyPresses;
                this.savedMovementVector = this.moveVector;

                // Immediately clear all movement
                this.keyPresses = Input.EMPTY;
                this.moveVector = Vec2.ZERO;
            }
            this.disabled = disabled;
        }
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void onTick(CallbackInfo ci) {
        if (disabled) {
            // Force all movement values to zero
            this.keyPresses = Input.EMPTY;
            this.moveVector = Vec2.ZERO;
            ci.cancel();
        } else if (savedInput != null) {
            // Restore saved state when not disabled
            this.keyPresses = this.savedInput;
            this.moveVector = this.savedMovementVector;
        }
    }
}