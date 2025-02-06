
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

    //private CameraTarget currentTarget;
    //private CameraTarget destinationTarget;
    public CameraTarget start = new CameraTarget(), end = new CameraTarget(), current = new CameraTarget();
    private boolean resetting = false;
    private float weight = 1.0f;

    @Override
    public void start(MinecraftClient client, Camera camera) {
        PlayerEntity player = client.player;
        if (player == null) return;

        start = CameraTarget.fromCamera(camera);
        current = CameraTarget.fromCamera(camera);
        end = CameraTarget.fromDistance(player, targetDistance);
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
        end.set(CameraTarget.fromDistance(player, targetDistance));
        CameraTarget a, b;
        if (!resetting) {
            a = start;
            b = end;
        } else {
            a = end;
            b = start;
        }

        // Calculate position
//        Vec3d currentPos = currentTarget.getPosition();
//        Vec3d targetPos = destinationTarget.getPosition();
//        Vec3d desiredPos = currentPos.lerp(targetPos, positionEasing);

        Vec3d desired = current.getPosition().lerp(b.getPosition(), positionEasing);

        // Apply position speed limit
        Vec3d moveVector = desired.subtract(current.getPosition());
        double moveDistance = moveVector.length();
        if (moveDistance > 0.01) {
            double maxMove = positionSpeedLimit * (1.0/20.0); // Convert blocks/second to blocks/tick
            if (moveDistance > maxMove) {
                Vec3d limitedMove = moveVector.normalize().multiply(maxMove);
                desired = current.getPosition().add(limitedMove);
            }
        }
//        // Apply position speed limit
//        Vec3d moveVector = desiredPos.subtract(currentPos);
//        double moveDistance = moveVector.length();
//        if (moveDistance > 0.01) {
//            double maxMove = positionSpeedLimit * (1.0/20.0); // Convert blocks/second to blocks/tick
//            if (moveDistance > maxMove) {
//                Vec3d limitedMove = moveVector.normalize().multiply(maxMove);
//                desiredPos = currentPos.add(limitedMove);
//            }
//        }

        current = new CameraTarget(
                desired,
                end.getYaw(),
                end.getPitch()
        );

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
