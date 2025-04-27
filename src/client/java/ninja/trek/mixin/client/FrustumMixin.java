package ninja.trek.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import ninja.trek.Craneshot;
import ninja.trek.OrthographicCameraManager;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Enhanced frustum mixin to handle orthographic projection and prevent hanging issues with extreme view angles.
 * Rather than completely disabling frustum culling, it uses a smarter approach that still allows some culling
 * to maintain performance while preventing issues that can cause the game to hang.
 */
@Mixin(Frustum.class)
public class FrustumMixin {
    
    // Track if this frustum might be problematic due to extreme angles
    private boolean potentiallyProblematicFrustum = false;
    private int safetyRadius = 8; // Chunk radius to consider "safe" from culling issues
    
    /**
     * Detect potentially problematic frustums based on camera pitch.
     * When the camera is looking almost straight up or down, frustum calculations can sometimes
     * produce invalid results that cause culling issues or game hangs.
     */
    @Inject(method = "setPosition", at = @At("TAIL"))
    private void onSetPosition(double x, double y, double z, CallbackInfo ci) {
        try {
            Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
            
            // Check if the camera pitch is at an extreme angle (near 90° up or down)
            if (camera != null) {
                float pitch = Math.abs(camera.getPitch());
                potentiallyProblematicFrustum = pitch > 80.0f; // Consider angles > 80° as potentially problematic
                
                // If we're in orthographic mode, always flag as potentially problematic
                if (OrthographicCameraManager.isOrthographicMode()) {
                    potentiallyProblematicFrustum = true;
                    
                    // Use a larger safety radius in orthographic mode
                    safetyRadius = 16;
                }
            }
        } catch (Exception e) {
            // Default to safe behavior if we encounter any errors
            potentiallyProblematicFrustum = true;
            Craneshot.LOGGER.warn("Error detecting camera angle, defaulting to safe frustum behavior: {}", e.getMessage());
        }
    }
    
    /**
     * Implements a smarter frustum culling approach for orthographic mode and extreme angles.
     * This method:
     * 1. For orthographic mode, uses a distance-based approach to determine visibility
     * 2. For extreme camera angles, prevents culling for nearby boxes to avoid hanging issues
     * 3. Still allows culling of distant objects to maintain performance
     */
    @Inject(method = "isVisible(Lnet/minecraft/util/math/Box;)Z", at = @At("HEAD"), cancellable = true)
    private void enhancedFrustumCulling(Box box, CallbackInfoReturnable<Boolean> cir) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            
            // For orthographic mode or potentially problematic frustums
            if (OrthographicCameraManager.isOrthographicMode() || potentiallyProblematicFrustum) {
                if (client.player == null) return; // Skip processing if player is null
                
                // Get player position and box center
                Vec3d playerPos = client.player.getPos();
                Vec3d boxCenter = new Vec3d(
                    (box.minX + box.maxX) / 2.0,
                    (box.minY + box.maxY) / 2.0,
                    (box.minZ + box.maxZ) / 2.0
                );
                
                // Calculate chunk distance from player to box
                ChunkPos playerChunkPos = new ChunkPos(client.player.getBlockPos());
                ChunkPos boxChunkPos = new ChunkPos((int)boxCenter.x, (int)boxCenter.z);
                int chunkDistance = Math.max(
                    Math.abs(playerChunkPos.x - boxChunkPos.x),
                    Math.abs(playerChunkPos.z - boxChunkPos.z)
                );
                
                // For boxes within safety radius chunks, always return visible
                if (chunkDistance <= safetyRadius) {
                    cir.setReturnValue(true);
                    return;
                }
                
                // For boxes beyond the safety radius but within render distance, use standard culling
                // This avoids rendering everything which can tank performance
                int renderDistance = client.options.getClampedViewDistance();
                if (chunkDistance > renderDistance + 2) {
                    // Boxes far beyond render distance are definitely not visible
                    cir.setReturnValue(false);
                    return;
                }
                
                // Fall through to default implementation for boxes between safety radius and render distance
            }
        } catch (Exception e) {
            // If anything goes wrong, default to safe behavior in orthographic mode
            if (OrthographicCameraManager.isOrthographicMode()) {
                Craneshot.LOGGER.debug("Error in frustum culling, defaulting to safe behavior: {}", e.getMessage());
                cir.setReturnValue(true);
            }
        }
    }
}