package ninja.trek.cameramovements.movements;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import ninja.trek.CameraController;
import ninja.trek.Craneshot;
import ninja.trek.CraneshotClient;
import ninja.trek.cameramovements.*;
import ninja.trek.config.MovementSetting;
import ninja.trek.mixin.client.FovAccessor;

@CameraMovementType(
        name = "Linear",
        description = "Moves the camera along a line"
)
public class LinearMovement extends AbstractMovementSettings implements ICameraMovement {
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

    public CameraTarget start = new CameraTarget();
    public CameraTarget end = new CameraTarget();
    public CameraTarget current = new CameraTarget();
    private boolean resetting = false;
    private float weight = 1.0f;
    private float baseFov;

    public void start(MinecraftClient client, Camera camera) {
        // Initialize with camera's current state
        start = CameraTarget.fromCamera(camera);
        current = CameraTarget.fromCamera(camera);
        // Store base FOV and set initial FOV delta to 0
        baseFov = client.options.getFov().getValue().floatValue();
        start.setFovMultiplier(1.0f);  // Start at normal FOV
        current.setFovMultiplier(1.0f); // Start at normal FOV
        // Calculate end target based on controlStick
        Vec3d targetPos = calculateTargetPosition(CameraController.controlStick);
        end = new CameraTarget(targetPos, CameraController.controlStick.getYaw(),
                CameraController.controlStick.getPitch() + pitchOffset, 1.0f);
        resetting = false;
        weight = 1.0f;
    }


    private Vec3d calculateTargetPosition(CameraTarget stick) {
        double yaw = Math.toRadians(stick.getYaw());
        double pitch = Math.toRadians(stick.getPitch() + pitchOffset);
        // Calculate offset based on target distance
        double xOffset = Math.sin(yaw) * Math.cos(pitch) * targetDistance;
        double yOffset = Math.sin(pitch) * targetDistance;
        double zOffset = -Math.cos(yaw) * Math.cos(pitch) * targetDistance;
        return stick.getPosition().add(xOffset, yOffset, zOffset);
    }

    @Override
    public MovementState calculateState(MinecraftClient client, Camera camera) {
        if (client.player == null) return new MovementState(current, true);

        // Update start target with controlStick's current state
        start = new CameraTarget(
                CameraController.controlStick.getPosition(),
                CameraController.controlStick.getYaw(),
                CameraController.controlStick.getPitch() + pitchOffset,
                start.getFovMultiplier()
        );

        // Update end target based on controlStick and target distance
        Vec3d targetPos = calculateTargetPosition(CameraController.controlStick);
        end = new CameraTarget(targetPos, CameraController.controlStick.getYaw(),
                CameraController.controlStick.getPitch() + pitchOffset, end.getFovMultiplier());

        CameraTarget a = resetting ? end : start;
        CameraTarget b = resetting ? start : end;

        // Position interpolation with speed limit
        Vec3d desired = current.getPosition().lerp(b.getPosition(), positionEasing);
        Vec3d moveVector = desired.subtract(current.getPosition());
        double moveDistance = moveVector.length();
        if (moveDistance > 0.01) {
            double maxMove = positionSpeedLimit * (1.0/20.0); // Convert blocks/second to blocks/tick
            if (moveDistance > maxMove) {
                Vec3d limitedMove = moveVector.normalize().multiply(maxMove);
                desired = current.getPosition().add(limitedMove);
            }
        }

        // Rotation interpolation with speed limit
        float targetYaw = b.getYaw();
        float targetPitch = b.getPitch();
        float yawDiff = targetYaw - current.getYaw();
        float pitchDiff = targetPitch - current.getPitch();

        // Normalize angles to [-180, 180]
        while (yawDiff > 180) yawDiff -= 360;
        while (yawDiff < -180) yawDiff += 360;

        // Apply easing to get desired rotation speed
        float desiredYawSpeed = (float)(yawDiff * rotationEasing);
        float desiredPitchSpeed = (float)(pitchDiff * rotationEasing);

        // Apply rotation speed limit
        float maxRotation = (float)(rotationSpeedLimit * (1.0/20.0));
        if (Math.abs(desiredYawSpeed) > maxRotation) {
            desiredYawSpeed = Math.signum(desiredYawSpeed) * maxRotation;
        }
        if (Math.abs(desiredPitchSpeed) > maxRotation) {
            desiredPitchSpeed = Math.signum(desiredPitchSpeed) * maxRotation;
        }

        // FOV interpolation with speed limit
        float targetFovDelta = b.getFovMultiplier();
        float fovDiff = targetFovDelta - current.getFovMultiplier();
        float desiredFovSpeed = (float)(fovDiff * fovEasing);
        float maxFovChange = (float)(fovSpeedLimit * (1.0/20.0));

        if (Math.abs(desiredFovSpeed) > maxFovChange) {
            desiredFovSpeed = Math.signum(desiredFovSpeed) * maxFovChange;
        }

        // Apply final changes
        float newYaw = current.getYaw() + desiredYawSpeed;
        float newPitch = current.getPitch() + desiredPitchSpeed;
        float newFovDelta = current.getFovMultiplier() + desiredFovSpeed;

        // Update current target with all new values
        current = new CameraTarget(desired, newYaw, newPitch, newFovDelta);

        // Update FOV in game renderer
        if (client.gameRenderer instanceof FovAccessor) {
            ((FovAccessor) client.gameRenderer).setFovModifier(current.getFovMultiplier());
        }

        // Calculate progress for blending
        alpha = current.getPosition().distanceTo(b.getPosition()) /
                a.getPosition().distanceTo(b.getPosition());

        boolean complete = resetting && moveDistance < 0.01;
        return new MovementState(current, complete);
    }

    @Override
    public void queueReset(MinecraftClient client, Camera camera) {
        if (client.player == null) return;
        resetting = true;
        // Reset FOV delta when movement ends
        end.setFovMultiplier(0.0f);
    }

    @Override
    public void adjustDistance(boolean increase) {
        if (mouseWheel == SCROLL_WHEEL.DISTANCE) {
            double multiplier = increase ? 1.1 : 0.9;
            targetDistance = Math.max(minDistance, Math.min(maxDistance, targetDistance * multiplier));
        } else if (mouseWheel == SCROLL_WHEEL.FOV) {
            adjustFov(increase);
        }
    }
    @Override
    public void adjustFov(boolean increase) {
        if (mouseWheel != SCROLL_WHEEL.FOV) return;
        // Change multiplier by 10% each scroll
        float change = increase ? 0.2f : -0.2f;
        float newMultiplier = fovMultiplier + change;
        // Clamp between 0.1 and 3.0
        fovMultiplier = Math.max(0.1f, Math.min(3.0f, newMultiplier));

        // Update current target's FOV immediately
        current.setFovMultiplier(fovMultiplier);

        // Update end target's FOV for smooth transitions
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
        return resetting && current.getPosition().distanceTo(start.getPosition()) < 0.03;
    }

    @Override
    public boolean hasCompletedOutPhase() {
        return !resetting && alpha < .1;
    }
}