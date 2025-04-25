package ninja.trek.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.chunk.ChunkStatus;

/**
 * Utility class for camera-related functions.
 * Handles camera positioning, rotation, and chunk updating.
 */
public class CameraUtils {
    private static float cameraYaw;
    private static float cameraPitch;
    private static boolean freeCameraActive;

    /**
     * Sets the free camera state.
     * @param isActive true to enable free camera mode
     */
    public static void setFreeCameraActive(boolean isActive) {
        freeCameraActive = isActive;
    }

    /**
     * Checks if free camera is currently active.
     * @return true if free camera mode is active
     */
    public static boolean isFreeCameraActive() {
        return freeCameraActive;
    }

    /**
     * Gets the camera yaw angle.
     * @return camera yaw in degrees
     */
    public static float getCameraYaw() {
        return MathHelper.wrapDegrees(cameraYaw);
    }

    /**
     * Gets the camera pitch angle.
     * @return camera pitch in degrees
     */
    public static float getCameraPitch() {
        return MathHelper.wrapDegrees(cameraPitch);
    }

    /**
     * Sets the camera yaw angle.
     * @param yaw yaw angle in degrees
     */
    public static void setCameraYaw(float yaw) {
        cameraYaw = yaw;
    }

    /**
     * Sets the camera pitch angle.
     * @param pitch pitch angle in degrees
     */
    public static void setCameraPitch(float pitch) {
        cameraPitch = pitch;
    }

    /**
     * Sets both camera rotation angles at once.
     * @param yaw yaw angle in degrees
     * @param pitch pitch angle in degrees
     */
    public static void setCameraRotations(float yaw, float pitch) {
        CameraEntity camera = CameraEntity.getCamera();

        if (camera != null) {
            camera.setCameraRotations(yaw, pitch);
        }
    }

    /**
     * Updates camera rotations based on delta changes.
     * @param yawChange change in yaw
     * @param pitchChange change in pitch
     */
    public static void updateCameraRotations(float yawChange, float pitchChange) {
        CameraEntity camera = CameraEntity.getCamera();

        if (camera != null) {
            camera.updateCameraRotations(yawChange, pitchChange);
        }
    }

    /**
     * Marks chunks for rebuilding when camera moves.
     * @param chunkX current chunk X
     * @param chunkZ current chunk Z
     * @param lastChunkX previous chunk X
     * @param lastChunkZ previous chunk Z
     */
    public static void markChunksForRebuild(int chunkX, int chunkZ, int lastChunkX, int lastChunkZ) {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.world == null || (chunkX == lastChunkX && chunkZ == lastChunkZ)) {
            return;
        }

        final int viewDistance = mc.options.getViewDistance().getValue();

        if (chunkX != lastChunkX) {
            final int minCX = chunkX > lastChunkX ? lastChunkX + viewDistance : chunkX     - viewDistance;
            final int maxCX = chunkX > lastChunkX ? chunkX     + viewDistance : lastChunkX - viewDistance;

            for (int cx = minCX; cx <= maxCX; ++cx) {
                for (int cz = chunkZ - viewDistance; cz <= chunkZ + viewDistance; ++cz) {
                    if (isClientChunkLoaded(mc.world, cx, cz)) {
                        markChunkForReRender(mc.worldRenderer, cx, cz);
                    }
                }
            }
        }

        if (chunkZ != lastChunkZ) {
            final int minCZ = chunkZ > lastChunkZ ? lastChunkZ + viewDistance : chunkZ     - viewDistance;
            final int maxCZ = chunkZ > lastChunkZ ? chunkZ     + viewDistance : lastChunkZ - viewDistance;

            for (int cz = minCZ; cz <= maxCZ; ++cz) {
                for (int cx = chunkX - viewDistance; cx <= chunkX + viewDistance; ++cx) {
                    if (isClientChunkLoaded(mc.world, cx, cz)) {
                        markChunkForReRender(mc.worldRenderer, cx, cz);
                    }
                }
            }
        }
    }

    /**
     * Marks chunks for rebuilding when deactivating camera.
     * @param lastChunkX last camera chunk X
     * @param lastChunkZ last camera chunk Z
     */
    public static void markChunksForRebuildOnDeactivation(int lastChunkX, int lastChunkZ) {
        MinecraftClient mc = MinecraftClient.getInstance();
        final int viewDistance = mc.options.getViewDistance().getValue();
        Entity entity = mc.getCameraEntity();

        if (mc.world == null || entity == null) {
            return;
        }

        final int chunkX = MathHelper.floor(entity.getX() / 16.0) >> 4;
        final int chunkZ = MathHelper.floor(entity.getZ() / 16.0) >> 4;

        final int minCameraCX = lastChunkX - viewDistance;
        final int maxCameraCX = lastChunkX + viewDistance;
        final int minCameraCZ = lastChunkZ - viewDistance;
        final int maxCameraCZ = lastChunkZ + viewDistance;
        final int minCX = chunkX - viewDistance;
        final int maxCX = chunkX + viewDistance;
        final int minCZ = chunkZ - viewDistance;
        final int maxCZ = chunkZ + viewDistance;

        for (int cz = minCZ; cz <= maxCZ; ++cz) {
            for (int cx = minCX; cx <= maxCX; ++cx) {
                // Mark all chunks that were not in free camera range
                if ((cx < minCameraCX || cx > maxCameraCX || cz < minCameraCZ || cz > maxCameraCZ) &&
                    isClientChunkLoaded(mc.world, cx, cz)) {
                    markChunkForReRender(mc.worldRenderer, cx, cz);
                }
            }
        }
    }

    /**
     * Marks a chunk for re-rendering.
     * @param renderer the world renderer
     * @param chunkX chunk X coordinate
     * @param chunkZ chunk Z coordinate
     */
    public static void markChunkForReRender(WorldRenderer renderer, int chunkX, int chunkZ) {
        for (int cy = 0; cy < 16; ++cy) {
            renderer.scheduleChunkRender(chunkX, cy, chunkZ);
        }
    }

    /**
     * Checks if a client chunk is loaded.
     * @param world the client world
     * @param chunkX chunk X coordinate
     * @param chunkZ chunk Z coordinate
     * @return true if the chunk is loaded
     */
    public static boolean isClientChunkLoaded(ClientWorld world, int chunkX, int chunkZ) {
        return world.getChunkManager().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) != null;
    }
}