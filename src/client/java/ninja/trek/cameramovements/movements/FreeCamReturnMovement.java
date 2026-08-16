package ninja.trek.cameramovements.movements;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import ninja.trek.CameraController;
import ninja.trek.CraneshotClient;
 
import ninja.trek.cameramovements.*;
import ninja.trek.config.MovementSetting;

@CameraMovementType(
        name = "FreeCamReturn",
        description = "Return from freecam to the tracked target using critically damped springs",
        showInSlots = false
)
public class FreeCamReturnMovement extends AbstractMovementSettings implements ICameraMovement {
    private static final double MAX_RETURN_TARGET_DISTANCE = 256.0;
    private static final double MAX_TRACKED_TARGET_SPEED = 100.0;

    @MovementSetting(label = "Position Halflife", min = 0.01, max = 1.0)
    private double positionHalflife = 0.15;

    @MovementSetting(label = "Rotation Halflife", min = 0.01, max = 1.0)
    private double rotationHalflife = 0.15;

    @MovementSetting(label = "FOV Halflife", min = 0.01, max = 1.0)
    private double fovHalflife = 0.15;

    private CameraTarget start = new CameraTarget();
    private CameraTarget end = new CameraTarget();
    private CameraTarget current = new CameraTarget();

    private Vec3 positionVelocity = Vec3.ZERO;
    private float yawVelocity = 0.0f;
    private float pitchVelocity = 0.0f;
    private float fovVelocity = 0.0f;
    private Vec3 lastTargetPosition = null;

    private boolean isComplete = false;

    @Override
    public void start(Minecraft client, Camera camera) {
        resetReturnTargetTracking();
        // Force return target to player's head rotation for consistent return
        endTarget = END_TARGET.HEAD_BACK;
        CraneshotClient.CAMERA_CONTROLLER.setPreMoveStates(this);

        // Start from the exact freecam state tracked by controller
        Vec3 startPos = CameraController.freeCamPosition;
        float startYaw = CameraController.freeCamYaw;
        float startPitch = CameraController.freeCamPitch;
        float startFov = camera != null
                ? CameraTarget.fromCamera(camera).getFovMultiplier()
                : 1.0f;
        start = new CameraTarget(startPos, startYaw, startPitch, startFov);
        current = new CameraTarget(startPos, startYaw, startPitch, startFov);

        // Initial end target (will be updated every frame)
        end = resolveReturnTarget(client, 1.0f);
        lastTargetPosition = end.getPosition();
        // No startup log

        positionVelocity = Vec3.ZERO;
        yawVelocity = 0.0f;
        pitchVelocity = 0.0f;
        fovVelocity = 0.0f;
        isComplete = false;
    }

    @Override
    public MovementState calculateState(Minecraft client, Camera camera, float tickDelta, float deltaSeconds) {
        if (client == null || client.player == null) {
            return new MovementState(current, true);
        }

        // The destination follows the live stick controller pose while the
        // critically damped springs preserve momentum between frames.
        end = resolveReturnTarget(client, tickDelta);

        double dt = Math.max(0.0, Math.min(deltaSeconds, 0.25f));
        Vec3 targetVelocity = calculateTargetVelocity(end.getPosition(), dt);
        double distanceBeforeSpring = current.getPosition().distanceTo(end.getPosition());
        double closeRangeBoost = getCloseRangeReturnBoost(distanceBeforeSpring);
        double convergenceProgress = updateGuaranteedCloseRangeReturn(
                distanceBeforeSpring, deltaSeconds
        );
        double velocityMatchBlend = Math.max(closeRangeBoost, convergenceProgress);
        double effectivePositionHalflife = getGuaranteedCloseRangeReturnHalflife(
                positionHalflife, distanceBeforeSpring, convergenceProgress
        );
        double effectiveRotationHalflife = getGuaranteedCloseRangeReturnHalflife(
                rotationHalflife, distanceBeforeSpring, convergenceProgress
        );
        double effectiveFovHalflife = getGuaranteedCloseRangeReturnHalflife(
                fovHalflife, distanceBeforeSpring, convergenceProgress
        );
        Vec3 desiredPos = springPosition(
                current.getPosition(),
                positionVelocity,
                end.getPosition(),
                targetVelocity.scale(velocityMatchBlend),
                effectivePositionHalflife,
                dt
        );

        desiredPos = applyMinimumSpeedDuringReturn(
                current.getPosition(),
                desiredPos,
                end.getPosition(),
                deltaSeconds,
                client
        );

        SpringValue yawSpring = springValue(
                current.getYaw(), yawVelocity, end.getYaw(), effectiveRotationHalflife, dt, true
        );
        SpringValue pitchSpring = springValue(
                current.getPitch(), pitchVelocity, end.getPitch(), effectiveRotationHalflife, dt, false
        );
        SpringValue fovSpring = springValue(
                current.getFovMultiplier(), fovVelocity, 1.0f, effectiveFovHalflife, dt, false
        );
        yawVelocity = yawSpring.velocity();
        pitchVelocity = pitchSpring.velocity();
        fovVelocity = fovSpring.velocity();

        current = new CameraTarget(
                desiredPos, yawSpring.value(), pitchSpring.value(), fovSpring.value()
        );

        // Drive visible FOV
        ninja.trek.camera.CameraSystem.getInstance().setFovMultiplier((float) current.getFovMultiplier());

        isComplete = isGuaranteedCloseRangeReturnComplete();
        if (isComplete) {
            // Finish on the live target pose. Requiring the camera's absolute
            // spring velocity to reach zero can never complete while the player
            // is moving, even after the camera has caught them.
            current = new CameraTarget(
                    end.getPosition(), end.getYaw(), end.getPitch(), 1.0f
            );
            positionVelocity = targetVelocity;
            yawVelocity = 0.0f;
            pitchVelocity = 0.0f;
            fovVelocity = 0.0f;
        }
        return new MovementState(current, isComplete);
    }

    private Vec3 springPosition(Vec3 position, Vec3 velocity, Vec3 target, Vec3 targetVelocity,
                                double halflife, double dt) {
        double damping = (4.0 * Math.log(2.0)) / Math.max(halflife, 0.001);
        double y = damping / 2.0;
        double decay = Math.exp(-y * dt);
        Vec3 offset = position.subtract(target);
        Vec3 relativeVelocity = velocity.subtract(targetVelocity);
        Vec3 impulse = relativeVelocity.add(offset.scale(y));
        positionVelocity = relativeVelocity.subtract(impulse.scale(y * dt)).scale(decay).add(targetVelocity);
        return offset.add(impulse.scale(dt)).scale(decay).add(target);
    }

    private Vec3 calculateTargetVelocity(Vec3 targetPosition, double dt) {
        if (lastTargetPosition == null || dt <= 1.0e-6) {
            lastTargetPosition = targetPosition;
            return Vec3.ZERO;
        }

        Vec3 velocity = targetPosition.subtract(lastTargetPosition).scale(1.0 / dt);
        lastTargetPosition = targetPosition;
        double speed = velocity.length();
        if (!Double.isFinite(speed)) {
            return Vec3.ZERO;
        }
        if (speed > MAX_TRACKED_TARGET_SPEED) {
            return velocity.scale(MAX_TRACKED_TARGET_SPEED / speed);
        }
        return velocity;
    }

    private SpringValue springValue(float position, float velocity, float target,
                                    double halflife, double dt, boolean angle) {
        if (angle) {
            target = position + angleDifference(target, position);
        }
        double damping = (4.0 * Math.log(2.0)) / Math.max(halflife, 0.001);
        double y = damping / 2.0;
        double decay = Math.exp(-y * dt);
        float offset = position - target;
        float impulse = velocity + offset * (float) y;
        float newPosition = (float) ((offset + impulse * dt) * decay + target);
        float newVelocity = (float) ((velocity - impulse * y * dt) * decay);
        return new SpringValue(newPosition, newVelocity);
    }

    private static float angleDifference(float target, float current) {
        float difference = target - current;
        while (difference > 180.0f) difference -= 360.0f;
        while (difference < -180.0f) difference += 360.0f;
        return difference;
    }

    private record SpringValue(float value, float velocity) {}


    @Override
    public void queueReset(Minecraft client, Camera camera) {
        // Already a return-only movement; allow immediate completion if requested
        isComplete = true;
    }

    @Override
    public void adjustDistance(boolean increase, Minecraft client) {
        // Not applicable for this movement
    }

    @Override
    public String getName() {
        return "FreeCamReturn";
    }

    @Override
    public float getWeight() {
        return 1.0f;
    }

    @Override
    public boolean isComplete() {
        return isComplete;
    }

    @Override
    public RaycastType getRaycastType() {
        // Let manager handle collision against the end position if needed
        return RaycastType.NONE;
    }

    private CameraTarget resolveReturnTarget(Minecraft client, float tickDelta) {
        Vec3 targetPos = CameraController.controlStick.getPosition();
        float targetYaw = CameraController.controlStick.getYaw();
        float targetPitch = CameraController.controlStick.getPitch() + pitchOffset;

        if (client == null || client.player == null) {
            return new CameraTarget(targetPos, targetYaw, targetPitch, 1.0f);
        }

        Vec3 playerEye = client.player.getEyePosition(tickDelta);
        double distance = targetPos.distanceTo(playerEye);
        if (!Double.isFinite(distance) || distance > MAX_RETURN_TARGET_DISTANCE) {
            return new CameraTarget(
                    playerEye,
                    client.player.getViewYRot(tickDelta),
                    client.player.getViewXRot(tickDelta) + pitchOffset,
                    1.0f
            );
        }

        return new CameraTarget(targetPos, targetYaw, targetPitch, 1.0f);
    }
}
