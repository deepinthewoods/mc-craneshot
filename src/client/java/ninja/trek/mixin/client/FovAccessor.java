package ninja.trek.mixin.client;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface FovAccessor {
    @Accessor("fovModifier")
    float getFovModifier();

    @Accessor("fovModifier")
    void setFovModifier(float modifier);

    @Accessor("oldFovModifier")
    float getLastFovModifier();

    @Accessor("oldFovModifier")
    void setLastFovModifier(float modifier);
}
