package ninja.trek.mixin.client;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface GameRendererFovAccessor {
    @Accessor("fovModifier")
    float getFovMultiplier();
}

