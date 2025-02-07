package ninja.trek.cameramovements;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;

public class CameraState {
    private Vec3d position;
    private float yaw;
    private float pitch;

    public CameraState(Vec3d position, float yaw, float pitch) {
        this.position = position;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public static CameraState fromCamera(Camera camera) {
        return new CameraState(camera.getPos(), camera.getYaw(), camera.getPitch());
    }

    public Vec3d getPosition() { return position; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }

    public CameraState lerp(CameraState other, double t) {
        Vec3d lerpedPos = this.position.lerp(other.position, t);
        float lerpedYaw = lerpAngle(this.yaw, other.yaw, (float)t);
        float lerpedPitch = lerpAngle(this.pitch, other.pitch, (float)t);
        return new CameraState(lerpedPos, lerpedYaw, lerpedPitch);
    }

    private float lerpAngle(float start, float end, float t) {
        float diff = end - start;
        while (diff > 180) diff -= 360;
        while (diff < -180) diff += 360;
        return start + diff * t;
    }
}