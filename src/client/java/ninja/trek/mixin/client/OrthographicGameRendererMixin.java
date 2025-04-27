package ninja.trek.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import ninja.trek.CraneshotClient;
import ninja.trek.OrthographicCameraManager;
import ninja.trek.cameramovements.CameraTarget;
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
     * Also handles blending between perspective and orthographic projection during camera movements.
     */
    @Inject(method = "getBasicProjectionMatrix", at = @At("RETURN"), cancellable = true)
    private void onGetBasicProjectionMatrix(float fovDegrees, CallbackInfoReturnable<Matrix4f> cir) {
        // Get current camera target to check ortho factor
        CameraTarget currentTarget = CraneshotClient.MOVEMENT_MANAGER.getCurrentTarget();
        float orthoFactor = 0.0f;
        
        // Check if we have an active camera movement with ortho factor
        if (currentTarget != null) {
            orthoFactor = currentTarget.getOrthoFactor();
            ninja.trek.Craneshot.LOGGER.info("Current ortho factor: {}", orthoFactor);
        }
        
        // Apply orthographic projection if either global ortho mode is enabled or if we have a non-zero ortho factor
        if (OrthographicCameraManager.isOrthographicMode() || orthoFactor > 0.001f) {
            ninja.trek.Craneshot.LOGGER.info("Applying orthographic projection, orthoFactor={}, global={}", 
                                            orthoFactor, OrthographicCameraManager.isOrthographicMode());
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
            
            // Get the original perspective matrix from the return value
            Matrix4f perspectiveMatrix = cir.getReturnValue();
            
            // If we're using blended projection from camera movement
            if (currentTarget != null && orthoFactor > 0.001f && orthoFactor < 0.999f) {
                // Create orthographic projection
                Matrix4f orthoMatrix = new Matrix4f();
                
                // Calculate distance-adaptive scale
                float baseScale = scale;
                if (currentTarget != null && this.client.player != null) {
                    // Use distance to player to match orthographic view with perspective view
                    double distanceToPlayer = currentTarget.getPosition().distanceTo(this.client.player.getEyePos());
                    
                    // Calculate a stable scale factor based on FOV and distance
                    // This makes the orthographic view match what the player sees in perspective
                    float fovRadians = (float) Math.toRadians(fovDegrees);
                    baseScale = (float)(Math.tan(fovRadians/2) * distanceToPlayer * 2.0f);
                    
                    // Make sure we get a safe value for the scale factor
                    baseScale = (float)Math.max(1.0f, Math.min(100.0f, baseScale)); 
                    
                    ninja.trek.Craneshot.LOGGER.debug("Distance to player: {}, FOV: {}, calculated baseScale: {}", 
                            distanceToPlayer, fovDegrees, baseScale);
                }
                
                // The scale affects how much of the world is visible (larger = more zoomed out)
                float width = baseScale * aspectRatio;
                float height = baseScale;
                
                // Apply zoom transformation if needed
                if (zoom != 1.0F) {
                    orthoMatrix.translate(accessor.getZoomX(), -accessor.getZoomY(), 0.0F);
                    orthoMatrix.scale(zoom, zoom, 1.0F);
                }
                
                // Create orthographic projection with safe values
                try {
                    // Ensure positive non-zero values for width and height
                    float safeWidth = Math.max(0.1f, width * 2);
                    float safeHeight = Math.max(0.1f, height * 2);
                    
                    orthoMatrix.ortho(
                        -safeWidth, safeWidth,             // left, right 
                        -safeHeight, safeHeight,           // bottom, top 
                        0.1F,                              // safe near plane that's not too close
                        Math.max(100.0F, baseScale * 50)  // safe far plane
                    );
                    
                    ninja.trek.Craneshot.LOGGER.debug("Created ortho matrix with width={}, height={}, near=0.1, far={}",
                                              safeWidth, safeHeight, Math.max(100.0F, baseScale * 50));
                } catch (Exception e) {
                    ninja.trek.Craneshot.LOGGER.error("Error creating orthographic matrix: {}", e.getMessage());
                    return; // Skip orthographic rendering if there's an error
                }
                
                // Blend between perspective and orthographic matrices
                // This is a basic approximation but should provide a smooth transition
                // Use temporary matrices to avoid modifying the originals
                try {
                    Matrix4f blendedMatrix = new Matrix4f();
                    blendedMatrix.set(perspectiveMatrix);
                    
                    // Ensure orthoFactor is within valid range to prevent NaN issues
                    float safeOrthoFactor = Math.max(0.0f, Math.min(1.0f, orthoFactor));
                    blendedMatrix.lerp(orthoMatrix, safeOrthoFactor);
                    
                    ninja.trek.Craneshot.LOGGER.info("Blending ortho projection with factor: {}", safeOrthoFactor);
                    cir.setReturnValue(blendedMatrix);
                } catch (Exception e) {
                    ninja.trek.Craneshot.LOGGER.error("Error blending matrices: {}", e.getMessage());
                    // Fall back to the original perspective matrix if blending fails
                    cir.setReturnValue(perspectiveMatrix);
                }
            } 
            else if (orthoFactor >= 0.999f || OrthographicCameraManager.isOrthographicMode()) {
                // Full orthographic projection for either global ortho mode or full ortho factor
                // Calculate distance-adaptive scale
                float baseScale = scale;
                if (currentTarget != null && this.client.player != null) {
                    // Use distance to player to match orthographic view with perspective view
                    double distanceToPlayer = currentTarget.getPosition().distanceTo(this.client.player.getEyePos());
                    
                    // Calculate a stable scale factor based on FOV and distance
                    // This makes the orthographic view match what the player sees in perspective
                    float fovRadians = (float) Math.toRadians(fovDegrees);
                    baseScale = (float)(Math.tan(fovRadians/2) * distanceToPlayer * 2.0f);
                    
                    // Make sure we get a safe value for the scale factor
                    baseScale = (float)Math.max(1.0f, Math.min(100.0f, baseScale)); 
                    
                    ninja.trek.Craneshot.LOGGER.debug("Full ortho - Distance to player: {}, FOV: {}, calculated baseScale: {}", 
                            distanceToPlayer, fovDegrees, baseScale);
                }
                
                // The scale affects how much of the world is visible (larger = more zoomed out)
                float width = baseScale * aspectRatio;
                float height = baseScale;
                
                // Make the view frustum extremely large to prevent any culling
                // Use safe near and far planes to avoid infinity/NaN issues
                try {
                    // Ensure positive non-zero values for width and height
                    float safeWidth = Math.max(0.1f, width * 2);
                    float safeHeight = Math.max(0.1f, height * 2);
                    
                    matrix.ortho(
                        -safeWidth, safeWidth,             // left, right 
                        -safeHeight, safeHeight,           // bottom, top 
                        0.1F,                              // safe near plane that's not too close
                        Math.max(100.0F, baseScale * 50)  // safe far plane
                    );
                    
                    ninja.trek.Craneshot.LOGGER.debug("Created full ortho matrix with width={}, height={}, near=0.1, far={}",
                                               safeWidth, safeHeight, Math.max(100.0F, baseScale * 50));
                } catch (Exception e) {
                    ninja.trek.Craneshot.LOGGER.error("Error creating full orthographic matrix: {}", e.getMessage());
                    return; // Skip orthographic rendering if there's an error
                }
                
                ninja.trek.Craneshot.LOGGER.info("Applying full orthographic projection");
                cir.setReturnValue(matrix);
            }
            
            // Make sure chunk culling is disabled for any ortho mode
            this.client.chunkCullingEnabled = false;
        }
    }
}