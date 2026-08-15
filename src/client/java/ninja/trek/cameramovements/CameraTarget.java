package ninja.trek.cameramovements;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import ninja.trek.camera.CameraSystem;

public class CameraTarget {
    private Vec3 position;
    private float yaw;
    private float pitch;
    private float fovMultiplier;  // 1.0 = normal FOV, >1 = wider, <1 = narrower

    public CameraTarget(Vec3 position, float yaw, float pitch, float fovMultiplier) {
        this.position = position;
        this.yaw = yaw;
        this.pitch = pitch;
        this.fovMultiplier = Math.max(0.1f, fovMultiplier);
        // orthographic factor removed
    }
    
    // Legacy signature retained; orthographic factor is ignored
    public CameraTarget(Vec3 position, float yaw, float pitch, float fovMultiplier, float orthoFactor) {
        this.position = position;
        this.yaw = yaw;
        this.pitch = pitch;
        this.fovMultiplier = Math.max(0.1f, fovMultiplier);
    }

    public CameraTarget(Vec3 position, float yaw, float pitch) {
        this(position, yaw, pitch, 1.0f); // Default to normal FOV
    }

    public CameraTarget() {
        position = new Vec3(0, 0, 0);
        yaw = 0;
        pitch = 0;
        fovMultiplier = 1.0f; // Default to normal FOV
    }

    public static CameraTarget fromCamera(Camera camera) {
        float currentFovMultiplier = CameraSystem.getInstance().getFovMultiplier();
        CameraTarget target = new CameraTarget(camera.position(), camera.yRot(), camera.xRot(), currentFovMultiplier);
        // logging removed
        return target;
    }



    public Vec3 getPosition() {
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
    
    // Legacy API; no-op after orthographic removal
    public float getOrthoFactor() { return 0.0f; }
    public void setOrthoFactor(float factor) { }

    public void setFovMultiplier(float multiplier) {
        this.fovMultiplier = Math.max(0.1f, multiplier); // Ensure we never have a zero or negative multiplier
    }

    public CameraTarget withAdjustedPosition(Player player, RaycastType raycastType, float tickDelta) {
        // Handle null raycastType safely
        if (raycastType == null) {
            raycastType = RaycastType.NONE;
        }
        
        Vec3 adjustedPos = RaycastUtil.adjustForCollision(player.getEyePosition(tickDelta), this.position, raycastType);
        CameraTarget adjusted = new CameraTarget(adjustedPos, this.yaw, this.pitch, this.fovMultiplier);
        // logging removed
        return adjusted;
    }

    public void set(Vec3 v, float yaw, float pitch) {
        set(v, yaw, pitch, this.fovMultiplier);
    }

    public void set(Vec3 v, float yaw, float pitch, float fovMultiplier) {
        position = v;
        this.yaw = yaw;
        this.pitch = pitch;
        this.fovMultiplier = fovMultiplier != 0 ? fovMultiplier : 1.0f;
    }
    
    public void set(Vec3 v, float yaw, float pitch, float fovMultiplier, float orthoFactor) {
        position = v;
        this.yaw = yaw;
        this.pitch = pitch;
        this.fovMultiplier = fovMultiplier != 0 ? fovMultiplier : 1.0f;
    }

    public void set(CameraTarget t) {
        position = t.position;
        this.pitch = t.pitch;
        this.yaw = t.yaw;
        this.fovMultiplier = t.fovMultiplier != 0 ? t.fovMultiplier : 1.0f;
    }

    public CameraTarget lerp(CameraTarget other, float t) {
        Vec3 lerpedPos = this.position.lerp(other.position, t);
        float lerpedYaw = lerpAngle(this.yaw, other.yaw, t);
        float lerpedPitch = lerpAngle(this.pitch, other.pitch, t);

        // Ensure we're interpolating between valid FOV multipliers
        float startFov = this.fovMultiplier != 0 ? this.fovMultiplier : 1.0f;
        float endFov = other.fovMultiplier != 0 ? other.fovMultiplier : 1.0f;
        float lerpedFov = startFov + (endFov - startFov) * t;
        
        return new CameraTarget(lerpedPos, lerpedYaw, lerpedPitch, lerpedFov);
    }

    private float lerpAngle(float start, float end, float t) {
        float diff = end - start;
        while (diff > 180) diff -= 360;
        while (diff < -180) diff += 360;
        return start + diff * t;
    }
}
