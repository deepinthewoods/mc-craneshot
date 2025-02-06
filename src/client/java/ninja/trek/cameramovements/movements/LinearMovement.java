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
        description = "Moves the camera with linear interpolation and separate position/rotation controls"
)
public class LinearMovement extends AbstractMovementSettings implements ICameraMovement {
    @MovementSetting(label = "Position Easing", min = 0.01, max = 1.0)
    private double positionEasing = 0.1;

    @MovementSetting(label = "Rotation Easing", min = 0.01, max = 1.0)
    private double rotationEasing = 0.1;

    @MovementSetting(label = "Position Speed Limit", min = 0.1, max = 100.0)
    private double positionSpeedLimit = 2.0;

    @MovementSetting(label = "Rotation Speed Limit", min = 0.1, max = 360.0)
    private double rotationSpeedLimit = 45.0;

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

        // Calculate rotation movement
        float currentYaw = currentTarget.getYaw();
        float currentPitch = currentTarget.getPitch();
        float targetYaw = destinationTarget.getYaw();
        float targetPitch = destinationTarget.getPitch();

        // First calculate desired rotation with easing
        float desiredYaw = lerpAngle(currentYaw, targetYaw, (float)rotationEasing);
        float desiredPitch = lerpAngle(currentPitch, targetPitch, (float)rotationEasing);

        // Apply rotation speed limit
        float yawDiff = angleDifference(currentYaw, desiredYaw);
        float pitchDiff = angleDifference(currentPitch, desiredPitch);
        float maxRotation = (float)(rotationSpeedLimit * (1.0/20.0)); // Convert degrees/second to degrees/tick

        if (Math.abs(yawDiff) > maxRotation) {
            desiredYaw = currentYaw + Math.signum(yawDiff) * maxRotation;
        }
        if (Math.abs(pitchDiff) > maxRotation) {
            desiredPitch = currentPitch + Math.signum(pitchDiff) * maxRotation;
        }

        // Create new camera target with calculated position and rotation
        currentTarget = new CameraTarget(desiredPos, desiredYaw, desiredPitch, getRaycastType());

        // Check if movement is complete
        boolean complete = resetting &&
                currentPos.distanceTo(targetPos) < 0.01 &&
                Math.abs(angleDifference(currentYaw, targetYaw)) < 0.1 &&
                Math.abs(angleDifference(currentPitch, targetPitch)) < 0.1;

        return new MovementState(currentTarget, complete);
    }

    private float lerpAngle(float start, float end, float t) {
        float diff = angleDifference(start, end);
        return start + diff * t;
    }

    private float angleDifference(float start, float end) {
        float diff = end - start;
        while (diff > 180) diff -= 360;
        while (diff < -180) diff += 360;
        return diff;
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