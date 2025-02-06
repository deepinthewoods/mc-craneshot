package ninja.trek.cameramovements;

import net.minecraft.client.render.Camera;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import ninja.trek.Craneshot;

public class CameraTarget {
    private Vec3d position;
    private float yaw;
    private float pitch;


    public CameraTarget(Vec3d position, float yaw, float pitch) {
        this.position = position;
        this.yaw = yaw;
        this.pitch = pitch;

    }

    public static CameraTarget fromCamera(Camera camera) {
        return new CameraTarget(camera.getPos(), camera.getYaw(), camera.getPitch());
    }

    public static CameraTarget fromDistance(PlayerEntity player, double distance) {
        double yaw = Math.toRadians(player.getYaw());// + Math.toRadians((180));
        double pitch = Math.toRadians(player.getPitch());
        double xOffset = Math.sin(yaw) * Math.cos(pitch) * distance;
        double yOffset = Math.sin(pitch) * distance;
        double zOffset = -Math.cos(yaw) * Math.cos(pitch) * distance;
        Vec3d targetPos = player.getEyePos().add(xOffset, yOffset, zOffset);
        return new CameraTarget(targetPos, player.getYaw(), player.getPitch());
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



    public CameraTarget withAdjustedPosition(PlayerEntity player, RaycastType raycastType) {
//        Craneshot.LOGGER.info("withAdjustedPosition called with raycastType: {}", this.raycastType);

        Vec3d adjustedPos = RaycastUtil.adjustForCollision(player.getEyePos(), this.position, raycastType);
        return new CameraTarget(adjustedPos, this.yaw, this.pitch);
    }
}