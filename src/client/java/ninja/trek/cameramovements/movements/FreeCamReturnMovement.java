package ninja.trek.cameramovements.movements;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import ninja.trek.CameraController;
import ninja.trek.Craneshot;
import ninja.trek.cameramovements.*;
import ninja.trek.config.MovementSetting;
import ninja.trek.mixin.client.FovAccessor;

@CameraMovementType(
        name = "FreeCamReturn",
        description = "Return from freecam to stick target using Linear easing"
)
public class FreeCamReturnMovement extends AbstractMovementSettings implements ICameraMovement {

    // Linear-like parameters
    @MovementSetting(label = "Position Easing", min = 0.01, max = 1.0)
    private double positionEasing = 0.1;

    @MovementSetting(label = "Position Speed Limit", min = 0.1, max = 100.0)
    private double positionSpeedLimit = 10;

    @MovementSetting(label = "Rotation Easing", min = 0.01, max = 1.0)
    private double rotationEasing = 0.1;

    @MovementSetting(label = "Rotation Speed Limit", min = 0.1, max = 1000)
    private double rotationSpeedLimit = 500;

    @MovementSetting(label = "FOV Easing", min = 0.01, max = 1.0)
    private double fovEasing = 0.1;

    @MovementSetting(label = "FOV Speed Limit", min = 0.1, max = 100.0)
    private double fovSpeedLimit = 10.0;

    private CameraTarget start = new CameraTarget();
    private CameraTarget end = new CameraTarget();
    private CameraTarget current = new CameraTarget();

    private boolean finalInterpActive = false;
    private double finalInterpT = 0.0;
    private Vec3d finalInterpStart = null;

    private boolean isComplete = false;

    @Override
    public void start(MinecraftClient client, Camera camera) {
        // Start from the exact freecam state tracked by controller
        Vec3d startPos = CameraController.freeCamPosition;
        float startYaw = CameraController.freeCamYaw;
        float startPitch = CameraController.freeCamPitch;
        start = new CameraTarget(startPos, startYaw, startPitch, 1.0f);
        current = new CameraTarget(startPos, startYaw, startPitch, 1.0f);

        // Initial end target from stick controller (will be updated every frame)
        Vec3d targetPos = CameraController.controlStick.getPosition();
        float targetYaw = CameraController.controlStick.getYaw();
        float targetPitch = CameraController.controlStick.getPitch();
        end = new CameraTarget(targetPos, targetYaw, targetPitch, 1.0f);
        try {
            Craneshot.LOGGER.info(
                "FreeCamReturn.start: start(pos={}, yaw={}, pitch={}) end(pos={}, yaw={}, pitch={})",
                start.getPosition(), String.format("%.2f", start.getYaw()), String.format("%.2f", start.getPitch()),
                end.getPosition(), String.format("%.2f", end.getYaw()), String.format("%.2f", end.getPitch())
            );
        } catch (Throwable ignore) { }

        finalInterpActive = false;
        finalInterpT = 0.0;
        finalInterpStart = null;
        isComplete = false;
    }

    @Override
    public MovementState calculateState(MinecraftClient client, Camera camera, float tickDelta) {
        if (client == null || client.player == null) {
            return new MovementState(current, true);
        }

        // End follows live stick controller pose (player can move during return)
        Vec3d targetPos = CameraController.controlStick.getPosition();
        float targetYaw = CameraController.controlStick.getYaw();
        float targetPitch = CameraController.controlStick.getPitch();
        end = new CameraTarget(targetPos, targetYaw, targetPitch, 1.0f);

        // Position step with LinearMovement-like speed-capped easing
        Vec3d desiredPos;
        double remainingForFinal = current.getPosition().distanceTo(end.getPosition());
        if (!finalInterpActive && remainingForFinal <= FINAL_INTERP_DISTANCE_THRESHOLD) {
            finalInterpActive = true;
            finalInterpT = 0.0;
            finalInterpStart = current.getPosition();
            try {
                Craneshot.LOGGER.info(
                    "FreeCamReturn.finalInterp: activated at remainingDistance={} startPos={} targetPos={}",
                    String.format("%.4f", remainingForFinal), finalInterpStart, end.getPosition()
                );
            } catch (Throwable ignore) { }
        }

        if (finalInterpActive) {
            double step = tickDelta / (FINAL_INTERP_TIME_SECONDS * 20.0);
            finalInterpT = Math.min(1.0, finalInterpT + step);
            desiredPos = finalInterpStart.lerp(end.getPosition(), finalInterpT);
        } else {
            Vec3d delta = end.getPosition().subtract(current.getPosition());
            double deltaLength = delta.length();
            double maxMove = positionSpeedLimit * (tickDelta / 20.0);
            Vec3d move = deltaLength > 0 ? delta.multiply(positionEasing) : Vec3d.ZERO;
            if (move.length() > maxMove) move = move.normalize().multiply(maxMove);
            desiredPos = current.getPosition().add(move);
        }

        // Rotation step with speed limits
        float yawError = targetYaw - current.getYaw();
        while (yawError > 180) yawError -= 360;
        while (yawError < -180) yawError += 360;
        float pitchError = targetPitch - current.getPitch();
        float desiredYawSpeed = (float) (yawError * rotationEasing);
        float desiredPitchSpeed = (float) (pitchError * rotationEasing);
        float maxRot = (float) (rotationSpeedLimit * (tickDelta / 20.0));
        if (Math.abs(desiredYawSpeed) > maxRot) desiredYawSpeed = Math.signum(desiredYawSpeed) * maxRot;
        if (Math.abs(desiredPitchSpeed) > maxRot) desiredPitchSpeed = Math.signum(desiredPitchSpeed) * maxRot;
        float newYaw = current.getYaw() + desiredYawSpeed;
        float newPitch = current.getPitch() + desiredPitchSpeed;

        // FOV returns to 1.0 with easing/speed-limit
        float fovError = 1.0f - current.getFovMultiplier();
        float absFovError = Math.abs(fovError);
        float adaptiveFovEasing = (float) (fovEasing * (0.5 + 0.5 * (absFovError / 0.1)));
        if (adaptiveFovEasing > fovEasing) adaptiveFovEasing = (float) fovEasing;
        float desiredFovSpeed = fovError * adaptiveFovEasing;
        float maxFovChange = (float) (fovSpeedLimit * (1.0 / 20.0));
        if (Math.abs(desiredFovSpeed) > maxFovChange) desiredFovSpeed = Math.signum(desiredFovSpeed) * maxFovChange;
        float newFov = (float) (current.getFovMultiplier() + desiredFovSpeed);

        current = new CameraTarget(desiredPos, newYaw, newPitch, newFov);

        // Drive visible FOV
        if (client.gameRenderer instanceof FovAccessor) {
            ((FovAccessor) client.gameRenderer).setFovModifier((float) current.getFovMultiplier());
        }

        // Completion when very close (or final interp done) and FOV near 1.0
        double posRemaining = current.getPosition().distanceTo(end.getPosition());
        boolean positionComplete = posRemaining < 0.005 || (finalInterpActive && finalInterpT >= 0.9999);
        boolean fovComplete = Math.abs(current.getFovMultiplier() - 1.0f) < 0.01f;
        isComplete = positionComplete && fovComplete;
        if (isComplete) {
            try {
                Craneshot.LOGGER.info(
                    "FreeCamReturn.complete: current(pos={}, yaw={}, pitch={}) target(pos={}, yaw={}, pitch={}) posRemaining={} finalInterpT={}",
                    current.getPosition(), String.format("%.2f", current.getYaw()), String.format("%.2f", current.getPitch()),
                    end.getPosition(), String.format("%.2f", end.getYaw()), String.format("%.2f", end.getPitch()),
                    String.format("%.5f", posRemaining), String.format("%.3f", finalInterpT)
                );
            } catch (Throwable ignore) { }
        }

        return new MovementState(current, isComplete);
    }

    @Override
    public void queueReset(MinecraftClient client, Camera camera) {
        // Already a return-only movement; allow immediate completion if requested
        isComplete = true;
    }

    @Override
    public void adjustDistance(boolean increase, MinecraftClient client) {
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
}
