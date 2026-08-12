package ninja.trek.util;

// import annotation from org.jetbrains instead
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.stats.StatsCounter;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;
import ninja.trek.CraneshotClient;
import ninja.trek.camera.CameraSystem;
import ninja.trek.config.GeneralMenuSettings;
import ninja.trek.config.FreeCamSettings;

/**
 * A dedicated camera entity for free camera movement
 * Used when the camera is in "out" position 
 */
public class CameraEntity extends LocalPlayer {
    @Nullable private static CameraEntity camera;
    @Nullable private static Entity originalCameraEntity;
    private static Vec3 cameraMotion = new Vec3(0.0, 0.0, 0.0);
    private static boolean cullChunksOriginal;
    private static boolean sprinting;
    private static boolean originalCameraWasPlayer;

    private float targetYaw = 0f;
    private float targetPitch = 0f;

    private CameraEntity(Minecraft mc, ClientLevel world,
                         ClientPacketListener netHandler, StatsCounter stats,
                         ClientRecipeBook recipeBook) {
        super(mc, world, netHandler, stats, recipeBook, Input.EMPTY, false);
    }

    @Override
    public boolean isSpectator() {
        return true;
    }

    /**
     * Returns an entity ID to prevent mods from complaining about missing ID
     */
    @Override
    public int getId() {
        if (originalCameraEntity != null) {
            return originalCameraEntity.getId();
        }
        return super.getId();
    }

    /**
     * Handles camera movement during each tick
     */
    public static void movementTick() {
        CameraEntity camera = getCamera();

        if (camera != null) {
            Options options = Minecraft.getInstance().options;

            camera.updateLastTickPosition();

            if (options.keySprint.isDown()) {
                sprinting = true;
            } else if (!options.keyUp.isDown() && !options.keyDown.isDown()) {
                sprinting = false;
            }

            FreeCamSettings settings = GeneralMenuSettings.getFreeCamSettings();
            
            // Calculate deceleration
            cameraMotion = calculatePlayerMotionWithDeceleration(cameraMotion, 
                                                               settings.getAcceleration(),
                                                               settings.getDeceleration());
            
            // Map axes correctly and apply sprint multiplier uniformly to horizontal movement
            double mult = sprinting ? 3.0 : 1.0;
            double forward = cameraMotion.z * mult;
            double strafe = cameraMotion.x * mult;
            double up = cameraMotion.y * mult;
            camera.handleMotion(forward, up, strafe);
        }
    }

    /**
     * Calculates motion with deceleration similar to tweakeroo's implementation
     */
    private static Vec3 calculatePlayerMotionWithDeceleration(Vec3 motion, float acceleration, float deceleration) {
        Minecraft mc = Minecraft.getInstance();

        double x = 0;
        double y = 0;
        double z = 0;
        
        if (mc.options.keyUp.isDown()) {
            z += 1.0;
        }
        
        if (mc.options.keyDown.isDown()) {
            z -= 1.0;
        }
        
        if (mc.options.keyLeft.isDown()) {
            x += 1.0;
        }
        
        if (mc.options.keyRight.isDown()) {
            x -= 1.0;
        }
        
        if (mc.options.keyJump.isDown()) {
            y += 1.0;
        }
        
        if (ninja.trek.CameraController.isKeyPhysicallyHeld(mc, mc.options.keyShift)) {
            y -= 1.0;
        }
        
        boolean keyPressed = x != 0 || y != 0 || z != 0;
        
        double accX = motion.x;
        double accY = motion.y;
        double accZ = motion.z;
        
        if (keyPressed) {
            // Normalize the input vector if there's input in multiple directions
            if ((x != 0 && z != 0) || (x != 0 && y != 0) || (z != 0 && y != 0)) {
                double norm = Math.sqrt(x * x + y * y + z * z);
                x /= norm;
                y /= norm;
                z /= norm;
            }
            
            // Accelerate towards the target motion
            accX = accX + (x - accX) * acceleration;
            accY = accY + (y - accY) * acceleration;
            accZ = accZ + (z - accZ) * acceleration;
        } else {
            // Decelerate when no input
            accX = accX * (1.0 - deceleration);
            accY = accY * (1.0 - deceleration);
            accZ = accZ * (1.0 - deceleration);
            
            // Fix very small values to zero to prevent perpetual small movements
            if (Math.abs(accX) < 0.001) accX = 0;
            if (Math.abs(accY) < 0.001) accY = 0;
            if (Math.abs(accZ) < 0.001) accZ = 0;
        }
        
        return new Vec3(accX, accY, accZ);
    }

    private static double getMoveSpeed() {
        return GeneralMenuSettings.getFreeCamSettings().getMoveSpeed() * 10;
    }

    private void handleMotion(double forward, double up, double strafe) {
        float yaw = this.getYRot();
        double scale = getMoveSpeed();

        // Use POST_MOVE_KEYS setting from CameraController to determine movement mode
        // MOVE_CAMERA_FLAT and MOVE_CAMERA_FREE both use camera-relative movement
        boolean useCameraRelative = (ninja.trek.CameraController.currentKeyMoveMode ==
                ninja.trek.cameramovements.AbstractMovementSettings.POST_MOVE_KEYS.MOVE_CAMERA_FLAT ||
                ninja.trek.CameraController.currentKeyMoveMode ==
                ninja.trek.cameramovements.AbstractMovementSettings.POST_MOVE_KEYS.MOVE_CAMERA_FREE);

        if (useCameraRelative) {
            // Camera-relative movement
            double xFactor = Math.sin(yaw * Math.PI / 180.0);
            double zFactor = Math.cos(yaw * Math.PI / 180.0);

            double x = (strafe * zFactor - forward * xFactor) * scale;
            double y = up * scale;
            double z = (forward * zFactor + strafe * xFactor) * scale;

            this.setDeltaMovement(new Vec3(x, y, z));
        } else {
            // Axis-aligned movement (fallback for other modes or NONE)
            double x = strafe * scale;
            double y = up * scale;
            double z = forward * scale;

            this.setDeltaMovement(new Vec3(x, y, z));
        }

        this.move(MoverType.SELF, this.getDeltaMovement());
    }

    private void updateLastTickPosition() {
        this.xOld = this.getX();
        this.yOld = this.getY();
        this.zOld = this.getZ();

        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();

        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();

        this.yHeadRotO = this.yHeadRot;
    }

    public void setCameraRotations(float yaw, float pitch) {
        this.setYRot(yaw);
        this.setXRot(pitch);
        this.yHeadRot = yaw;
        this.targetYaw = yaw;
        this.targetPitch = pitch;
    }

    /**
     * Updates camera rotations by applying deltas directly.
     * NOTE: This is a legacy method. CameraEntity is now a "ghost" that mirrors
     * CameraSystem's state. Rotation easing is handled in CameraSystem, not here.
     */
    public void updateCameraRotations(float yawChange, float pitchChange) {
        // No easing - just apply directly
        // CameraSystem handles all easing logic now
        float newYaw = this.getYRot() + yawChange;
        float newPitch = Mth.clamp(this.getXRot() + pitchChange, -90F, 90F);
        setCameraRotations(newYaw, newPitch);
    }

    private static CameraEntity createCameraEntity(Minecraft mc) {
        LocalPlayer player = mc.player;

        if (player == null) {
            throw new RuntimeException("Cannot create CameraEntity from null player!");
        }

        // Seed from current camera transform if available (prevents snap to player)
        Vec3 entityPos;
        float yaw;
        float pitch;
        net.minecraft.client.Camera current = mc.gameRenderer != null ? mc.gameRenderer.getMainCamera() : null;
        if (current != null) {
            entityPos = current.position();
            yaw = current.yRot();
            pitch = current.xRot();
        } else {
            entityPos = new Vec3(player.getX(), player.getY(), player.getZ());
            yaw = player.getYRot();
            pitch = player.getXRot();
        }

        mc.player.setDeltaMovement(Vec3.ZERO);

        CameraEntity camera = new CameraEntity(
            mc, 
            mc.level, 
            player.connection, 
            player.getStats(), 
            player.getRecipeBook()
        );
        camera.noPhysics = true;

        camera.setPosRaw(entityPos.x(), entityPos.y(), entityPos.z());
        camera.setYRot(yaw);
        camera.setXRot(pitch);
        camera.targetYaw = yaw;
        camera.targetPitch = pitch;
        camera.setDeltaMovement(Vec3.ZERO);

        return camera;
    }

    @Nullable
    public static CameraEntity getCamera() {
        return camera;
    }

    public static void setCameraState(boolean enabled) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.level != null && mc.player != null) {
            if (enabled) {
                createAndSetCamera(mc);
            } else {
                removeCamera(mc);
            }

            // Hand rendering toggle API changed; rely on camera entity + perspective instead.
        }
    }

    public static boolean originalCameraWasPlayer() {
        return originalCameraWasPlayer;
    }

    private static void createAndSetCamera(Minecraft mc) {
        camera = createCameraEntity(mc);
        originalCameraEntity = mc.getCameraEntity();
        originalCameraWasPlayer = originalCameraEntity == mc.player;
        cullChunksOriginal = mc.smartCull;

        // logging removed

        mc.setCameraEntity(camera);
        mc.smartCull = false; // Disable chunk culling
    }

    private static void removeCamera(Minecraft mc) {
        if (mc.level != null && camera != null) {
            // Re-fetch the player entity, in case the player died while in Free Camera mode
            mc.setCameraEntity(originalCameraWasPlayer ? mc.player : originalCameraEntity);
            mc.smartCull = cullChunksOriginal;

            final int chunkX = Mth.floor(camera.getX() / 16.0) >> 4;
            final int chunkZ = Mth.floor(camera.getZ() / 16.0) >> 4;
            CameraUtils.markChunksForRebuildOnDeactivation(chunkX, chunkZ);
        }

        originalCameraEntity = null;
        camera = null;
    }
}

