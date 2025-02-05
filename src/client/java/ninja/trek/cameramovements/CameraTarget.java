package ninja.trek.cameramovements;

import net.minecraft.client.render.Camera;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import ninja.trek.Craneshot;

public class CameraTarget {
    private Vec3d position;
    private float yaw;
    private float pitch;
    private RaycastType raycastType;

    public CameraTarget(Vec3d position, float yaw, float pitch, RaycastType raycastType) {
        this.position = position;
        this.yaw = yaw;
        this.pitch = pitch;
        this.raycastType = raycastType;
    }

    public static CameraTarget fromCamera(Camera camera, RaycastType raycastType) {
        return new CameraTarget(camera.getPos(), camera.getYaw(), camera.getPitch(), raycastType);
    }

    public static CameraTarget fromPlayer(PlayerEntity player, RaycastType raycastType) {
        return new CameraTarget(player.getEyePos(), player.getYaw(), player.getPitch(), raycastType);
    }

    public static CameraTarget fromDistance(PlayerEntity player, double distance, RaycastType raycastType) {
        double yaw = Math.toRadians(player.getYaw());
        double pitch = Math.toRadians(player.getPitch());
        double xOffset = Math.sin(yaw) * Math.cos(pitch) * distance;
        double yOffset = Math.sin(pitch) * distance;
        double zOffset = -Math.cos(yaw) * Math.cos(pitch) * distance;
        Vec3d targetPos = player.getEyePos().add(xOffset, yOffset, zOffset);
        return new CameraTarget(targetPos, player.getYaw(), player.getPitch(), raycastType);
    }

    public CameraTarget lerp(CameraTarget other, double t) {
        Vec3d lerpedPos = this.position.lerp(other.position, t);
        float lerpedYaw = lerpAngle(this.yaw, other.yaw, (float)t);
        float lerpedPitch = lerpAngle(this.pitch, other.pitch, (float)t);
        return new CameraTarget(lerpedPos, lerpedYaw, lerpedPitch, this.raycastType);
    }

    private float lerpAngle(float start, float end, float t) {
        float diff = end - start;
        while (diff > 180) diff -= 360;
        while (diff < -180) diff += 360;
        return start + diff * t;
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

    public RaycastType getRaycastType() {
        return raycastType;
    }

    public void setPosition(Vec3d position) {
        this.position = position;
    }

    public CameraTarget withAdjustedPosition(PlayerEntity player) {
        Craneshot.LOGGER.info("withAdjustedPosition called with raycastType: {}", this.raycastType);

        Vec3d adjustedPos = RaycastUtil.adjustForCollision(player.getEyePos(), this.position, this.raycastType);
        return new CameraTarget(adjustedPos, this.yaw, this.pitch, this.raycastType);
    }
}