package ninja.trek.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.client.Mouse;

@Mixin(Mouse.class)
public interface MouseAccessor {
    @Accessor("cursorDeltaX")
    double getCursorDeltaX();

    @Accessor("cursorDeltaY")
    double getCursorDeltaY();

    @Accessor("cursorDeltaX")
    void setCursorDeltaX(double value);

    @Accessor("cursorDeltaY")
    void setCursorDeltaY(double value);
}
