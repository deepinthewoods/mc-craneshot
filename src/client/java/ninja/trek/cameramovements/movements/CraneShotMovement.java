package ninja.trek.cameramovements.movements;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import ninja.trek.cameramovements.CameraMovementType;
import ninja.trek.cameramovements.CameraTarget;
import ninja.trek.cameramovements.ICameraMovement;
import ninja.trek.cameramovements.MovementState;
import ninja.trek.cameramovements.RaycastType;
import ninja.trek.config.AbstractMovementSettings;
import ninja.trek.config.MovementSetting;
import java.util.Random;

@CameraMovementType(
        name = "Crane Shot Movement",
        description = "Creates a curved camera path through a displaced midpoint"
)
public class CraneShotMovement extends AbstractMovementSettings implements ICameraMovement {
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

    @MovementSetting(label = "Min Displacement", min = 0.0, max = 10.0)
    private double minDisplacement = 2.0;

    @MovementSetting(label = "Max Displacement", min = 0.0, max = 20.0)
    private double maxDisplacement = 5.0;

    private CameraTarget start = new CameraTarget();
    private CameraTarget midpoint = new CameraTarget();
    private CameraTarget end = new CameraTarget();
    private CameraTarget current = new CameraTarget();
    private boolean resetting = false;
    private float weight = 1.0f;
    private double progress = 0.0;
    private Random random = new Random();

    @Override
    public void start(MinecraftClient client, Camera camera) {
        PlayerEntity player = client.player;
        if (player == null) return;

        // Set start and current position from camera
        start = CameraTarget.fromCamera(camera);
        current = CameraTarget.fromCamera(camera);

        // Calculate end position
        end = getEndTarget(player, targetDistance);

        // Calculate midpoint with perpendicular displacement
        Vec3d startPos = start.getPosition();
        Vec3d endPos = end.getPosition();

        // Calculate direction vector from start to end
        Vec3d direction = endPos.subtract(startPos);

        // Calculate perpendicular vector (cross product with up vector)
        Vec3d perp = direction.crossProduct(new Vec3d(0, 1, 0)).normalize();

        // Generate random displacement amount within range
        double displacement = minDisplacement + random.nextDouble() * (maxDisplacement - minDisplacement);

        // Calculate midpoint position
        Vec3d midPos = startPos.add(direction.multiply(0.5)).add(perp.multiply(displacement));

        // Set midpoint with interpolated rotation
        float midYaw = (start.getYaw() + end.getYaw()) / 2.0f;
        float midPitch = (start.getPitch() + end.getPitch()) / 2.0f;
        midpoint = new CameraTarget(midPos, midYaw, midPitch);

        resetting = false;
        weight = 1.0f;
        progress = 0.0;
    }

    @Override
    public MovementState calculateState(MinecraftClient client, Camera camera) {
        PlayerEntity player = client.player;
        if (player == null) return new MovementState(current, true);

        // Update progress based on direction (forward or reverse)
        double progressDelta = (1.0/20.0) * positionSpeedLimit; // Convert to progress per tick
        if (resetting) {
            progress -= progressDelta;
            if (progress < 0.0) progress = 0.0;
        } else {
            progress += progressDelta;
            if (progress > 1.0) progress = 1.0;
        }

        // Calculate quadratic bezier curve position
        Vec3d p0 = start.getPosition();
        Vec3d p1 = midpoint.getPosition();
        Vec3d p2 = end.getPosition();

        // Quadratic bezier formula: B(t) = (1-t)²P0 + 2(1-t)tP1 + t²P2
        double t = progress;
        double oneMinusT = 1.0 - t;
        Vec3d position = p0.multiply(oneMinusT * oneMinusT)
                .add(p1.multiply(2 * oneMinusT * t))
                .add(p2.multiply(t * t));

        // Interpolate rotation
        float yaw = lerpAngle(
                lerpAngle(start.getYaw(), midpoint.getYaw(), (float)t),
                lerpAngle(midpoint.getYaw(), end.getYaw(), (float)t),
                (float)t
        );
        float pitch = lerpAngle(
                lerpAngle(start.getPitch(), midpoint.getPitch(), (float)t),
                lerpAngle(midpoint.getPitch(), end.getPitch(), (float)t),
                (float)t
        );

        // Update current position and rotation
        current = new CameraTarget(position, yaw, pitch);

        // Return completion state based on direction
        return new MovementState(current, resetting && progress <= 0.0);
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
    }

    @Override
    public void adjustDistance(boolean increase) {
        double multiplier = increase ? 1.1 : 0.9;
        targetDistance = Math.max(minDistance, Math.min(maxDistance, targetDistance * multiplier));
    }

    @Override
    public String getName() {
        return "Crane Shot";
    }

    @Override
    public float getWeight() {
        return weight;
    }

    @Override
    public boolean isComplete() {
        return resetting && progress <= 0.0;
    }

    @Override
    public RaycastType getRaycastType() {
        return RaycastType.FAR;
    }
}