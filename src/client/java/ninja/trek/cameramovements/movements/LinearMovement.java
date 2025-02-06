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
        currentTarget = CameraTarget.fromCamera(camera, getRaycastType());
        destinationTarget = CameraTarget.fromDistance(player, targetDistance, getRaycastType());

        resetting = false;
        weight = 1.0f;
    }

    @Override
    public MovementState calculateState(MinecraftClient client, Camera camera) {
        PlayerEntity player = client.player;
        if (player == null) return new MovementState(currentTarget, true);

        // Update destination target if not resetting
        if (!resetting) {
            destinationTarget = CameraTarget.fromDistance(player, targetDistance, getRaycastType());
        }

        // Calculate position movement
        Vec3d currentPos = currentTarget.getPosition();
        Vec3d targetPos = destinationTarget.getPosition();
        Vec3d desiredPos = currentPos.lerp(targetPos, positionEasing);

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

        // Create new camera target with calculated position
        currentTarget = new CameraTarget(
                desiredPos,
                destinationTarget.getYaw(),
                destinationTarget.getPitch(),
                getRaycastType()
        );

        // Check if movement is complete
        boolean complete = resetting && currentPos.distanceTo(targetPos) < 0.01;

        return new MovementState(currentTarget, complete);
    }

    @Override
    public void queueReset(MinecraftClient client, Camera camera) {
        if (client.player == null) return;
        resetting = true;
        destinationTarget = new CameraTarget(
                client.player.getEyePos(),
                client.player.getYaw(),
                client.player.getPitch(),
                getRaycastType()
        );
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
        return resetting && MinecraftClient.getInstance().player != null &&
                currentTarget.getPosition().distanceTo(MinecraftClient.getInstance().player.getEyePos()) < 0.1;
    }
}