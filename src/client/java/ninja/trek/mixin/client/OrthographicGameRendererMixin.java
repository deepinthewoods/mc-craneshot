package ninja.trek.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import ninja.trek.Craneshot;
import ninja.trek.CraneshotClient;
import ninja.trek.cameramovements.CameraTarget;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ninja.trek.IMouseMixin;

@Mixin(GameRenderer.class)
public class OrthographicGameRendererMixin {
    private static float orthoPanX = 0f;
    private static float orthoPanY = 0f;

    @Shadow @Final
    private MinecraftClient client;
    
    // Transition state - simplified to rely on camera movement's alpha
    private float transitionProgress = 0.0f;
    private boolean transitionTargetOrtho = false;

    /**
     * Updates the transition target state.
     * The actual transition progress is now derived directly from camera movement alpha.
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        boolean shouldBeOrtho = CraneshotClient.MOVEMENT_MANAGER.isOrthographicMode();
        
        // Just update the target state - no manual progress management
        if (shouldBeOrtho != transitionTargetOrtho) {
            transitionTargetOrtho = shouldBeOrtho;
            Craneshot.LOGGER.info("Setting projection transition target to: {}", 
                transitionTargetOrtho ? "orthographic" : "perspective");
        }

        // Reset panning offsets when leaving orthographic mode
        if (!shouldBeOrtho) {
            orthoPanX = 0f;
            orthoPanY = 0f;
        }
    }
    
    /**
     * Modifies the projection matrix to use orthographic projection when enabled.
     * Uses an extremely wide view to prevent culling at the edges.
     * Also handles blending between perspective and orthographic projection based on camera movement.
     */
    @Inject(method = "getBasicProjectionMatrix", at = @At("RETURN"), cancellable = true)
    private void onGetBasicProjectionMatrix(float fovDegrees, CallbackInfoReturnable<Matrix4f> cir) {
        // Get effective ortho factor
        float effectiveOrthoFactor = getEffectiveOrthoFactor();
        
        // If we're in full perspective mode with no transition happening, return early
        if (effectiveOrthoFactor <= 0.001f) {
            return;
        }

        // Special case: if rendering panorama, skip orthographic rendering
        GameRenderer thisRenderer = (GameRenderer)(Object)this;
        if (thisRenderer.isRenderingPanorama()) {
            return;
        }
        
        // Skip further processing if ortho factor is effectively zero
        if (effectiveOrthoFactor <= 0.001f) {
            return;
        }
        
        try {
            // Get the original perspective matrix from the return value
            Matrix4f perspectiveMatrix = cir.getReturnValue();
            if (perspectiveMatrix == null) {
                Craneshot.LOGGER.error("Perspective matrix is null, skipping orthographic projection");
                return;
            }
            
            // Create orthographic projection matrix
            Matrix4f orthoMatrix = createOrthographicMatrix(fovDegrees);
            if (orthoMatrix == null) {
                return; // Error already logged in createOrthographicMatrix
            }
            
            // If we're fully in orthographic mode (or almost), just use the ortho matrix
            if (effectiveOrthoFactor >= 0.999f) {
                cir.setReturnValue(orthoMatrix);
            } else {
                // Otherwise blend between perspective and orthographic matrices
                try {
                    Matrix4f blendedMatrix = new Matrix4f();
                    blendedMatrix.set(perspectiveMatrix);
                    blendedMatrix.lerp(orthoMatrix, effectiveOrthoFactor);
                    cir.setReturnValue(blendedMatrix);
                    
                    // Log occasionally when blending matrices
                    if (Math.random() < 0.01) { // Only log occasionally
                        Craneshot.LOGGER.debug("Blending with transition factor: {}", effectiveOrthoFactor);
                    }
                } catch (Exception e) {
                    Craneshot.LOGGER.error("Error blending matrices: {}", e.getMessage());
                    // Fall back to perspective matrix if blending fails
                    cir.setReturnValue(perspectiveMatrix);
                }
            }
            
            // Disable chunk culling for any amount of orthographic projection
            // This prevents chunks from disappearing at extreme angles
            this.client.chunkCullingEnabled = false;
        } catch (Exception e) {
            // Catch-all for any unexpected errors
            Craneshot.LOGGER.error("Critical error in orthographic projection: {}", e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Creates an orthographic projection matrix with appropriate safeguards
     * to prevent NaN values and extreme angles issues
     */
    private Matrix4f createOrthographicMatrix(float fovDegrees) {
        try {
            Matrix4f matrix = new Matrix4f();

            // Get window dimensions
            float aspectRatio = (float) this.client.getWindow().getFramebufferWidth() / 
                               (float) this.client.getWindow().getFramebufferHeight();
            float baseScale = calculateBaseScale(fovDegrees);

            // Apply FOV-based zoom if present (replacement for removed zoom fields)
            try {
                GameRenderer renderer = this.client.gameRenderer;
                float fovMul = ((GameRendererFovAccessor) (Object) renderer).getFovMultiplier();
                if (fovMul != 1.0F) {
                    matrix.scale(fovMul, fovMul, 1.0F);
                }
            } catch (Throwable ignored) {
                // Accessor failure should not break rendering
            }

            // Mouse-based panning while in orthographic mode (replacement for removed zoomX/zoomY)
            // Hold sneak (Shift) to pan the view when moving the mouse
            try {
                if (CraneshotClient.MOVEMENT_MANAGER.isOrthographicMode()
                        && this.client.currentScreen == null
                        && this.client.options.sneakKey.isPressed()) {
                    double dx;
                    double dy;
                    // Prefer captured deltas if our mouse mixin is active, else fall back to raw deltas
                    if (this.client.mouse instanceof IMouseMixin mm) {
                        dx = mm.getCapturedDeltaX();
                        dy = mm.getCapturedDeltaY();
                    } else {
                        dx = 0.0;
                        dy = 0.0;
                    }

                    if (dx != 0.0 || dy != 0.0) {
                        // Scale panning speed with base scale so it feels consistent
                        float panFactor = baseScale * 0.0025f;
                        orthoPanX += (float) dx * panFactor;
                        orthoPanY -= (float) dy * panFactor; // invert Y to match screen coords
                    }
                }
            } catch (Throwable ignored) {
            }
            
            // Calculate view dimensions
            float width = baseScale * aspectRatio;
            float height = baseScale;
            
            // Ensure positive non-zero values
            float safeWidth = Math.max(0.1f, width);
            float safeHeight = Math.max(0.1f, height);
            
            // Use a fixed near plane to prevent issues with upward rotations
            float nearPlane = 0.05F;
            // Use a far plane that's distant enough but not too far to cause precision issues
            float farPlane = 1000.0F;
            
            // Check if camera is at an extreme angle
            boolean extremeAngle = false;
            Camera camera = this.client.gameRenderer.getCamera();
            if (camera != null) {
                float pitch = Math.abs(camera.getPitch());
                extremeAngle = pitch > 80.0f; // Near vertical looking up or down
                
                // Apply special handling for extreme angles
                if (extremeAngle) {
                    Craneshot.LOGGER.debug("Applying special matrix handling for extreme camera angle: pitch={}", pitch);
                    
                    // Use a more conservative near plane for extreme angles
                    nearPlane = 0.1F;
                    
                    // Use smaller width/height for extreme angles to prevent precision issues
                    safeWidth = Math.min(safeWidth, 100.0f);
                    safeHeight = Math.min(safeHeight, 100.0f);
                }
            }
            
            // Apply accumulated panning prior to projection
            if (orthoPanX != 0.0f || orthoPanY != 0.0f) {
                matrix.translate(orthoPanX, orthoPanY, 0.0f);
            }

            // Create the orthographic projection with special handling for extreme angles
            try {
                matrix.ortho(
                    -safeWidth, safeWidth,          // left, right 
                    -safeHeight, safeHeight,        // bottom, top 
                    nearPlane, farPlane,            // near, far
                    true                           // zero to one depth range
                );
            } catch (Exception e) {
                // If ortho creation fails, try with even more conservative values
                Craneshot.LOGGER.warn("Failed to create ortho matrix, trying with more conservative values: {}", e.getMessage());
                safeWidth = 50.0f;
                safeHeight = 50.0f;
                nearPlane = 0.5f;
                farPlane = 500.0f;
                
                matrix.ortho(
                    -safeWidth, safeWidth, 
                    -safeHeight, safeHeight,
                    nearPlane, farPlane,
                    true
                );
            }
            
            // Return the configured matrix
            return matrix;
        } catch (Exception e) {
            Craneshot.LOGGER.error("Failed to create orthographic matrix: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Calculates a stable and safe scale factor for orthographic projection
     */
    private float calculateBaseScale(float fovDegrees) {
        // Start with the configured scale from settings
        float scale = CraneshotClient.MOVEMENT_MANAGER.getOrthoScale();
        
        // Get the camera target if available
        CameraTarget currentTarget = CraneshotClient.MOVEMENT_MANAGER.getCurrentTarget();
        
        // If we have a camera target and player, adjust scale based on distance
        if (currentTarget != null && this.client.player != null) {
            try {
                // Calculate distance to player
                double distanceToPlayer = currentTarget.getPosition().distanceTo(this.client.player.getEyePos());
                distanceToPlayer = Math.max(1.0, Math.min(200.0, distanceToPlayer));
                
                // Calculate scale based on FOV and distance
                float fovRadians = (float) Math.toRadians(fovDegrees);
                float calculatedScale = (float)(Math.tan(fovRadians/2) * distanceToPlayer * 2.0f);
                
                // Blend between configured scale and calculated scale
                scale = scale * 0.7f + calculatedScale * 0.3f;
            } catch (Exception e) {
                Craneshot.LOGGER.warn("Error calculating adaptive scale: {}", e.getMessage());
                // Keep using the default scale if calculation fails
            }
        }
        
        // Ensure the scale is within safe bounds
        return Math.max(1.0f, Math.min(100.0f, scale));
    }
    
    /**
     * Gets the effective orthographic factor directly from the camera movement.
     * When no movement is active, smoothly transitions to the target state.
     */
    private float getEffectiveOrthoFactor() {
        // Check if we have an active camera movement with ortho component
        CameraTarget currentTarget = CraneshotClient.MOVEMENT_MANAGER.getCurrentTarget();
        boolean hasActiveMovement = CraneshotClient.MOVEMENT_MANAGER.hasActiveMovement();
        
        // If we have an active camera movement, use its ortho factor directly
        if (currentTarget != null && hasActiveMovement) {
            // Get the ortho factor from the movement - this already has the correct
            // interpolation between perspective and orthographic modes
            float movementOrthoFactor = currentTarget.getOrthoFactor();
            
            // Update our transition progress to match for smoother transitions
            // when the movement completes
            transitionProgress = movementOrthoFactor;
            
            // Log significant ortho factor changes from movements
            if (Math.abs(movementOrthoFactor) > 0.01f && Math.random() < 0.01) { // Occasional logging
                Craneshot.LOGGER.error("Using camera movement ortho factor: {} (target: {})", 
                    movementOrthoFactor, transitionTargetOrtho ? 1.0f : 0.0f);
            }
            
            return Math.max(0.0f, Math.min(1.0f, movementOrthoFactor));
        }
        
        // If no active movement, check if we need to match the target state
        if (transitionTargetOrtho && transitionProgress < 1.0f) {
            // No active movement but should be in ortho mode - use saved progress
            return Math.max(0.0f, Math.min(1.0f, transitionProgress));
        } else if (!transitionTargetOrtho && transitionProgress > 0.0f) {
            // No active movement but should be in perspective mode - use saved progress
            return Math.max(0.0f, Math.min(1.0f, transitionProgress));
        }
        
        // At target state
        return transitionTargetOrtho ? 1.0f : 0.0f;
    }
    
    /**
     * No longer needed as we use getEffectiveOrthoFactor() for all ortho factor checks
     */
    private boolean hasActiveMovementWithOrtho() {
        // For backward compatibility - not used in the new implementation
        return getEffectiveOrthoFactor() > 0.001f;
    }
}
