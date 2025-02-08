package ninja.trek.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.ChunkSectionPos;
import ninja.trek.CameraController;
import ninja.trek.cameramovements.AbstractMovementSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    @Shadow private int viewDistance;
    @Shadow private double lastCameraX;
    @Shadow private double lastCameraY;
    @Shadow private double lastCameraZ;

    private long lastUpdateTime = 0;
    private static final long UPDATE_INTERVAL = 1000; // 1 second in milliseconds

    @ModifyVariable(
            method = "setupTerrain",
            at = @At(value = "STORE"),
            ordinal = 0
    )
    private double modifyPlayerX(double d) {
        if (CameraController.currentKeyMoveMode == AbstractMovementSettings.POST_MOVE_KEYS.MOVE_CAMERA_FLAT || CameraController.currentKeyMoveMode == AbstractMovementSettings.POST_MOVE_KEYS.MOVE_CAMERA_FREE) {
            return CameraController.freeCamPosition.x;
        }
        return d;
    }

    @ModifyVariable(
            method = "setupTerrain",
            at = @At(value = "STORE"),
            ordinal = 1
    )
    private double modifyPlayerY(double e) {
        if (CameraController.currentKeyMoveMode == AbstractMovementSettings.POST_MOVE_KEYS.MOVE_CAMERA_FLAT || CameraController.currentKeyMoveMode == AbstractMovementSettings.POST_MOVE_KEYS.MOVE_CAMERA_FREE) {
            return CameraController.freeCamPosition.y;
        }
        return e;
    }

    @ModifyVariable(
            method = "setupTerrain",
            at = @At(value = "STORE"),
            ordinal = 2
    )
    private double modifyPlayerZ(double f) {
        if (CameraController.currentKeyMoveMode == AbstractMovementSettings.POST_MOVE_KEYS.MOVE_CAMERA_FLAT || CameraController.currentKeyMoveMode == AbstractMovementSettings.POST_MOVE_KEYS.MOVE_CAMERA_FREE) {
            return CameraController.freeCamPosition.z;
        }
        return f;
    }

    @ModifyVariable(
            method = "setupTerrain",
            at = @At(value = "STORE"),
            ordinal = 3
    )
    private double modifyCameraX(double g) {
        if (CameraController.currentKeyMoveMode == AbstractMovementSettings.POST_MOVE_KEYS.MOVE_CAMERA_FLAT || CameraController.currentKeyMoveMode == AbstractMovementSettings.POST_MOVE_KEYS.MOVE_CAMERA_FREE) {
            lastCameraX = CameraController.freeCamPosition.x;
            return CameraController.freeCamPosition.x;
        }
        return g;
    }

    @ModifyVariable(
            method = "setupTerrain",
            at = @At(value = "STORE"),
            ordinal = 4
    )
    private double modifyCameraY(double h) {
        if (CameraController.currentKeyMoveMode == AbstractMovementSettings.POST_MOVE_KEYS.MOVE_CAMERA_FLAT || CameraController.currentKeyMoveMode == AbstractMovementSettings.POST_MOVE_KEYS.MOVE_CAMERA_FREE) {
            lastCameraY = CameraController.freeCamPosition.y;
            return CameraController.freeCamPosition.y;
        }
        return h;
    }

    @ModifyVariable(
            method = "setupTerrain",
            at = @At(value = "STORE"),
            ordinal = 5
    )
    private double modifyCameraZ(double l) {
        if (CameraController.currentKeyMoveMode == AbstractMovementSettings.POST_MOVE_KEYS.MOVE_CAMERA_FLAT || CameraController.currentKeyMoveMode == AbstractMovementSettings.POST_MOVE_KEYS.MOVE_CAMERA_FREE) {
            lastCameraZ = CameraController.freeCamPosition.z;
            return CameraController.freeCamPosition.z;
        }
        return l;
    }

    @Inject(
            method = "setupTerrain",
            at = @At("HEAD")
    )
    private void onSetupTerrainStart(Camera camera, Frustum frustum, boolean hasForcedFrustum, boolean spectator, CallbackInfo ci) {
        if (CameraController.currentKeyMoveMode == AbstractMovementSettings.POST_MOVE_KEYS.MOVE_CAMERA_FLAT || CameraController.currentKeyMoveMode == AbstractMovementSettings.POST_MOVE_KEYS.MOVE_CAMERA_FREE) {
            Vec3d freeCamPos = CameraController.freeCamPosition;
            ((CameraAccessor)camera).invokesetPos(freeCamPos);

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
                    WorldRenderer worldRenderer = (WorldRenderer)(Object)this;
                    //worldRenderer.reload();
                    lastUpdateTime = currentTime;
                }
            }
        }
    }

    @Inject(
            method = "setupTerrain",
            at = @At("RETURN")
    )
    private void onSetupTerrainEnd(Camera camera, Frustum frustum, boolean hasForcedFrustum, boolean spectator, CallbackInfo ci) {
        if (CameraController.currentKeyMoveMode == AbstractMovementSettings.POST_MOVE_KEYS.MOVE_CAMERA_FLAT || CameraController.currentKeyMoveMode == AbstractMovementSettings.POST_MOVE_KEYS.MOVE_CAMERA_FREE) {
            WorldRenderer worldRenderer = (WorldRenderer)(Object)this;
            worldRenderer.getChunkBuilder().setCameraPosition(CameraController.freeCamPosition);
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

//    // Redirect the call to ClientPlayerEntity#getX() in setupTerrain
//    @Redirect(
//            method = "setupTerrain",
//            at = @At(
//                    value = "INVOKE",
//                    target = "Lnet/minecraft/client/network/ClientPlayerEntity;getX()D"
//            )
//    )
//    private double redirectPlayerX(ClientPlayerEntity player) {
//        if (CameraController.inFreeControlMode) {
//            return CameraController.freeCamPosition.x;
//        }
//        return player.getX();
//    }
//
//    // Redirect the call to ClientPlayerEntity#getY() in setupTerrain
//    @Redirect(
//            method = "setupTerrain",
//            at = @At(
//                    value = "INVOKE",
//                    target = "Lnet/minecraft/client/network/ClientPlayerEntity;getY()D"
//            )
//    )
//    private double redirectPlayerY(ClientPlayerEntity player) {
//        if (CameraController.inFreeControlMode) {
//            return CameraController.freeCamPosition.y;
//        }
//        return player.getY();
//    }
//
//    // Redirect the call to ClientPlayerEntity#getZ() in setupTerrain
//    @Redirect(
//            method = "setupTerrain",
//            at = @At(
//                    value = "INVOKE",
//                    target = "Lnet/minecraft/client/network/ClientPlayerEntity;getZ()D"
//            )
//    )
//    private double redirectPlayerZ(ClientPlayerEntity player) {
//        if (CameraController.inFreeControlMode) {
//            return CameraController.freeCamPosition.z;
//        }
//        return player.getZ();
//    }
//
//    // Redirect the call to Camera#getPitch() in setupTerrain so that it uses freecam pitch
//    @Redirect(
//            method = "setupTerrain",
//            at = @At(
//                    value = "INVOKE",
//                    target = "Lnet/minecraft/client/render/Camera;getPitch()F"
//            )
//    )
//    private float redirectCameraPitch(Camera camera) {
//        if (CameraController.inFreeControlMode) {
//            return CameraController.freeCamPitch;
//        }
//        return camera.getPitch();
//    }
//
//    // Redirect the call to Camera#getYaw() in setupTerrain so that it uses freecam yaw
//    @Redirect(
//            method = "setupTerrain",
//            at = @At(
//                    value = "INVOKE",
//                    target = "Lnet/minecraft/client/render/Camera;getYaw()F"
//            )
//    )
//    private float redirectCameraYaw(Camera camera) {
//        if (CameraController.inFreeControlMode) {
//            return CameraController.freeCamYaw;
//        }
//        return camera.getYaw();
//    }


}