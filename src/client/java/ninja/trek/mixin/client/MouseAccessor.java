package ninja.trek.mixin.client;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MouseHandler.class)
public interface MouseAccessor {
    @Accessor("accumulatedDX")
    double getCursorDeltaX();

    @Accessor("accumulatedDY")
    double getCursorDeltaY();

    @Accessor("accumulatedDX")
    void setCursorDeltaX(double value);

    @Accessor("accumulatedDY")
    void setCursorDeltaY(double value);
}
