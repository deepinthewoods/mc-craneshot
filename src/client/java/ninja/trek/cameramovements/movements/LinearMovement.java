package ninja.trek.cameramovements.movements;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import ninja.trek.CameraController;
import ninja.trek.cameramovements.*;
import ninja.trek.config.MovementSetting;
import ninja.trek.mixin.client.FovAccessor;
 

@CameraMovementType(
        name = "Linear",
        description = "Moves the camera in a straight line"
)
public class LinearMovement extends AbstractMovementSettings implements ICameraMovement {
    @MovementSetting(label = "Position Easing", min = 0.01, max = 1.0)
    private double positionEasing = 0.1;

    @MovementSetting(label = "Position Speed Limit", min = 0.1, max = 100.0)
    private double positionSpeedLimit = 10;

    @MovementSetting(label = "Rotation Easing", min = 0.01, max = 1.0)
    private double rotationEasing = 0.1;

    @MovementSetting(label = "Rotation Speed Limit", min = 0.1, max = 1000)
    private double rotationSpeedLimit = 500;

    @MovementSetting(label = "Target Distance", min = 1.0, max = 50.0)
    private double targetDistance = 10.0;

    @MovementSetting(label = "Min Distance", min = 1.0, max = 10.0)
    private double minDistance = 2.0;

    @MovementSetting(label = "Max Distance", min = 10.0, max = 50.0)
    private double maxDistance = 20.0;

    public CameraTarget start = new CameraTarget();
    private CameraTarget end = new CameraTarget();
    public CameraTarget current = new CameraTarget();

    private boolean resetting = false;
    private boolean distanceChanged = false;
    private float weight = 1.0f;

    // Final interpolation state to guarantee arrival
    private boolean finalInterpActive = false;
    private double finalInterpT = 0.0;
    private Vec3d finalInterpStart = null;

    // Jitter suppression state (for tiny oscillations when fully out)
    private float lastTargetYaw = 0f;
    private float lastTargetPitch = 0f;
    private float lastYawError = 0f;
    private float lastPitchError = 0f;
    private Vec3d lastPlayerEyePos = null;

    @Override
    public void start(MinecraftClient client, Camera camera) {
        start = CameraTarget.fromCamera(camera);
        current = CameraTarget.fromCamera(camera);

        Vec3d targetPos = calculateTargetPosition(CameraController.controlStick);
        end = new CameraTarget(targetPos, CameraController.controlStick.getYaw(),
                CameraController.controlStick.getPitch(), fovMultiplier);

        resetting = false;
        distanceChanged = false;
        weight = 1.0f;
        alpha = 1;

        finalInterpActive = false;
        finalInterpT = 0.0;
        finalInterpStart = null;

        // Reset jitter suppression tracking
        lastPlayerEyePos = null;
        lastTargetYaw = current.getYaw();
        lastTargetPitch = current.getPitch();
        lastYawError = 0f;
        lastPitchError = 0f;
        // No startup log; keep movement logs minimal.
    }

    private Vec3d calculateTargetPosition(CameraTarget stick) {
        double yaw = Math.toRadians(stick.getYaw());
        double pitch = Math.toRadians(stick.getPitch());
        double xOffset = Math.sin(yaw) * Math.cos(pitch) * targetDistance;
        double yOffset = Math.sin(pitch) * targetDistance;
        double zOffset = -Math.cos(yaw) * Math.cos(pitch) * targetDistance;
        return stick.getPosition().add(xOffset, yOffset, zOffset);
    }

    @Override
    public MovementState calculateState(MinecraftClient client, Camera camera, float deltaSeconds) {
        if (client.player == null) return new MovementState(current, true);

        // Update start target with controlStick's current state
        start = new CameraTarget(
                CameraController.controlStick.getPosition(),
                CameraController.controlStick.getYaw(),
                CameraController.controlStick.getPitch(),
                start.getFovMultiplier()
        );

        // Update end target based on controlStick and target distance
        if (!resetting) {
            Vec3d targetPos = calculateTargetPosition(CameraController.controlStick);
            end = new CameraTarget(targetPos, CameraController.controlStick.getYaw(),
                    CameraController.controlStick.getPitch(), end.getFovMultiplier());
        }

        CameraTarget a = resetting ? end : start;
        CameraTarget b = resetting ? start : end;

        // During return, track the player head position and rotation continuously
        if (resetting) {
            Vec3d playerPos = client.player.getEyePos();
            float playerYaw = client.player.getYaw();
            float playerPitch = client.player.getPitch();
            b = new CameraTarget(playerPos, playerYaw, playerPitch, b.getFovMultiplier());
        }

        Vec3d desiredPos;

        // Switch to fixed-time interpolation when very close to target while returning
        double remainingDistanceForFinal = current.getPosition().distanceTo(b.getPosition());
        if (resetting && !finalInterpActive && remainingDistanceForFinal <= AbstractMovementSettings.FINAL_INTERP_DISTANCE_THRESHOLD) {
            finalInterpActive = true;
            finalInterpT = 0.0;
            finalInterpStart = current.getPosition();
            // No log needed
        }

        String stepBranch;
        double deltaLength = 0.0;
        if (finalInterpActive) {
            double step = deltaSeconds / (AbstractMovementSettings.FINAL_INTERP_TIME_SECONDS);
            finalInterpT = Math.min(1.0, finalInterpT + step);
            desiredPos = finalInterpStart.lerp(b.getPosition(), finalInterpT);
            stepBranch = "finalInterp";
        } else {
            // Straight-line eased movement towards target with speed cap
            Vec3d delta = b.getPosition().subtract(current.getPosition());
            deltaLength = delta.length();
            double maxMove = positionSpeedLimit * (deltaSeconds);
            Vec3d move;
            if (deltaLength > 0) {
                move = delta.multiply(positionEasing);
                if (move.length() > maxMove) {
                    move = move.normalize().multiply(maxMove);
                }
            } else {
                move = Vec3d.ZERO;
            }
            desiredPos = current.getPosition().add(move);
            stepBranch = "eased";
        }

        // Target rotation and FOV
        float targetYaw = b.getYaw();
        float targetPitch = b.getPitch();
        float targetFovDelta = (float) b.getFovMultiplier();

        // Rotation easing
        float yawError = targetYaw - current.getYaw();
        float pitchError = targetPitch - current.getPitch();
        while (yawError > 180) yawError -= 360;
        while (yawError < -180) yawError += 360;

        float desiredYawSpeed = (float)(yawError * rotationEasing);
        float desiredPitchSpeed = (float)(pitchError * rotationEasing);

        // Jitter suppression when fully out (near target) while player moves
        if (!resetting && client.player != null) {
            Vec3d eye = client.player.getEyePos();
            double playerMove = lastPlayerEyePos == null ? 0.0 : eye.distanceTo(lastPlayerEyePos);
            final float ANGLE_EPS = 0.7f;
            final float TARGET_EPS = 0.7f;
            final double MOVE_EPS = 0.03;

            float targetYawDelta = targetYaw - lastTargetYaw;
            while (targetYawDelta > 180) targetYawDelta -= 360;
            while (targetYawDelta < -180) targetYawDelta += 360;
            float targetPitchDelta = targetPitch - lastTargetPitch;
            while (targetPitchDelta > 180) targetPitchDelta -= 360;
            while (targetPitchDelta < -180) targetPitchDelta += 360;

            boolean smallYawJitter = Math.abs(yawError) < ANGLE_EPS && Math.abs(lastYawError) < ANGLE_EPS && Math.abs(targetYawDelta) < TARGET_EPS;
            boolean smallPitchJitter = Math.abs(pitchError) < ANGLE_EPS && Math.abs(lastPitchError) < ANGLE_EPS && Math.abs(targetPitchDelta) < TARGET_EPS;
            boolean playerMoving = playerMove > MOVE_EPS;

            // Consider near-complete when remaining alpha is small
            double totalDistance = a.getPosition().distanceTo(b.getPosition());
            double remaining = current.getPosition().distanceTo(b.getPosition());
            boolean fullyOut = totalDistance > 0 && (remaining / totalDistance) <= 0.01;

            if (fullyOut && playerMoving && (smallYawJitter || (lastYawError * yawError < 0 && Math.abs(yawError) < ANGLE_EPS))) {
                desiredYawSpeed = 0f;
            }
            if (fullyOut && playerMoving && (smallPitchJitter || (lastPitchError * pitchError < 0 && Math.abs(pitchError) < ANGLE_EPS))) {
                desiredPitchSpeed = 0f;
            }

            lastPlayerEyePos = eye;
        }

        // FOV easing with adaptive smoothness
        float fovError = (float) (targetFovDelta - current.getFovMultiplier());
        float absFovError = Math.abs(fovError);
        float adaptiveFovEasing = (float) (fovEasing * (0.5 + 0.5 * (absFovError / 0.1)));
        if (adaptiveFovEasing > fovEasing) adaptiveFovEasing = (float)fovEasing;
        float desiredFovSpeed = fovError * adaptiveFovEasing;

        // Apply speed limits
        float maxRotation = (float)(rotationSpeedLimit * (deltaSeconds));
        float maxFovChange = (float)(fovSpeedLimit * (deltaSeconds));
        if (Math.abs(desiredYawSpeed) > maxRotation) desiredYawSpeed = Math.signum(desiredYawSpeed) * maxRotation;
        if (Math.abs(desiredPitchSpeed) > maxRotation) desiredPitchSpeed = Math.signum(desiredPitchSpeed) * maxRotation;
        if (Math.abs(desiredFovSpeed) > maxFovChange) desiredFovSpeed = Math.signum(desiredFovSpeed) * maxFovChange;

        float newYaw = current.getYaw() + desiredYawSpeed;
        float newPitch = current.getPitch() + desiredPitchSpeed;
        float newFovDelta = (float) (current.getFovMultiplier() + desiredFovSpeed);

        current = new CameraTarget(desiredPos, newYaw, newPitch, newFovDelta);
        // No per-frame logs

        // Update FOV visibly
        if (client.gameRenderer instanceof FovAccessor) {
            ((FovAccessor) client.gameRenderer).setFovModifier((float) current.getFovMultiplier());
        }

        // Update alpha based on distance progress
        double remaining = current.getPosition().distanceTo(b.getPosition());
        double totalDistance = a.getPosition().distanceTo(b.getPosition());
        alpha = totalDistance != 0 ? remaining / totalDistance : 0.0;

        // Track last target/errors for next frame
        lastTargetYaw = targetYaw;
        lastTargetPitch = targetPitch;
        lastYawError = yawError;
        lastPitchError = pitchError;

        boolean complete = resetting && (remaining < 0.007 || finalInterpActive && finalInterpT >= 0.9999);
        // No completion log; controller/diag will handle return phase logging
        return new MovementState(current, complete);
    }

    @Override
    public void queueReset(MinecraftClient client, Camera camera) {
        if (!resetting) {
            resetting = true;
            finalInterpActive = false;
            finalInterpT = 0.0;
            finalInterpStart = null;
            current = CameraTarget.fromCamera(camera);

            if (client.player != null) {
                float playerYaw = client.player.getYaw();
                float playerPitch = client.player.getPitch();
                Vec3d playerPos = client.player.getEyePos();

                // Target player view with normal FOV on return
                end = new CameraTarget(playerPos, playerYaw, playerPitch, 1.0f);

                // Snap start to current to ensure a clean interpolation beginning
                start = new CameraTarget(
                        current.getPosition(),
                        current.getYaw(),
                        current.getPitch(),
                        current.getFovMultiplier()
                );
            }



            // Reset FOV delta when movement ends
            end.setFovMultiplier(1.0f);
        }
    }

    @Override
    public void adjustDistance(boolean increase, MinecraftClient client) {
        if (mouseWheel == SCROLL_WHEEL.DISTANCE) {
            double multiplier = increase ? 1.1 : 0.9;
            targetDistance = Math.max(minDistance, Math.min(maxDistance, targetDistance * multiplier));
            distanceChanged = true;
        } else if (mouseWheel == SCROLL_WHEEL.FOV) {
            adjustFov(increase, client);
        }
    }

    @Override
    public void adjustFov(boolean increase, MinecraftClient client) {
        if (mouseWheel != SCROLL_WHEEL.FOV) return;
        super.adjustFov(increase, client);
        end.setFovMultiplier(fovMultiplier);
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
        if (resetting) {
            double positionDistance = current.getPosition().distanceTo(end.getPosition());
            float fovDifference = Math.abs(current.getFovMultiplier() - 1.0f);
            boolean positionComplete = positionDistance < 0.005;
            boolean fovComplete = fovDifference < 0.01f;
            return positionComplete && fovComplete;
        }
        return false;
    }

    @Override
    public boolean hasCompletedOutPhase() {
        if (resetting) return false;
        return alpha < 0.1; // close enough to end of out phase
    }
}

