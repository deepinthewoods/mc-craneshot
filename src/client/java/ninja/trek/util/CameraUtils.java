package ninja.trek.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;

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
        return Mth.wrapDegrees(cameraYaw);
    }

    /**
     * Gets the camera pitch angle.
     * @return camera pitch in degrees
     */
    public static float getCameraPitch() {
        return Mth.wrapDegrees(cameraPitch);
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
        Minecraft mc = Minecraft.getInstance();

        if (mc.level == null || (chunkX == lastChunkX && chunkZ == lastChunkZ)) {
            return;
        }

        final int viewDistance = mc.options.renderDistance().get();

        if (chunkX != lastChunkX) {
            final int minCX = chunkX > lastChunkX ? lastChunkX + viewDistance : chunkX     - viewDistance;
            final int maxCX = chunkX > lastChunkX ? chunkX     + viewDistance : lastChunkX - viewDistance;

            for (int cx = minCX; cx <= maxCX; ++cx) {
                for (int cz = chunkZ - viewDistance; cz <= chunkZ + viewDistance; ++cz) {
                    if (isClientChunkLoaded(mc.level, cx, cz)) {
                        markChunkForReRender(mc.level, cx, cz);
                    }
                }
            }
        }

        if (chunkZ != lastChunkZ) {
            final int minCZ = chunkZ > lastChunkZ ? lastChunkZ + viewDistance : chunkZ     - viewDistance;
            final int maxCZ = chunkZ > lastChunkZ ? chunkZ     + viewDistance : lastChunkZ - viewDistance;

            for (int cz = minCZ; cz <= maxCZ; ++cz) {
                for (int cx = chunkX - viewDistance; cx <= chunkX + viewDistance; ++cx) {
                    if (isClientChunkLoaded(mc.level, cx, cz)) {
                        markChunkForReRender(mc.level, cx, cz);
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
        Minecraft mc = Minecraft.getInstance();
        final int viewDistance = mc.options.renderDistance().get();
        Entity entity = mc.getCameraEntity();

        if (mc.level == null || entity == null) {
            return;
        }

        final int chunkX = Mth.floor(entity.getX() / 16.0) >> 4;
        final int chunkZ = Mth.floor(entity.getZ() / 16.0) >> 4;

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
                    isClientChunkLoaded(mc.level, cx, cz)) {
                    markChunkForReRender(mc.level, cx, cz);
                }
            }
        }
    }

    /**
     * Marks a chunk for re-rendering.
     * @param world the client world
     * @param chunkX chunk X coordinate
     * @param chunkZ chunk Z coordinate
     */
    public static void markChunkForReRender(ClientLevel world, int chunkX, int chunkZ) {
        for (int cy = 0; cy < 16; ++cy) {
            world.setSectionDirtyWithNeighbors(chunkX, cy, chunkZ);
        }
    }

    /**
     * Checks if a client chunk is loaded.
     * @param world the client world
     * @param chunkX chunk X coordinate
     * @param chunkZ chunk Z coordinate
     * @return true if the chunk is loaded
     */
    public static boolean isClientChunkLoaded(ClientLevel world, int chunkX, int chunkZ) {
        return world.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) != null;
    }

    /**
     * Checks whether a world-space position lies within the client's render distance
     * of the local player and its chunk is fully loaded.
     */
    public static boolean isPoseWithinRenderDistance(Minecraft mc, Vec3 pos) {
        if (mc == null || mc.level == null || mc.player == null || pos == null) return false;
        int cx = Mth.floor(pos.x) >> 4;
        int cz = Mth.floor(pos.z) >> 4;
        int pcx = Mth.floor(mc.player.getX()) >> 4;
        int pcz = Mth.floor(mc.player.getZ()) >> 4;
        int r = mc.options.renderDistance().get();
        if (Math.abs(cx - pcx) > r || Math.abs(cz - pcz) > r) return false;
        return isClientChunkLoaded(mc.level, cx, cz);
    }
}
