package ninja.trek.mixin.client;

import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.util.math.Vec3d;
import ninja.trek.CameraController;
import ninja.trek.cameramovements.AbstractMovementSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    @Shadow private double lastCameraX;
    @Shadow private double lastCameraY;
    @Shadow private double lastCameraZ;

    @Unique
    private long lastUpdateTime = 0;
    @Unique
    private static final long UPDATE_INTERVAL = 1000; // 1 second in milliseconds

    // Instead of modifying variables directly, use a more direct approach with the Inject method
    @Inject(
            method = "setupTerrain",
            at = @At("HEAD")
    )
    private void onSetupTerrainStart(Camera camera, Frustum frustum, boolean hasForcedFrustum, boolean spectator, CallbackInfo ci) {
        if (CameraController.currentKeyMoveMode == AbstractMovementSettings.POST_MOVE_KEYS.MOVE_CAMERA_FLAT || CameraController.currentKeyMoveMode == AbstractMovementSettings.POST_MOVE_KEYS.MOVE_CAMERA_FREE) {
            Vec3d freeCamPos = CameraController.freeCamPosition;
            ((CameraAccessor)camera).invokesetPos(freeCamPos);

            // Update shadow fields
            this.lastCameraX = freeCamPos.x;
            this.lastCameraY = freeCamPos.y;
            this.lastCameraZ = freeCamPos.z;

            // Update the frustum to use the freecam position so that chunks aren't culled incorrectly.
            frustum.setPosition(freeCamPos.x, freeCamPos.y, freeCamPos.z);

            // Check if enough time has passed since last update
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastUpdateTime > UPDATE_INTERVAL) {
                // Force chunk updates when camera moves significantly
                double movementThreshold = 12.0;
                double dx = Math.abs(freeCamPos.x - lastCameraX);
                double dy = Math.abs(freeCamPos.y - lastCameraY);
                double dz = Math.abs(freeCamPos.z - lastCameraZ);

                if (dx > movementThreshold || dy > movementThreshold || dz > movementThreshold) {
                    // Update the last time we detected significant movement
                    lastUpdateTime = currentTime;
                    // Note: worldRenderer.reload() is commented out but might be needed in the future
                }
            }
        }
    }

    @Inject(
            method = "setupTerrain",
            at = @At("RETURN")
    )
    private void onSetupTerrainEnd(Camera camera, Frustum frustum, boolean hasForcedFrustum, boolean spectator, CallbackInfo ci) {
        if (CameraController.currentKeyMoveMode == AbstractMovementSettings.POST_MOVE_KEYS.MOVE_CAMERA_FLAT || 
            CameraController.currentKeyMoveMode == AbstractMovementSettings.POST_MOVE_KEYS.MOVE_CAMERA_FREE) {
            
            WorldRenderer worldRenderer = (WorldRenderer)(Object)this;
            if (worldRenderer != null && CameraController.freeCamPosition != null) {
                try {
                    worldRenderer.getChunkBuilder().setCameraPosition(CameraController.freeCamPosition);
                } catch (NullPointerException e) {
                    // Handle the potential null pointer exception
                    System.err.println("Error updating camera position for chunk builder: " + e.getMessage());
                }
            }
        }
    }

    @ModifyArg(
            method = "setupTerrain",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/chunk/ChunkBuilder;setCameraPosition(Lnet/minecraft/util/math/Vec3d;)V"
            ),
            index = 0
    )
    private Vec3d modifyChunkBuilderCameraPosition(Vec3d original) {
        if (CameraController.currentKeyMoveMode == AbstractMovementSettings.POST_MOVE_KEYS.MOVE_CAMERA_FLAT || CameraController.currentKeyMoveMode == AbstractMovementSettings.POST_MOVE_KEYS.MOVE_CAMERA_FREE) {
            return CameraController.freeCamPosition;
        }
        return original;
    }
    

}