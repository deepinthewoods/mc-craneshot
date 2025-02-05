package ninja.trek.cameramovements.movements;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.player.PlayerEntity;
import ninja.trek.cameramovements.*;
import ninja.trek.config.AbstractMovementSettings;
import ninja.trek.config.MovementSetting;

@CameraMovementType(
        name = "Easing Movement",
        description = "Moves the camera in a smooth eased motion"
)
public class EasingMovement extends AbstractMovementSettings implements ICameraMovement {
    @MovementSetting(label = "Position Easing Factor", min = 0.01, max = 1.0)
    private double positionEasingFactor = 0.1f;

    @MovementSetting(label = "Target Distance", min = 1.0, max = 50.0)
    private double targetDistance = 10;

    @MovementSetting(label = "Min Distance", min = 1.0, max = 10.0)
    private double minDistance = 2.0f;

    @MovementSetting(label = "Max Distance", min = 10.0, max = 50.0)
    private double maxDistance = 20.0f;

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

        // Update destination if not resetting
        if (!resetting) {
            destinationTarget = CameraTarget.fromDistance(player, targetDistance, getRaycastType());
        }

        // Calculate new position and rotation with easing
        currentTarget = currentTarget.lerp(destinationTarget, positionEasingFactor);

        // Check if we're close enough to complete
        boolean complete = resetting &&
                currentTarget.getPosition().distanceTo(player.getEyePos()) < 0.1;

        return new MovementState(currentTarget, complete);
    }

    @Override
    public void queueReset(MinecraftClient client, Camera camera) {
        if (client.player == null) return;
        resetting = true;
        // Instead of using fromPlayer which defaults to NONE,
        // create a new CameraTarget using the current raycast type.
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
        return "Easing";
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