package ninja.trek.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.client.Mouse;

@Mixin(Mouse.class)
public interface MouseAccessor {
    @Accessor("eventDeltaVerticalWheel")
    double getEventDeltaVerticalWheel();

    @Accessor("eventDeltaVerticalWheel")
    void setEventDeltaVerticalWheel(double value);
}