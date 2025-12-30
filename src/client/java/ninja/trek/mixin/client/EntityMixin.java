package ninja.trek.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import ninja.trek.MouseInterceptor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "changeLookDirection(DD)V", at = @At("HEAD"), cancellable = true)
    private void craneshot$blockPlayerLookChange(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if (!MouseInterceptor.isIntercepting()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return;
        }

        if ((Object) this == client.player) {
            ci.cancel();
        }
    }
}
