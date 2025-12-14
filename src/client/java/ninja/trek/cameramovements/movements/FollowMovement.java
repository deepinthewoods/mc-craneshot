package ninja.trek.cameramovements.movements;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import ninja.trek.CameraController;
import ninja.trek.cameramovements.AbstractMovementSettings;
import ninja.trek.cameramovements.CameraTarget;
import ninja.trek.cameramovements.ICameraMovement;
import ninja.trek.cameramovements.MovementState;
import ninja.trek.config.MovementSetting;
import ninja.trek.mixin.client.FovAccessor;

public class FollowMovement extends AbstractMovementSettings implements ICameraMovement {
    @MovementSetting(label = "Follow Height", min = 0.0, max = 50.0)
    private double followHeight = 8.0;

    @MovementSetting(label = "XZ Threshold", min = 0.0, max = 50.0)
    private double xzThreshold = 2.0;

    @MovementSetting(label = "Y Threshold", min = 0.0, max = 50.0)
    private double yThreshold = 2.0;

    @MovementSetting(label = "Position Easing", min = 0.01, max = 1.0)
    private double positionEasing = 0.1;

    @MovementSetting(label = "Position Speed Limit", min = 0.1, max = 200.0)
    private double positionSpeedLimit = 10.0;

    @MovementSetting(label = "Rotation Easing", min = 0.01, max = 1.0)
    private double rotationEasing = 0.1;

    @MovementSetting(label = "Rotation Speed Limit", min = 0.1, max = 1000.0)
    private double rotationSpeedLimit = 500.0;

    private CameraTarget current = new CameraTarget();
    private float lastStickYaw = 0.0f;
    private Vec3d startPlayerPosXZ = null;
    private boolean clampArmed = false;
    private Vec3d orbitTargetXZ = null;
    private boolean resetting = false;

    private boolean finalInterpActive = false;
    private double finalInterpT = 0.0;
    private Vec3d finalInterpStart = null;

    @Override
    public void start(MinecraftClient client, Camera camera) {
        current = CameraTarget.fromCamera(camera);
        lastStickYaw = CameraController.controlStick.getYaw();
        Vec3d stickPos = CameraController.controlStick.getPosition();
        startPlayerPosXZ = new Vec3d(stickPos.x, 0.0, stickPos.z);
        orbitTargetXZ = new Vec3d(current.getPosition().x, 0.0, current.getPosition().z);
        clampArmed = false;
        resetting = false;
        alpha = 1.0;

        finalInterpActive = false;
        finalInterpT = 0.0;
        finalInterpStart = null;
    }

    @Override
    public MovementState calculateState(MinecraftClient client, Camera camera, float deltaSeconds) {
        if (client.player == null) return new MovementState(current, true);

        Vec3d stickPos = CameraController.controlStick.getPosition();
        float stickYaw = CameraController.controlStick.getYaw();
        float stickPitch = (float) (CameraController.controlStick.getPitch() + pitchOffset);

        Vec3d desiredPos;
        float targetYaw;
        float targetPitch;
        float targetFovDelta;

        if (resetting) {
            Vec3d playerPos = client.player.getEyePos();
            targetYaw = client.player.getYaw();
            targetPitch = (float) (client.player.getPitch() + pitchOffset);
            targetFovDelta = 1.0f;

            double remainingDistanceForFinal = current.getPosition().distanceTo(playerPos);
            if (!finalInterpActive && remainingDistanceForFinal <= FINAL_INTERP_DISTANCE_THRESHOLD) {
                finalInterpActive = true;
                finalInterpT = 0.0;
                finalInterpStart = current.getPosition();
            }

            if (finalInterpActive) {
                double step = deltaSeconds / FINAL_INTERP_TIME_SECONDS;
                finalInterpT = Math.min(1.0, finalInterpT + step);
                desiredPos = finalInterpStart.lerp(playerPos, finalInterpT);
            } else {
                desiredPos = easedStep(current.getPosition(), playerPos, deltaSeconds);
            }
        } else {
            targetYaw = stickYaw;
            targetPitch = stickPitch;
            targetFovDelta = fovMultiplier;

            Vec3d cur = current.getPosition();
            Vec3d playerXZ = new Vec3d(stickPos.x, 0.0, stickPos.z);

            float deltaYaw = stickYaw - lastStickYaw;
            while (deltaYaw > 180f) deltaYaw -= 360f;
            while (deltaYaw < -180f) deltaYaw += 360f;
            lastStickYaw = stickYaw;

            if (orbitTargetXZ == null) {
                orbitTargetXZ = new Vec3d(cur.x, 0.0, cur.z);
            }
            orbitTargetXZ = rotateAroundY(playerXZ, orbitTargetXZ, deltaYaw);

            if (!clampArmed && startPlayerPosXZ != null) {
                double moved = horizontalDistanceXZ(playerXZ, startPlayerPosXZ);
                if (moved > 0.01) {
                    clampArmed = true;
                }
            }

            if (clampArmed) {
                orbitTargetXZ = clampDistanceXZ(playerXZ, orbitTargetXZ, xzThreshold);
            }
            Vec3d desiredCamXZ = orbitTargetXZ;
            double desiredY = computeFollowY(stickPos.y, cur.y, followHeight, yThreshold, client.player.isOnGround());

            Vec3d desiredRaw = new Vec3d(desiredCamXZ.x, desiredY, desiredCamXZ.z);
            desiredPos = easedStep(cur, desiredRaw, deltaSeconds);
        }

        float newYaw = easedAngle(current.getYaw(), targetYaw, deltaSeconds);
        float newPitch = easedAngle(current.getPitch(), targetPitch, deltaSeconds);
        float newFovDelta = easedFov(current.getFovMultiplier(), targetFovDelta, deltaSeconds);

        current = new CameraTarget(desiredPos, newYaw, newPitch, newFovDelta);

        if (client.gameRenderer instanceof FovAccessor) {
            ((FovAccessor) client.gameRenderer).setFovModifier(current.getFovMultiplier());
        }

        boolean complete = resetting && isComplete();
        return new MovementState(current, complete);
    }

    private Vec3d rotateAroundY(Vec3d centerXZ, Vec3d pointXZ, float deltaYawDegrees) {
        if (Math.abs(deltaYawDegrees) < 1e-6f) return pointXZ;
        double theta = Math.toRadians(deltaYawDegrees);
        double cos = Math.cos(theta);
        double sin = Math.sin(theta);

        double ox = pointXZ.x - centerXZ.x;
        double oz = pointXZ.z - centerXZ.z;

        double rx = ox * cos - oz * sin;
        double rz = ox * sin + oz * cos;

        return new Vec3d(centerXZ.x + rx, 0.0, centerXZ.z + rz);
    }

    private Vec3d clampDistanceXZ(Vec3d playerXZ, Vec3d cameraXZ, double threshold) {
        Vec3d delta = new Vec3d(cameraXZ.x - playerXZ.x, 0.0, cameraXZ.z - playerXZ.z);
        double dist = delta.length();
        if (dist <= threshold || dist <= 1e-9) return cameraXZ;
        Vec3d dir = delta.multiply(1.0 / dist);
        return new Vec3d(playerXZ.x, 0.0, playerXZ.z).add(dir.multiply(threshold));
    }

    private double horizontalDistanceXZ(Vec3d a, Vec3d b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private double computeFollowY(double centerY, double currentY, double height, double threshold, boolean onGround) {
        double targetY = centerY + height;
        if (onGround) {
            return targetY;
        }
        if (Math.abs(targetY - currentY) > threshold) {
            return targetY;
        }
        return currentY;
    }

    private Vec3d easedStep(Vec3d currentPos, Vec3d targetPos, float deltaSeconds) {
        Vec3d delta = targetPos.subtract(currentPos);
        double deltaLength = delta.length();
        double maxMove = positionSpeedLimit * deltaSeconds;
        if (deltaLength <= 1e-12) {
            return currentPos;
        }
        Vec3d move = delta.multiply(positionEasing);
        if (move.length() > maxMove) {
            move = move.normalize().multiply(maxMove);
        }
        return currentPos.add(move);
    }

    private float easedAngle(float currentAngle, float targetAngle, float deltaSeconds) {
        float err = targetAngle - currentAngle;
        while (err > 180) err -= 360;
        while (err < -180) err += 360;

        float desiredSpeed = (float) (err * rotationEasing);
        float maxRotation = (float) (rotationSpeedLimit * deltaSeconds);
        if (Math.abs(desiredSpeed) > maxRotation) desiredSpeed = Math.signum(desiredSpeed) * maxRotation;
        return currentAngle + desiredSpeed;
    }

    private float easedFov(float currentFov, float targetFov, float deltaSeconds) {
        float fovError = targetFov - currentFov;
        float absFovError = Math.abs(fovError);
        float adaptiveFovEasing = (float) (fovEasing * (0.5 + 0.5 * (absFovError / 0.1f)));
        if (adaptiveFovEasing > fovEasing) adaptiveFovEasing = (float) fovEasing;
        float desiredFovSpeed = fovError * adaptiveFovEasing;

        float maxFovChange = (float) (fovSpeedLimit * deltaSeconds);
        if (Math.abs(desiredFovSpeed) > maxFovChange) desiredFovSpeed = Math.signum(desiredFovSpeed) * maxFovChange;
        return currentFov + desiredFovSpeed;
    }

    @Override
    public void queueReset(MinecraftClient client, Camera camera) {
        if (!resetting) {
            resetting = true;
            finalInterpActive = false;
            finalInterpT = 0.0;
            finalInterpStart = null;
            current = CameraTarget.fromCamera(camera);
        }
    }

    @Override
    public void adjustDistance(boolean increase, MinecraftClient client) {
        if (mouseWheel == SCROLL_WHEEL.FOV) {
            adjustFov(increase, client);
        }
    }

    @Override
    public String getName() {
        return "Follow";
    }

    @Override
    public float getWeight() {
        return 1.0f;
    }

    @Override
    public boolean isComplete() {
        if (!resetting) return false;
        if (MinecraftClient.getInstance().player == null) return true;
        Vec3d playerPos = MinecraftClient.getInstance().player.getEyePos();
        double positionDistance = current.getPosition().distanceTo(playerPos);
        float fovDifference = Math.abs(current.getFovMultiplier() - 1.0f);
        boolean positionComplete = positionDistance < 0.005 || (finalInterpActive && finalInterpT >= 0.9999);
        boolean fovComplete = fovDifference < 0.01f;
        return positionComplete && fovComplete;
    }

    @Override
    public boolean hasCompletedOutPhase() {
        return false;
    }
}
