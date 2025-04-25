package ninja.trek.mixin.client;

import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor for GameRenderer's private fields.
 */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    /**
     * Gets the zoom level.
     */
    @Accessor("zoom")
    float getZoom();

    /**
     * Gets the zoom X offset.
     */
    @Accessor("zoomX")
    float getZoomX();

    /**
     * Gets the zoom Y offset.
     */
    @Accessor("zoomY")
    float getZoomY();
}
