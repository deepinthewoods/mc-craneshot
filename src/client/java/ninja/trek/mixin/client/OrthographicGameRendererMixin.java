package ninja.trek.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import ninja.trek.OrthographicCameraManager;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class OrthographicGameRendererMixin {

    @Shadow @Final
    private MinecraftClient client;

    /**
     * Modifies the projection matrix to use orthographic projection when enabled.
     * Uses an extremely wide view to prevent culling at the edges.
     */
    @Inject(method = "getBasicProjectionMatrix", at = @At("RETURN"), cancellable = true)
    private void onGetBasicProjectionMatrix(float fovDegrees, CallbackInfoReturnable<Matrix4f> cir) {
        // Only modify if orthographic mode is enabled
        if (OrthographicCameraManager.isOrthographicMode()) {
            GameRenderer thisRenderer = (GameRenderer)(Object)this;
            GameRendererAccessor accessor = (GameRendererAccessor)thisRenderer;
            
            Matrix4f matrix = new Matrix4f();
            
            // Get window dimensions
            float aspectRatio = (float) this.client.getWindow().getFramebufferWidth() / this.client.getWindow().getFramebufferHeight();
            float scale = OrthographicCameraManager.getOrthoScale();
            
            // Skip if rendering panorama
            if (thisRenderer.isRenderingPanorama()) {
                return;
            }
            
            // Apply zoom transformation if needed
            float zoom = accessor.getZoom();
            if (zoom != 1.0F) {
                matrix.translate(accessor.getZoomX(), -accessor.getZoomY(), 0.0F);
                matrix.scale(zoom, zoom, 1.0F);
            }
            
            // Create orthographic projection
            // The scale affects how much of the world is visible (larger = more zoomed out)
            float width = scale * aspectRatio;
            float height = scale;
            
            // Make the view frustum extremely large to prevent any culling
            // Use very aggressive near and far planes
            matrix.ortho(
                -width * 2, width * 2,     // left, right (doubled to prevent culling)
                -height * 2, height * 2,    // bottom, top (doubled to prevent culling)
                -1000.0F,                  // extremely close near plane
                2000.0F                    // very far plane
            );
            
            cir.setReturnValue(matrix);
            
            // Make sure chunk culling is disabled
            this.client.chunkCullingEnabled = false;
        }
    }
}