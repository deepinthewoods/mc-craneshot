package ninja.trek.cameramovements.movements;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import ninja.trek.cameramovements.CameraMovementType;
import ninja.trek.cameramovements.CameraTarget;
import ninja.trek.cameramovements.ICameraMovement;
import ninja.trek.cameramovements.MovementState;
import ninja.trek.config.AbstractMovementSettings;
import ninja.trek.config.MovementSetting;

@CameraMovementType(
        name = "Linear Movement",
        description = "Moves the camera with linear interpolation and position controls"
)
public class LinearMovement extends AbstractMovementSettings implements ICameraMovement {
    @MovementSetting(label = "Position Easing", min = 0.01, max = 1.0)
    private double positionEasing = 0.1;

    @MovementSetting(label = "Position Speed Limit", min = 0.1, max = 100.0)
    private double positionSpeedLimit = 2.0;

    @MovementSetting(label = "Rotation Easing", min = 0.01, max = 1.0)
    private double rotationEasing = 0.1;

    @MovementSetting(label = "Rotation Speed Limit", min = 0.1, max = 3600.0)
    private double rotationSpeedLimit = 45.0;

    @MovementSetting(label = "Target Distance", min = 1.0, max = 50.0)
    private double targetDistance = 10.0;

    @MovementSetting(label = "Min Distance", min = 1.0, max = 10.0)
    private double minDistance = 2.0;

    @MovementSetting(label = "Max Distance", min = 10.0, max = 50.0)
    private double maxDistance = 20.0;

    public CameraTarget start = new CameraTarget(), end = new CameraTarget(), current = new CameraTarget();
    private boolean resetting = false;
    private float weight = 1.0f;

    @Override
    public void start(MinecraftClient client, Camera camera) {
        PlayerEntity player = client.player;
        if (player == null) return;
        start = CameraTarget.fromCamera(camera);
        current = CameraTarget.fromCamera(camera);
        end = getEndTarget(player, targetDistance);
        resetting = false;
        weight = 1.0f;
    }

    @Override
    public MovementState calculateState(MinecraftClient client, Camera camera) {
        PlayerEntity player = client.player;
        if (player == null) return new MovementState(current, true);

        start.set(player.getEyePos(),
                player.getYaw(),
                player.getPitch());
        end.set(getEndTarget(player, targetDistance));

        CameraTarget a, b;
        if (!resetting) {
            a = start;
            b = end;
        } else {
            a = end;
            b = start;
        }

        // Position interpolation with speed limit
        Vec3d desired = current.getPosition().lerp(b.getPosition(), positionEasing);
        Vec3d moveVector = desired.subtract(current.getPosition());
        double moveDistance = moveVector.length();
        if (moveDistance > 0.01) {
            double maxMove = positionSpeedLimit * (1.0/20.0); // Convert blocks/second to blocks/tick
            if (moveDistance > maxMove) {
                Vec3d limitedMove = moveVector.normalize().multiply(maxMove);
                desired = current.getPosition().add(limitedMove);
            }
        }

        // Rotation interpolation with speed limit
        float targetYaw = b.getYaw();
        float targetPitch = b.getPitch();

        // Calculate rotation with easing first
        float yawDiff = targetYaw - current.getYaw();
        float pitchDiff = targetPitch - current.getPitch();

// Normalize angles to [-180, 180]
        while (yawDiff > 180) yawDiff -= 360;
        while (yawDiff < -180) yawDiff += 360;

// Apply easing to get desired rotation speed
        float desiredYawSpeed = (float)(yawDiff * rotationEasing);
        float desiredPitchSpeed = (float)(pitchDiff * rotationEasing);

// Apply rotation speed limit
        float maxRotation = (float)(rotationSpeedLimit * (1.0/20.0)); // Convert degrees/second to degrees/tick
        if (Math.abs(desiredYawSpeed) > maxRotation) {
            desiredYawSpeed = Math.signum(desiredYawSpeed) * maxRotation;
        }
        if (Math.abs(desiredPitchSpeed) > maxRotation) {
            desiredPitchSpeed = Math.signum(desiredPitchSpeed) * maxRotation;
        }

        // Apply the final rotation
        float newYaw = current.getYaw() + desiredYawSpeed;
        float newPitch = current.getPitch() + desiredPitchSpeed;

        current = new CameraTarget(
                desired,
                newYaw,
                newPitch
        );

        alpha = current.getPosition().distanceTo(b.getPosition()) / a.getPosition().distanceTo(b.getPosition());
        boolean complete = resetting && moveDistance < 0.01;
        return new MovementState(current, complete);
    }

    @Override
    public void queueReset(MinecraftClient client, Camera camera) {
        if (client.player == null) return;
        resetting = true;
    }

    @Override
    public void adjustDistance(boolean increase) {
        double multiplier = increase ? 1.1 : 0.9;
        targetDistance = Math.max(minDistance, Math.min(maxDistance, targetDistance * multiplier));
    }

    @Override
    public String getName() {
        return "Linear";
    }

    @Override
    public float getWeight() {
        return weight;
    }

    @Override
    public boolean isComplete() {
        return resetting && current.getPosition().distanceTo(start.getPosition()) < 0.01;
    }
}