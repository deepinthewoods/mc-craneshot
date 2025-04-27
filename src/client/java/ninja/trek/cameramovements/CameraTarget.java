package ninja.trek.cameramovements;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import ninja.trek.mixin.client.FovAccessor;

public class CameraTarget {
    private Vec3d position;
    private float yaw;
    private float pitch;
    private float fovMultiplier;  // 1.0 = normal FOV, >1 = wider, <1 = narrower
    private float orthoFactor = 0.0f; // 0.0 = perspective, 1.0 = orthographic

    public CameraTarget(Vec3d position, float yaw, float pitch, float fovMultiplier) {
        this.position = position;
        this.yaw = yaw;
        this.pitch = pitch;
        this.fovMultiplier = Math.max(0.1f, fovMultiplier);
        this.orthoFactor = 0.0f;
    }
    
    public CameraTarget(Vec3d position, float yaw, float pitch, float fovMultiplier, float orthoFactor) {
        this.position = position;
        this.yaw = yaw;
        this.pitch = pitch;
        this.fovMultiplier = Math.max(0.1f, fovMultiplier);
        this.orthoFactor = Math.max(0.0f, Math.min(1.0f, orthoFactor));
    }

    public CameraTarget(Vec3d position, float yaw, float pitch) {
        this(position, yaw, pitch, 1.0f); // Default to normal FOV
    }

    public CameraTarget() {
        position = new Vec3d(0, 0, 0);
        yaw = 0;
        pitch = 0;
        fovMultiplier = 1.0f; // Default to normal FOV
    }

    public static CameraTarget fromCamera(Camera camera) {
        MinecraftClient client = MinecraftClient.getInstance();
        float currentFovMultiplier = 1.0f;
        if (client.gameRenderer instanceof FovAccessor) {
            currentFovMultiplier = ((FovAccessor) client.gameRenderer).getFovModifier();
            if (currentFovMultiplier == 0) currentFovMultiplier = 1.0f;
        }
        CameraTarget target = new CameraTarget(camera.getPos(), camera.getYaw(), camera.getPitch(), currentFovMultiplier);
        ninja.trek.Craneshot.LOGGER.debug("Created CameraTarget from camera with default orthoFactor=0.0");
        return target;
    }

    public static CameraTarget fromDistanceBack(PlayerEntity player, double distance) {
        double yaw = Math.toRadians(player.getYaw());
        double pitch = Math.toRadians(player.getPitch());
        double xOffset = Math.sin(yaw) * Math.cos(pitch) * distance;
        double yOffset = Math.sin(pitch) * distance;
        double zOffset = -Math.cos(yaw) * Math.cos(pitch) * distance;
        Vec3d targetPos = player.getEyePos().add(xOffset, yOffset, zOffset);
        return new CameraTarget(targetPos, player.getYaw(), player.getPitch(), 1.0f);
    }

    public static CameraTarget fromDistanceFront(PlayerEntity player, double distance) {
        double yaw = Math.toRadians(player.getYaw() + 180);
        double pitch = Math.toRadians(-player.getPitch());
        double xOffset = Math.sin(yaw) * Math.cos(pitch) * distance;
        double yOffset = Math.sin(pitch) * distance;
        double zOffset = -Math.cos(yaw) * Math.cos(pitch) * distance;
        Vec3d targetPos = player.getEyePos().add(xOffset, yOffset, zOffset);
        return new CameraTarget(targetPos, player.getYaw() + 180, -player.getPitch(), 1.0f);
    }

    public Vec3d getPosition() {
        return position;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public float getFovMultiplier() {
        return fovMultiplier;
    }
    
    public float getOrthoFactor() {
        return orthoFactor;
    }
    
    public void setOrthoFactor(float factor) {
        this.orthoFactor = Math.max(0.0f, Math.min(1.0f, factor));
    }

    public void setFovMultiplier(float multiplier) {
        this.fovMultiplier = Math.max(0.1f, multiplier); // Ensure we never have a zero or negative multiplier
    }

    public CameraTarget withAdjustedPosition(PlayerEntity player, RaycastType raycastType) {
        Vec3d adjustedPos = RaycastUtil.adjustForCollision(player.getEyePos(), this.position, raycastType);
        CameraTarget adjusted = new CameraTarget(adjustedPos, this.yaw, this.pitch, this.fovMultiplier, this.orthoFactor);
        ninja.trek.Craneshot.LOGGER.debug("Applying collision adjustment, preserving orthoFactor={}", this.orthoFactor);
        return adjusted;
    }

    public void set(Vec3d v, float yaw, float pitch) {
        set(v, yaw, pitch, this.fovMultiplier);
    }

    public void set(Vec3d v, float yaw, float pitch, float fovMultiplier) {
        position = v;
        this.yaw = yaw;
        this.pitch = pitch;
        this.fovMultiplier = fovMultiplier != 0 ? fovMultiplier : 1.0f;
    }
    
    public void set(Vec3d v, float yaw, float pitch, float fovMultiplier, float orthoFactor) {
        position = v;
        this.yaw = yaw;
        this.pitch = pitch;
        this.fovMultiplier = fovMultiplier != 0 ? fovMultiplier : 1.0f;
        this.orthoFactor = Math.max(0.0f, Math.min(1.0f, orthoFactor));
    }

    public void set(CameraTarget t) {
        position = t.position;
        this.pitch = t.pitch;
        this.yaw = t.yaw;
        this.fovMultiplier = t.fovMultiplier != 0 ? t.fovMultiplier : 1.0f;
        this.orthoFactor = t.orthoFactor;
    }

    public CameraTarget lerp(CameraTarget other, float t) {
        Vec3d lerpedPos = this.position.lerp(other.position, t);
        float lerpedYaw = lerpAngle(this.yaw, other.yaw, t);
        float lerpedPitch = lerpAngle(this.pitch, other.pitch, t);

        // Ensure we're interpolating between valid FOV multipliers
        float startFov = this.fovMultiplier != 0 ? this.fovMultiplier : 1.0f;
        float endFov = other.fovMultiplier != 0 ? other.fovMultiplier : 1.0f;
        float lerpedFov = startFov + (endFov - startFov) * t;
        
        // Interpolate ortho factor
        float lerpedOrtho = this.orthoFactor + (other.orthoFactor - this.orthoFactor) * t;

        return new CameraTarget(lerpedPos, lerpedYaw, lerpedPitch, lerpedFov, lerpedOrtho);
    }

    private float lerpAngle(float start, float end, float t) {
        float diff = end - start;
        while (diff > 180) diff -= 360;
        while (diff < -180) diff += 360;
        return start + diff * t;
    }
}