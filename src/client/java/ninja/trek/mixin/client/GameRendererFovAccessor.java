package ninja.trek.mixin.client;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Camera.class)
public interface GameRendererFovAccessor {
    @Accessor("fovModifier")
    float getFovMultiplier();
}
