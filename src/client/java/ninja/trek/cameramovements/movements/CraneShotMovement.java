package ninja.trek.cameramovements.movements;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import ninja.trek.cameramovements.*;
import ninja.trek.config.MovementSetting;

import java.util.Random;

@CameraMovementType(
        name = "Crane Shot Movement",
        description = "Creates a curved camera path that follows player movement"
)
public class CraneShotMovement extends AbstractMovementSettings implements ICameraMovement {
    private static final double FINAL_APPROACH_SPEED = 0.05;
    private static final double FINAL_APPROACH_THRESHOLD = 1.0;

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

    // Store relative positions instead of world positions
    private Vec3d relativeStartPos;
    private Vec3d relativeMidPos;
    private Vec3d relativeEndPos;
    private float relativeStartYaw;
    private float relativeStartPitch;
    private float relativeMidYaw;
    private float relativeMidPitch;
    private float relativeEndYaw;
    private float relativeEndPitch;

    private CameraTarget current = new CameraTarget();
    private boolean resetting = false;
    private float weight = 1.0f;
    private double progress = 0.0;
    private double pathLength;
    private Random random = new Random();

    private Vec3d initialPlayerPos;
    private float initialPlayerYaw;
    private float initialPlayerPitch;

    @Override
    public void start(MinecraftClient client, Camera camera) {
        PlayerEntity player = client.player;
        if (player == null) return;

        // Store initial player state
        initialPlayerPos = player.getEyePos();
        initialPlayerYaw = player.getYaw();
        initialPlayerPitch = player.getPitch();

        // Calculate and store relative positions
        calculateRelativePositions(player, camera);

        // Initialize current camera position
        current = CameraTarget.fromCamera(camera);

        resetting = false;
        weight = 1.0f;
        progress = 0.0;
    }

    private void calculateRelativePositions(PlayerEntity player, Camera camera) {
        // Store start position relative to player
        relativeStartPos = camera.getPos().subtract(player.getEyePos());
        relativeStartYaw = camera.getYaw() - player.getYaw();
        relativeStartPitch = camera.getPitch() - player.getPitch();

        // Calculate relative end position
        Vec3d endDirection = new Vec3d(
                Math.sin(Math.toRadians(player.getYaw() + 180)) * Math.cos(Math.toRadians(-player.getPitch())),
                Math.sin(Math.toRadians(-player.getPitch())),
                -Math.cos(Math.toRadians(player.getYaw() + 180)) * Math.cos(Math.toRadians(-player.getPitch()))
        ).multiply(targetDistance);
        relativeEndPos = endDirection;
        relativeEndYaw = 180; // Relative to player viewing direction
        relativeEndPitch = -player.getPitch();

        // Calculate relative midpoint with displacement
        Vec3d midDirection = relativeEndPos.subtract(relativeStartPos);
        Vec3d perpVector = midDirection.crossProduct(new Vec3d(0, 1, 0)).normalize();
        double displacement = minDisplacement + random.nextDouble() * (maxDisplacement - minDisplacement);
        relativeMidPos = relativeStartPos.add(midDirection.multiply(0.5)).add(perpVector.multiply(displacement));
        relativeMidYaw = (relativeStartYaw + relativeEndYaw) / 2.0f;
        relativeMidPitch = (relativeStartPitch + relativeEndPitch) / 2.0f;

        // Calculate approximate path length
        pathLength = relativeMidPos.subtract(relativeStartPos).length() +
                relativeEndPos.subtract(relativeMidPos).length();
    }

    @Override
    public MovementState calculateState(MinecraftClient client, Camera camera) {
        PlayerEntity player = client.player;
        if (player == null) return new MovementState(current, true);

        // Get current player state
        Vec3d playerPos = player.getEyePos();
        float playerYaw = player.getYaw();
        float playerPitch = player.getPitch();

        // Calculate world space positions based on current player state
        Vec3d worldStartPos = playerPos.add(rotateVector(relativeStartPos,
                playerYaw - initialPlayerYaw,
                playerPitch - initialPlayerPitch));
        Vec3d worldMidPos = playerPos.add(rotateVector(relativeMidPos,
                playerYaw - initialPlayerYaw,
                playerPitch - initialPlayerPitch));
        Vec3d worldEndPos = playerPos.add(rotateVector(relativeEndPos,
                playerYaw - initialPlayerYaw,
                playerPitch - initialPlayerPitch));

        // Calculate target position along the curve
        double t = progress;
        double oneMinusT = 1.0 - t;
        Vec3d targetPos;
        if (resetting) {
            // For reset, swap start and end points but keep same curve
            targetPos = worldEndPos.multiply(oneMinusT * oneMinusT)
                    .add(worldMidPos.multiply(2 * oneMinusT * t))
                    .add(worldStartPos.multiply(t * t));
        } else {
            targetPos = worldStartPos.multiply(oneMinusT * oneMinusT)
                    .add(worldMidPos.multiply(2 * oneMinusT * t))
                    .add(worldEndPos.multiply(t * t));
        }

        // Apply movement constraints
        Vec3d moveVector = targetPos.subtract(current.getPosition());
        double moveDistance = moveVector.length();

        // Handle final approach
        Vec3d finalTarget = resetting ? worldStartPos : worldEndPos;
        double distanceToFinal = current.getPosition().distanceTo(finalTarget);

        if (distanceToFinal <= FINAL_APPROACH_THRESHOLD) {
            moveVector = finalTarget.subtract(current.getPosition())
                    .normalize()
                    .multiply(FINAL_APPROACH_SPEED);
        } else {
            // Apply normal position speed limit
            double maxMove = positionSpeedLimit * (1.0/20.0);
            if (moveDistance > maxMove) {
                moveVector = moveVector.normalize().multiply(maxMove);
            }
            moveVector = moveVector.multiply(positionEasing);
        }

        // Update position
        Vec3d newPos = current.getPosition().add(moveVector);

        // Calculate target rotation
        float targetYaw = resetting ?
                playerYaw + relativeStartYaw :
                lerpAngle(
                        lerpAngle(playerYaw + relativeStartYaw,
                                playerYaw + relativeMidYaw, (float)t),
                        lerpAngle(playerYaw + relativeMidYaw,
                                playerYaw + relativeEndYaw, (float)t),
                        (float)t
                );

        float targetPitch = resetting ?
                playerPitch + relativeStartPitch :
                lerpAngle(
                        lerpAngle(playerPitch + relativeStartPitch,
                                playerPitch + relativeMidPitch, (float)t),
                        lerpAngle(playerPitch + relativeMidPitch,
                                playerPitch + relativeEndPitch, (float)t),
                        (float)t
                );

        // Apply rotation constraints
        float yawDiff = targetYaw - current.getYaw();
        float pitchDiff = targetPitch - current.getPitch();

        while (yawDiff > 180) yawDiff -= 360;
        while (yawDiff < -180) yawDiff += 360;

        float desiredYawSpeed = (float)(yawDiff * rotationEasing);
        float desiredPitchSpeed = (float)(pitchDiff * rotationEasing);

        float maxRotation = (float)(rotationSpeedLimit * (1.0/20.0));
        if (Math.abs(desiredYawSpeed) > maxRotation) {
            desiredYawSpeed = Math.signum(desiredYawSpeed) * maxRotation;
        }
        if (Math.abs(desiredPitchSpeed) > maxRotation) {
            desiredPitchSpeed = Math.signum(desiredPitchSpeed) * maxRotation;
        }

        float newYaw = current.getYaw() + desiredYawSpeed;
        float newPitch = current.getPitch() + desiredPitchSpeed;

        // Update current state
        current = new CameraTarget(newPos, newYaw, newPitch);

        // Update progress based on camera position along the path
        if (resetting) {
            // For reset phase, progress goes from 0 to 1 just like outward phase
            double endDist = newPos.distanceTo(worldStartPos);
            progress = Math.min(1.0, 1.0 - (endDist / pathLength));
        } else {
            double endDist = newPos.distanceTo(worldEndPos);
            progress = Math.min(1.0, 1.0 - (endDist / pathLength));
        }

        boolean complete = resetting && (
                newPos.distanceTo(worldStartPos) < 0.1 ||
                        progress <= 0.0
        );

        return new MovementState(current, complete);
    }

    private Vec3d rotateVector(Vec3d vec, float yawDiff, float pitchDiff) {
        // Convert angles to radians
        double yawRad = Math.toRadians(yawDiff);
        double pitchRad = Math.toRadians(pitchDiff);

        // Get trig values
        double sy = Math.sin(yawRad);
        double cy = Math.cos(yawRad);
        double sp = Math.sin(pitchRad);
        double cp = Math.cos(pitchRad);

        // Apply yaw and pitch rotations
        // First rotate around Y axis (yaw)
        double x1 = vec.x * cy - vec.z * sy;
        double y1 = vec.y;
        double z1 = vec.x * sy + vec.z * cy;

        // Then rotate around X axis (pitch)
        return new Vec3d(
                x1,
                y1 * cp - z1 * sp,
                y1 * sp + z1 * cp
        );
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
        return resetting && current.getPosition().distanceTo(
                MinecraftClient.getInstance().player.getEyePos().add(relativeStartPos)
        ) < 0.1;
    }

    @Override
    public RaycastType getRaycastType() {
        return RaycastType.FAR;
    }
}