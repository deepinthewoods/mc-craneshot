package ninja.trek.cameramovements;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import ninja.trek.mixin.client.CameraAccessor;

public class LinearMovement implements ICameraMovement {
    private static final float POSITION_EASING_FACTOR = 0.1f;
    private static final float ROTATION_EASING_FACTOR = 0.1f;
    private static final float DISTANCE_EASING_FACTOR = 0.1f;
    private static final double SCROLL_SENSITIVITY = 0.5;
    private static final double FIRST_PERSON_DISTANCE_THRESHOLD = 2.5;
    private static final double MIN_DISTANCE = 2.0;
    private static final double MAX_DISTANCE = 20.0;

    private double targetDistance = 10;
    private double currentDistance = 0;

    // Smoothing variables
    private Vec3d smoothedPlayerPos = new Vec3d(0, 0, 0);
    private double smoothedYaw = 0;
    private double smoothedPitch = 0;

    private boolean resetting = false;
    private boolean wasFirstPerson = true;

    @Override
    public void startTransition(MinecraftClient client, Camera camera) {
        PlayerEntity player = client.player;
        if (player == null) return;

        // Initialize smoothing variables to current player state
        smoothedPlayerPos = player.getPos();
        smoothedYaw = player.getYaw();
        smoothedPitch = player.getPitch();
        currentDistance = 0;
        resetting = false;
        wasFirstPerson = client.options.getPerspective() == Perspective.FIRST_PERSON;
    }

    @Override
    public boolean updateTransition(MinecraftClient client, Camera camera) {
        PlayerEntity player = client.player;
        if (player == null) return true;

        // Smooth position interpolation
        Vec3d playerEyePos = player.getEyePos();
        smoothedPlayerPos = interpolateVec3d(smoothedPlayerPos, playerEyePos, POSITION_EASING_FACTOR);

        // Smooth rotation interpolation
        smoothedYaw = lerpAngle(smoothedYaw, player.getYaw(), ROTATION_EASING_FACTOR);
        smoothedPitch = lerpAngle(smoothedPitch, player.getPitch(), ROTATION_EASING_FACTOR);

        // Calculate desired distance based on whether we're resetting
        double desiredDistance = resetting ? 0 : targetDistance;
        currentDistance += (desiredDistance - currentDistance) * DISTANCE_EASING_FACTOR;

        // Handle perspective changes
        if (currentDistance > FIRST_PERSON_DISTANCE_THRESHOLD &&
                client.options.getPerspective() == Perspective.FIRST_PERSON) {
            client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        } else if (currentDistance < FIRST_PERSON_DISTANCE_THRESHOLD &&
                client.options.getPerspective() != Perspective.FIRST_PERSON) {
            client.options.setPerspective(Perspective.FIRST_PERSON);
        }

        // Calculate camera position behind player using smoothed values
        double yaw = Math.toRadians(smoothedYaw);
        double pitch = Math.toRadians(smoothedPitch);

        // Calculate offset based on smoothed yaw and pitch
        double xOffset = Math.sin(yaw) * Math.cos(pitch) * currentDistance;
        double yOffset = Math.sin(pitch) * currentDistance;
        double zOffset = -Math.cos(yaw) * Math.cos(pitch) * currentDistance;

        Vec3d cameraPos = smoothedPlayerPos.add(0, player.getEyeHeight(player.getPose()), 0).add(xOffset, yOffset, zOffset);

        // Update camera position and rotation
        ((CameraAccessor)camera).invokesetPos(cameraPos);
        ((CameraAccessor)camera).invokeSetRotation((float)smoothedYaw, (float)smoothedPitch);

        // Return true only if we're fully reset
        return resetting && currentDistance < 0.01;
    }

    @Override
    public void reset(MinecraftClient client, Camera camera) {
        resetting = true;
    }

    @Override
    public void adjustDistance(boolean increase) {
        double multiplier = increase ? (1.0 / (1.0 + SCROLL_SENSITIVITY)) : (1.0 + SCROLL_SENSITIVITY);
        targetDistance = Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE, targetDistance * multiplier));
    }

    // Helper methods for smooth interpolation
    private Vec3d interpolateVec3d(Vec3d current, Vec3d target, float factor) {
        double x = current.x + (target.x - current.x) * factor;
        double y = current.y + (target.y - current.y) * factor;
        double z = current.z + (target.z - current.z) * factor;
        return new Vec3d(x, y, z);
    }

    private double lerpAngle(double current, double target, float factor) {
        // Ensure the angle difference is wrapped properly
        double diff = target - current;
        while (diff > 180) diff -= 360;
        while (diff < -180) diff += 360;

        return current + diff * factor;
    }
}