package ninja.trek.mixin.client;

import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface FovAccessor {
    @Accessor("fovMultiplier")
    float getFovModifier();

    @Accessor("fovMultiplier")
    void setFovModifier(float modifier);

    @Accessor("lastFovMultiplier")
    float getLastFovModifier();

    @Accessor("lastFovMultiplier")
    void setLastFovModifier(float modifier);
}