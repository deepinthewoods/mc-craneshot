package ninja.trek.mixin.client;

import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface GameRendererFovAccessor {
    @Accessor("fovMultiplier")
    float getFovMultiplier();
}

