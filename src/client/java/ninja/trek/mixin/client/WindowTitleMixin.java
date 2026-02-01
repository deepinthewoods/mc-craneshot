package ninja.trek.mixin.client;

import net.minecraft.client.Minecraft;
import ninja.trek.config.FollowerMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class WindowTitleMixin {
    @Inject(method = "updateTitle", at = @At("RETURN"))
    private void craneshot$overrideWindowTitle(CallbackInfo ci) {
        if (FollowerMode.isFollower()) {
            Minecraft.getInstance().getWindow().setTitle("MC Follower " + FollowerMode.getFollowerIndex());
        }
    }
}
