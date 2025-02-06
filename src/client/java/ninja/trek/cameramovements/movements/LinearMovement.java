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

    @MovementSetting(label = "Target Distance", min = 1.0, max = 50.0)
    private double targetDistance = 10.0;

    @MovementSetting(label = "Min Distance", min = 1.0, max = 10.0)
    private double minDistance = 2.0;

    @MovementSetting(label = "Max Distance", min = 10.0, max = 50.0)
    private double maxDistance = 20.0;

    private CameraTarget currentTarget;
    private CameraTarget destinationTarget;
    private boolean resetting = false;
    private float weight = 1.0f;

    @Override
    public void start(MinecraftClient client, Camera camera) {
        PlayerEntity player = client.player;
        if (player == null) return;

        currentTarget = CameraTarget.fromCamera(camera);
        destinationTarget = CameraTarget.fromDistance(player, targetDistance);
        resetting = false;
        weight = 1.0f;
        alpha = 0.0; // Reset alpha at start
    }

    @Override
    public MovementState calculateState(MinecraftClient client, Camera camera) {
        PlayerEntity player = client.player;
        if (player == null) return new MovementState(currentTarget, true);

        if (!resetting) {
            destinationTarget = CameraTarget.fromDistance(player, targetDistance);
            alpha = Math.min(1.0, alpha + positionEasing);
        } else {
            destinationTarget = new CameraTarget(
                    player.getEyePos(),
                    player.getYaw(),
                    player.getPitch()
            );
            alpha = Math.min(1.0, alpha + positionEasing);
        }

        // Calculate position based on alpha
        Vec3d currentPos = currentTarget.getPosition();
        Vec3d targetPos = destinationTarget.getPosition();
        Vec3d desiredPos = currentPos.lerp(targetPos, alpha);

        // Apply position speed limit
        Vec3d moveVector = desiredPos.subtract(currentPos);
        double moveDistance = moveVector.length();
        if (moveDistance > 0.01) {
            double maxMove = positionSpeedLimit * (1.0/20.0); // Convert blocks/second to blocks/tick
            if (moveDistance > maxMove) {
                Vec3d limitedMove = moveVector.normalize().multiply(maxMove);
                desiredPos = currentPos.add(limitedMove);
            }
        }

        currentTarget = new CameraTarget(
                desiredPos,
                destinationTarget.getYaw(),
                destinationTarget.getPitch()
        );

        boolean complete = resetting && alpha >= 1.0;
        return new MovementState(currentTarget, complete);
    }

    @Override
    public void queueReset(MinecraftClient client, Camera camera) {
        if (client.player == null) return;
        resetting = true;
        alpha = 0.0; // Reset alpha when starting reset
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
        return resetting && alpha >= 1.0;
    }
}