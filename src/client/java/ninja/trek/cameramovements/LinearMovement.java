package ninja.trek.cameramovements;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import ninja.trek.config.AbstractMovementSettings;
import ninja.trek.config.MovementSetting;

public class LinearMovement extends AbstractMovementSettings implements ICameraMovement {
    @MovementSetting(label = "Position Easing Factor", min = 0.01, max = 1.0)
    private double positionEasingFactor = 0.1f;

    @MovementSetting(label = "Rotation Easing Factor", min = 0.01, max = 1.0)
    private double rotationEasingFactor = 0.1f;

    @MovementSetting(label = "Target Distance", min = 1.0, max = 50.0)
    private double targetDistance = 10;

    @MovementSetting(label = "Min Distance", min = 1.0, max = 10.0)
    private double minDistance = 2.0f;

    @MovementSetting(label = "Max Distance", min = 10.0, max = 50.0)
    private double maxDistance = 20.0f;

    private CameraState currentState;
    private boolean resetting = false;
    private float weight = 1.0f;

    @Override
    public void start(MinecraftClient client, Camera camera) {
        PlayerEntity player = client.player;
        if (player == null) return;

        currentState = CameraState.fromCamera(camera);
        resetting = false;
        weight = 1.0f;
    }

    @Override
    public MovementState calculateState(MinecraftClient client, Camera camera) {
        PlayerEntity player = client.player;
        if (player == null) return new MovementState(currentState, true);

        // Calculate desired camera position based on player position and rotation
        Vec3d playerEyePos = player.getEyePos();
        float playerYaw = player.getYaw();
        float playerPitch = player.getPitch();

        // Calculate target position
        double yaw = Math.toRadians(playerYaw);
        double pitch = Math.toRadians(playerPitch);
        double distance = resetting ? 0 : targetDistance;

        double xOffset = Math.sin(yaw) * Math.cos(pitch) * distance;
        double yOffset = Math.sin(pitch) * distance;
        double zOffset = -Math.cos(yaw) * Math.cos(pitch) * distance;

        Vec3d targetPos = playerEyePos.add(xOffset, yOffset, zOffset);
        CameraState targetState = new CameraState(targetPos, playerYaw, playerPitch);

        // Interpolate current state towards target
        currentState = currentState.lerp(targetState, resetting ? 0.2 : positionEasingFactor);

        boolean complete = resetting && targetPos.distanceTo(currentState.getPosition()) < 0.1;

        return new MovementState(currentState, complete);
    }

    @Override
    public void reset(MinecraftClient client, Camera camera) {
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
        return resetting && currentState.getPosition().distanceTo(currentState.getPosition()) < 0.1;
    }
}