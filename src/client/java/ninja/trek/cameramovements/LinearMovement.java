package ninja.trek.cameramovements;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import ninja.trek.mixin.client.CameraAccessor;

public class LinearMovement extends AbstractMovementSettings implements ICameraMovement {

    @MovementSetting(label = "Position Easing Factor", min = 0.01, max = 1.0)
    private float positionEasingFactor = 0.1f;

    @MovementSetting(label = "Rotation Easing Factor", min = 0.01, max = 1.0)
    private float rotationEasingFactor = 0.1f;

    @MovementSetting(label = "Distance Easing Factor", min = 0.01, max = 1.0)
    private float distanceEasingFactor = 0.1f;

    @MovementSetting(label = "Scroll Sensitivity", min = 0.1, max = 5.0)
    private double scrollSensitivity = 0.5;

    @MovementSetting(label = "Min Distance", min = 1.0, max = 10.0)
    private double minDistance = 2.0;

    @MovementSetting(label = "Max Distance", min = 10.0, max = 50.0)
    private double maxDistance = 20.0;

    @MovementSetting(label = "Target Distance", min = 1.0, max = 50.0)
    private double targetDistance = 10;

    private double currentDistance = 0;
    private Vec3d smoothedPlayerEyePos = new Vec3d(0, 0, 0);
    private double smoothedYaw = 0;
    private double smoothedPitch = 0;
    private boolean resetting = false;
    private boolean wasFirstPerson = true;
    private double firstPersonDistanceThreshold = 2;

    @Override
    public void start(MinecraftClient client, Camera camera) {
        PlayerEntity player = client.player;
        if (player == null) return;

        smoothedPlayerEyePos = player.getEyePos();
        smoothedYaw = player.getYaw();
        smoothedPitch = player.getPitch();
        currentDistance = 0;
        resetting = false;
        wasFirstPerson = client.options.getPerspective() == Perspective.FIRST_PERSON;
    }

    @Override
    public boolean update(MinecraftClient client, Camera camera) {
        PlayerEntity player = client.player;
        if (player == null) return true;

        // Smooth eye position interpolation
        Vec3d playerEyePos = player.getEyePos();
        smoothedPlayerEyePos = interpolateVec3d(smoothedPlayerEyePos, playerEyePos, positionEasingFactor);

        // Smooth rotation interpolation
        smoothedYaw = lerpAngle(smoothedYaw, player.getYaw(), rotationEasingFactor);
        smoothedPitch = lerpAngle(smoothedPitch, player.getPitch(), rotationEasingFactor);

        // Calculate desired distance based on whether we're resetting
        double desiredDistance = resetting ? 0 : targetDistance;
        currentDistance += (desiredDistance - currentDistance) * distanceEasingFactor;

        // Handle perspective changes
        if (currentDistance > firstPersonDistanceThreshold &&
                client.options.getPerspective() == Perspective.FIRST_PERSON) {
            client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        } else if (currentDistance < firstPersonDistanceThreshold &&
                client.options.getPerspective() != Perspective.FIRST_PERSON) {
            client.options.setPerspective(Perspective.FIRST_PERSON);
        }

        // Calculate camera position
        double yaw = Math.toRadians(smoothedYaw);
        double pitch = Math.toRadians(smoothedPitch);
        double xOffset = Math.sin(yaw) * Math.cos(pitch) * currentDistance;
        double yOffset = Math.sin(pitch) * currentDistance;
        double zOffset = -Math.cos(yaw) * Math.cos(pitch) * currentDistance;

        Vec3d cameraPos = smoothedPlayerEyePos.add(xOffset, yOffset, zOffset);
        ((CameraAccessor)camera).invokesetPos(cameraPos);
        ((CameraAccessor)camera).invokeSetRotation((float)smoothedYaw, (float)smoothedPitch);

        return resetting && currentDistance < 0.01;
    }

    @Override
    public void reset(MinecraftClient client, Camera camera) {
        resetting = true;
    }

    @Override
    public void adjustDistance(boolean increase) {
        double multiplier = increase ? (1.0 / (1.0 + scrollSensitivity)) : (1.0 + scrollSensitivity);
        targetDistance = Math.max(minDistance, Math.min(maxDistance, targetDistance * multiplier));
    }

    @Override
    public String getName() {
        return "Linear";
    }

    private Vec3d interpolateVec3d(Vec3d current, Vec3d target, float factor) {
        double x = current.x + (target.x - current.x) * factor;
        double y = current.y + (target.y - current.y) * factor;
        double z = current.z + (target.z - current.z) * factor;
        return new Vec3d(x, y, z);
    }

    private double lerpAngle(double current, double target, float factor) {
        double diff = target - current;
        while (diff > 180) diff -= 360;
        while (diff < -180) diff += 360;
        return current + diff * factor;
    }
}