package ninja.trek.util;

// import annotation from org.jetbrains instead
import org.jetbrains.annotations.Nullable;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.PlayerInput;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.stat.StatHandler;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import ninja.trek.CraneshotClient;
import ninja.trek.config.GeneralMenuSettings;
import ninja.trek.config.FreeCamSettings;

/**
 * A dedicated camera entity for free camera movement
 * Used when the camera is in "out" position or in orthographic mode
 */
public class CameraEntity extends ClientPlayerEntity {
    @Nullable private static CameraEntity camera;
    @Nullable private static Entity originalCameraEntity;
    private static Vec3d cameraMotion = new Vec3d(0.0, 0.0, 0.0);
    private static boolean cullChunksOriginal;
    private static boolean sprinting;
    private static boolean originalCameraWasPlayer;

    private CameraEntity(MinecraftClient mc, ClientWorld world,
                         ClientPlayNetworkHandler netHandler, StatHandler stats,
                         ClientRecipeBook recipeBook) {
        super(mc, world, netHandler, stats, recipeBook, PlayerInput.DEFAULT, false);
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
            GameOptions options = MinecraftClient.getInstance().options;

            camera.updateLastTickPosition();

            if (options.sprintKey.isPressed()) {
                sprinting = true;
            } else if (options.forwardKey.isPressed() == false && options.backKey.isPressed() == false) {
                sprinting = false;
            }

            FreeCamSettings settings = GeneralMenuSettings.getFreeCamSettings();
            
            // Calculate deceleration
            cameraMotion = calculatePlayerMotionWithDeceleration(cameraMotion, 
                                                               settings.getAcceleration(),
                                                               settings.getDeceleration());
            
            double forward = sprinting ? cameraMotion.x * 3 : cameraMotion.x;
            camera.handleMotion(forward, cameraMotion.y, cameraMotion.z);
        }
    }

    /**
     * Calculates motion with deceleration similar to tweakeroo's implementation
     */
    private static Vec3d calculatePlayerMotionWithDeceleration(Vec3d motion, float acceleration, float deceleration) {
        MinecraftClient mc = MinecraftClient.getInstance();
        FreeCamSettings.MovementMode movementMode = GeneralMenuSettings.getFreeCamSettings().getMovementMode();
        
        double x = 0;
        double y = 0;
        double z = 0;
        
        if (mc.options.forwardKey.isPressed()) {
            z += 1.0;
        }
        
        if (mc.options.backKey.isPressed()) {
            z -= 1.0;
        }
        
        if (mc.options.leftKey.isPressed()) {
            x += 1.0;
        }
        
        if (mc.options.rightKey.isPressed()) {
            x -= 1.0;
        }
        
        if (mc.options.jumpKey.isPressed()) {
            y += 1.0;
        }
        
        if (mc.options.sneakKey.isPressed()) {
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
        
        return new Vec3d(accX, accY, accZ);
    }

    private static double getMoveSpeed() {
        return GeneralMenuSettings.getFreeCamSettings().getMoveSpeed() * 10;
    }

    private void handleMotion(double forward, double up, double strafe) {
        float yaw = this.getYaw();
        double scale = getMoveSpeed();
        
        if (GeneralMenuSettings.getFreeCamSettings().getMovementMode() == FreeCamSettings.MovementMode.CAMERA) {
            // Camera-relative movement
            double xFactor = Math.sin(yaw * Math.PI / 180.0);
            double zFactor = Math.cos(yaw * Math.PI / 180.0);
            
            double x = (strafe * zFactor - forward * xFactor) * scale;
            double y = up * scale;
            double z = (forward * zFactor + strafe * xFactor) * scale;
            
            this.setVelocity(new Vec3d(x, y, z));
        } else {
            // Axis-aligned movement
            double x = strafe * scale;
            double y = up * scale;
            double z = forward * scale;
            
            this.setVelocity(new Vec3d(x, y, z));
        }
        
        this.move(MovementType.SELF, this.getVelocity());
    }

    private void updateLastTickPosition() {
        this.lastRenderX = this.getX();
        this.lastRenderY = this.getY();
        this.lastRenderZ = this.getZ();

        this.lastX = this.getX();
        this.lastY = this.getY();
        this.lastZ = this.getZ();

        this.lastYaw = this.getYaw();
        this.lastPitch = this.getPitch();

        this.lastHeadYaw = this.headYaw;
    }

    public void setCameraRotations(float yaw, float pitch) {
        this.setYaw(yaw);
        this.setPitch(pitch);
        this.headYaw = yaw;
    }

    public void updateCameraRotations(float yawChange, float pitchChange) {
        float yaw = this.getYaw() + yawChange * 0.15F;
        float pitch = MathHelper.clamp(this.getPitch() + pitchChange * 0.15F, -90F, 90F);

        this.setYaw(yaw);
        this.setPitch(pitch);

        this.setCameraRotations(yaw, pitch);
    }

    private static CameraEntity createCameraEntity(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;

        if (player == null) {
            throw new RuntimeException("Cannot create CameraEntity from null player!");
        }

        Vec3d entityPos = player.getPos();
        float yaw = player.getYaw();
        float pitch = player.getPitch();

        mc.player.setVelocity(Vec3d.ZERO);

        CameraEntity camera = new CameraEntity(
            mc, 
            mc.world, 
            player.networkHandler, 
            player.getStatHandler(), 
            player.getRecipeBook()
        );
        camera.noClip = true;

        camera.setPos(entityPos.getX(), entityPos.getY() + 0.125f, entityPos.getZ());
        camera.setYaw(yaw);
        camera.setPitch(pitch);
        camera.setVelocity(Vec3d.ZERO);

        return camera;
    }

    @Nullable
    public static CameraEntity getCamera() {
        return camera;
    }

    public static void setCameraState(boolean enabled) {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.world != null && mc.player != null) {
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

    private static void createAndSetCamera(MinecraftClient mc) {
        camera = createCameraEntity(mc);
        originalCameraEntity = mc.getCameraEntity();
        originalCameraWasPlayer = originalCameraEntity == mc.player;
        cullChunksOriginal = mc.chunkCullingEnabled;

        mc.setCameraEntity(camera);
        mc.chunkCullingEnabled = false; // Disable chunk culling
    }

    private static void removeCamera(MinecraftClient mc) {
        if (mc.world != null && camera != null) {
            // Re-fetch the player entity, in case the player died while in Free Camera mode
            mc.setCameraEntity(originalCameraWasPlayer ? mc.player : originalCameraEntity);
            mc.chunkCullingEnabled = cullChunksOriginal;

            final int chunkX = MathHelper.floor(camera.getX() / 16.0) >> 4;
            final int chunkZ = MathHelper.floor(camera.getZ() / 16.0) >> 4;
            CameraUtils.markChunksForRebuildOnDeactivation(chunkX, chunkZ);
        }

        originalCameraEntity = null;
        camera = null;
    }
}
