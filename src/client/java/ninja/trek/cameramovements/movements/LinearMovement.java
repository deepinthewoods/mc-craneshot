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
        description = "Moves the camera in a straight line at constant speed"
)
public class LinearMovement extends AbstractMovementSettings implements ICameraMovement {
    @MovementSetting(label = "Movement Speed", min = 0.1, max = 5.0)
    private double movementSpeed = 1.0;

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

        // Calculate movement for this frame
        double distanceToMove = movementSpeed * (1.0/20.0); // Convert to blocks per tick
        Vec3d currentPos = currentTarget.getPosition();
        Vec3d targetPos = destinationTarget.getPosition();

        // Calculate direction vector
        double dx = targetPos.x - currentPos.x;
        double dy = targetPos.y - currentPos.y;
        double dz = targetPos.z - currentPos.z;
        double totalDistance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        boolean complete = false;
        if (totalDistance < distanceToMove) {
            // If we're close enough, snap to the destination
            currentTarget = destinationTarget;
            complete = resetting && totalDistance < 0.1;
        } else {
            // Normalize direction vector and multiply by speed
            double scale = distanceToMove / totalDistance;
            double newX = currentPos.x + dx * scale;
            double newY = currentPos.y + dy * scale;
            double newZ = currentPos.z + dz * scale;

            // Linear interpolation of rotation
            float progress = (float)(distanceToMove / totalDistance);
            float newYaw = lerpAngle(currentTarget.getYaw(), destinationTarget.getYaw(), progress);
            float newPitch = lerpAngle(currentTarget.getPitch(), destinationTarget.getPitch(), progress);

            currentTarget = new CameraTarget(
                    new Vec3d(newX, newY, newZ),
                    newYaw,
                    newPitch,
                    getRaycastType()
            );
        }
        return new MovementState(currentTarget, complete);
    }

    private float lerpAngle(float start, float end, float t) {
        float diff = end - start;
        while (diff > 180) diff -= 360;
        while (diff < -180) diff += 360;
        return start + diff * t;
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