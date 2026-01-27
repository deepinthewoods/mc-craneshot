package ninja.trek.cameramovements.movements;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import ninja.trek.CameraController;
import ninja.trek.cameramovements.*;
import ninja.trek.config.MovementSetting;
import ninja.trek.mixin.client.FovAccessor;


@CameraMovementType(
        name = "Spring Linear",
        description = "Linear movement using critically damped springs for all phases"
)
public class SpringLinearMovement extends AbstractMovementSettings implements ICameraMovement {

    @MovementSetting(label = "Position Halflife", min = 0.01, max = 1.0)
    private double positionHalflife = 0.15;

    @MovementSetting(label = "Rotation Halflife", min = 0.01, max = 1.0)
    private double rotationHalflife = 0.15;

    @MovementSetting(label = "Target Distance", min = 0.0, max = 50.0)
    private double targetDistance = 10.0;

    @MovementSetting(label = "Min Distance", min = 0.0, max = 10.0)
    private double minDistance = 2.0;

    @MovementSetting(label = "Max Distance", min = 10.0, max = 50.0)
    private double maxDistance = 20.0;

    @MovementSetting(label = "FOV Halflife", min = 0.01, max = 1.0)
    private double fovHalflife = 0.15;

    public CameraTarget start = new CameraTarget();
    private CameraTarget end = new CameraTarget();
    public CameraTarget current = new CameraTarget();

    private boolean resetting = false;
    private float weight = 1.0f;

    // Spring velocity state - tracks momentum for smooth movement
    private Vec3d positionVelocity = Vec3d.ZERO;
    private float yawVelocity = 0f;
    private float pitchVelocity = 0f;
    private float fovVelocity = 0f;

    @Override
    public void start(MinecraftClient client, Camera camera) {
        start = CameraTarget.fromCamera(camera);
        current = CameraTarget.fromCamera(camera);

        Vec3d targetPos = calculateTargetPosition(CameraController.controlStick);
        end = new CameraTarget(targetPos, CameraController.controlStick.getYaw(),
                CameraController.controlStick.getPitch(), fovMultiplier);

        resetting = false;
        weight = 1.0f;
        alpha = 1;

        // Initialize spring velocities to zero
        positionVelocity = Vec3d.ZERO;
        yawVelocity = 0f;
        pitchVelocity = 0f;
        fovVelocity = 0f;
    }

    private Vec3d calculateTargetPosition(CameraTarget stick) {
        double yaw = Math.toRadians(stick.getYaw());
        double pitch = Math.toRadians(stick.getPitch());
        double xOffset = Math.sin(yaw) * Math.cos(pitch) * targetDistance;
        double yOffset = Math.sin(pitch) * targetDistance;
        double zOffset = -Math.cos(yaw) * Math.cos(pitch) * targetDistance;
        return stick.getPosition().add(xOffset, yOffset, zOffset);
    }

    /**
     * Critically damped spring using exact solution.
     * Updates position and velocity atomically.
     *
     * Based on "Spring-It-On" by Daniel Holden.
     * halflife = time for spring to move halfway to target
     */
    private Vec3d springDamperExact3D(Vec3d pos, Vec3d vel, Vec3d target, double halflife, double dt) {
        double damping = (4.0 * 0.69314718056) / Math.max(halflife, 0.001);
        double y = damping / 2.0;
        double eydt = Math.exp(-y * dt);

        Vec3d j0 = pos.subtract(target);
        Vec3d j1 = vel.add(j0.multiply(y));

        Vec3d newPos = j0.add(j1.multiply(dt)).multiply(eydt).add(target);
        // Update velocity for next frame
        positionVelocity = vel.subtract(j1.multiply(y * dt)).multiply(eydt);

        return newPos;
    }

    /**
     * 1D spring for scalar values (rotation, fov)
     */
    private float springDamperExact1D(float pos, float vel, float target, double halflife, double dt, boolean isAngle) {
        // Handle angle wrapping
        float error = target - pos;
        if (isAngle) {
            while (error > 180) error -= 360;
            while (error < -180) error += 360;
            target = pos + error;
        }

        double damping = (4.0 * 0.69314718056) / Math.max(halflife, 0.001);
        double y = damping / 2.0;
        double eydt = Math.exp(-y * dt);

        float j0 = pos - target;
        float j1 = vel + j0 * (float)y;

        return (float)((j0 + j1 * dt) * eydt + target);
    }

    private float springVelocityUpdate1D(float vel, float pos, float target, double halflife, double dt, boolean isAngle) {
        float error = target - pos;
        if (isAngle) {
            while (error > 180) error -= 360;
            while (error < -180) error += 360;
            target = pos + error;
        }

        double damping = (4.0 * 0.69314718056) / Math.max(halflife, 0.001);
        double y = damping / 2.0;
        double eydt = Math.exp(-y * dt);

        float j0 = pos - target;
        float j1 = vel + j0 * (float)y;

        return (float)((vel - j1 * y * dt) * eydt);
    }

    @Override
    public MovementState calculateState(MinecraftClient client, Camera camera, float deltaSeconds) {
        if (client.player == null) return new MovementState(current, true);

        // Determine target based on phase
        Vec3d targetPos;
        float targetYaw;
        float targetPitch;
        float targetFov;

        if (resetting) {
            // Return phase: target is player's head
            targetPos = client.player.getEyePos();
            targetYaw = client.player.getYaw();
            targetPitch = client.player.getPitch();
            targetFov = 1.0f;
        } else {
            // Out phase: target is offset position behind/around player
            targetPos = calculateTargetPosition(CameraController.controlStick);
            targetYaw = CameraController.controlStick.getYaw();
            targetPitch = CameraController.controlStick.getPitch();
            targetFov = fovMultiplier;

            // Update end for alpha calculation
            end = new CameraTarget(targetPos, targetYaw, targetPitch, targetFov);
        }

        // Apply springs for position
        Vec3d newPos = springDamperExact3D(
            current.getPosition(),
            positionVelocity,
            targetPos,
            positionHalflife,
            deltaSeconds
        );

        // Apply springs for rotation
        float newYaw = springDamperExact1D(current.getYaw(), yawVelocity, targetYaw, rotationHalflife, deltaSeconds, true);
        yawVelocity = springVelocityUpdate1D(yawVelocity, current.getYaw(), targetYaw, rotationHalflife, deltaSeconds, true);

        float newPitch = springDamperExact1D(current.getPitch(), pitchVelocity, targetPitch, rotationHalflife, deltaSeconds, false);
        pitchVelocity = springVelocityUpdate1D(pitchVelocity, current.getPitch(), targetPitch, rotationHalflife, deltaSeconds, false);

        // Apply spring for FOV
        float newFov = springDamperExact1D(current.getFovMultiplier(), fovVelocity, targetFov, fovHalflife, deltaSeconds, false);
        fovVelocity = springVelocityUpdate1D(fovVelocity, current.getFovMultiplier(), targetFov, fovHalflife, deltaSeconds, false);

        current = new CameraTarget(newPos, newYaw, newPitch, newFov);

        // Update FOV in game renderer
        if (client.gameRenderer instanceof FovAccessor) {
            ((FovAccessor) client.gameRenderer).setFovModifier(current.getFovMultiplier());
        }

        // Update alpha based on distance progress
        if (!resetting) {
            Vec3d startPos = CameraController.controlStick.getPosition();
            double totalDistance = startPos.distanceTo(end.getPosition());
            double remaining = current.getPosition().distanceTo(end.getPosition());
            alpha = totalDistance > 0.001 ? remaining / totalDistance : 0.0;
        } else {
            double remaining = current.getPosition().distanceTo(targetPos);
            alpha = remaining;
        }

        // Completion check
        boolean complete = resetting &&
            current.getPosition().distanceTo(targetPos) < 0.005 &&
            Math.abs(current.getFovMultiplier() - 1.0f) < 0.01f;

        return new MovementState(current, complete);
    }

    @Override
    public void queueReset(MinecraftClient client, Camera camera) {
        if (!resetting) {
            resetting = true;
            resetReturnTargetTracking();
            current = CameraTarget.fromCamera(camera);

            // Keep existing velocity for smooth transition into return phase
            // The spring will naturally redirect toward the new target

            if (client.player != null) {
                float playerYaw = client.player.getYaw();
                float playerPitch = client.player.getPitch();
                Vec3d playerPos = client.player.getEyePos();
                end = new CameraTarget(playerPos, playerYaw, playerPitch, 1.0f);
            }
        }
    }

    public boolean isResetting() {
        return resetting;
    }

    public void resumeOutPhase(MinecraftClient client, Camera camera) {
        if (!resetting) {
            return;
        }
        resetting = false;
        if (camera != null) {
            current = CameraTarget.fromCamera(camera);
        }

        // Keep velocity for smooth transition back to out phase

        Vec3d targetPos = calculateTargetPosition(CameraController.controlStick);
        end = new CameraTarget(
                targetPos,
                CameraController.controlStick.getYaw(),
                CameraController.controlStick.getPitch(),
                fovMultiplier
        );

        Vec3d startPos = CameraController.controlStick.getPosition();
        double totalDistance = startPos.distanceTo(end.getPosition());
        double remaining = current.getPosition().distanceTo(end.getPosition());
        alpha = totalDistance > 0.001 ? remaining / totalDistance : 0.0;
    }

    @Override
    public void adjustDistance(boolean increase, MinecraftClient client) {
        if (mouseWheel == SCROLL_WHEEL.DISTANCE) {
            double multiplier = increase ? 1.2 : 0.8;
            targetDistance = Math.max(minDistance, Math.min(maxDistance, targetDistance * multiplier));
        } else if (mouseWheel == SCROLL_WHEEL.FOV) {
            adjustFov(increase, client);
        }
    }

    @Override
    public void adjustFov(boolean increase, MinecraftClient client) {
        if (mouseWheel != SCROLL_WHEEL.FOV) return;
        super.adjustFov(increase, client);
    }

    @Override
    public String getName() {
        return "Spring Linear";
    }

    @Override
    public float getWeight() {
        return weight;
    }

    @Override
    public boolean isComplete() {
        if (resetting) {
            double positionDistance = current.getPosition().distanceTo(end.getPosition());
            float fovDifference = Math.abs(current.getFovMultiplier() - 1.0f);
            return positionDistance < 0.005 && fovDifference < 0.01f;
        }
        return false;
    }

    @Override
    public boolean hasCompletedOutPhase() {
        if (resetting) return false;
        return alpha < 0.1;
    }
}
