package ninja.trek.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.ChunkSectionPos;
import ninja.trek.CameraController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
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
        if (CameraController.inFreeControlMode) {
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
        if (CameraController.inFreeControlMode) {
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
        if (CameraController.inFreeControlMode) {
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
        if (CameraController.inFreeControlMode) {
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
        if (CameraController.inFreeControlMode) {
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
        if (CameraController.inFreeControlMode) {
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
        if (CameraController.inFreeControlMode) {
            Vec3d freeCamPos = CameraController.freeCamPosition;
            ((CameraAccessor)camera).invokesetPos(freeCamPos);

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
                    worldRenderer.reload();
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
        if (CameraController.inFreeControlMode) {
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
        if (CameraController.inFreeControlMode) {
            return CameraController.freeCamPosition;
        }
        return original;
    }
}