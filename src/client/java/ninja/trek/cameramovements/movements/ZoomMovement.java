package ninja.trek.cameramovements.movements;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import ninja.trek.cameramovements.AbstractMovementSettings;
import ninja.trek.cameramovements.CameraTarget;
import ninja.trek.cameramovements.ICameraMovement;
import ninja.trek.cameramovements.MovementState;
import ninja.trek.config.MovementSetting;
import ninja.trek.mixin.client.FovAccessor;

/**
 * Zoom movement - adjusts FOV while maintaining camera position and rotation.
 * Activated via keybind, zoom level adjusted via mouse wheel.
 */
public class ZoomMovement extends AbstractMovementSettings implements ICameraMovement {

    // Persisted zoom level (remembers zoom from last use)
    private float targetZoomFov = 0.1f; // Default: 10x zoom (0.1 = 10% of normal FOV)

    @MovementSetting(label = "FOV Easing", min = 0.01, max = 1.0)
    private double zoomFovEasing = 0.2;

    @MovementSetting(label = "FOV Speed Limit", min = 0.1, max = 100.0)
    private double zoomFovSpeedLimit = 10.0;

    private CameraTarget current = new CameraTarget();
    private boolean resetting = false;

    public ZoomMovement() {
        // Enable FOV scrolling by default
        this.mouseWheel = SCROLL_WHEEL.FOV;
    }

    @Override
    public void start(MinecraftClient client, Camera camera) {
        current = CameraTarget.fromCamera(camera);
        // Set initial FOV to target zoom level
        current = new CameraTarget(
            current.getPosition(),
            current.getYaw(),
            current.getPitch(),
            targetZoomFov
        );
        resetting = false;
    }

    @Override
    public MovementState calculateState(MinecraftClient client, Camera camera, float deltaSeconds) {
        if (client.player == null) {
            return new MovementState(current, true);
        }

        // Get player position and rotation (follow player)
        float playerYaw = client.player.getYaw();
        float playerPitch = client.player.getPitch();

        // Target FOV depends on whether we're resetting
        float targetFov = resetting ? 1.0f : targetZoomFov;

        // Direct FOV interpolation - no compensation
        float currentFov = current.getFovMultiplier();

        // Calculate desired change based on error and easing
        float fovError = targetFov - currentFov;
        float desiredSpeed = fovError * (float) zoomFovEasing;

        // Apply speed limit
        float maxSpeed = (float) (zoomFovSpeedLimit * deltaSeconds);
        if (Math.abs(desiredSpeed) > maxSpeed) {
            desiredSpeed = Math.signum(desiredSpeed) * maxSpeed;
        }

        // Apply change
        float newFov = currentFov + desiredSpeed;

        // Clamp to valid range
        newFov = Math.max(0.01f, Math.min(3.0f, newFov));

        // Update camera target with player rotation and new FOV
        current = new CameraTarget(
            client.player.getEyePos(),
            playerYaw,
            playerPitch,
            newFov
        );

        // Apply FOV to game renderer
        if (client.gameRenderer instanceof FovAccessor) {
            ((FovAccessor) client.gameRenderer).setFovModifier(current.getFovMultiplier());
        }

        boolean complete = resetting && isComplete();
        return new MovementState(current, complete);
    }

    @Override
    public void queueReset(MinecraftClient client, Camera camera) {
        if (!resetting) {
            resetting = true;
            current = CameraTarget.fromCamera(camera);
        }
    }

    @Override
    public void adjustDistance(boolean increase, MinecraftClient client) {
        // Not used for zoom - we use adjustFov instead
        // But required by ICameraMovement interface
    }

    @Override
    public void adjustFov(boolean increase, MinecraftClient client) {
        // Adjust zoom level via scroll wheel
        // increase=true means scroll up (zoom out), increase=false means scroll down (zoom in)
        float change = increase ? 0.1f : -0.1f;
        targetZoomFov += change;

        // Clamp to reasonable range (0.01 to 3.0)
        targetZoomFov = Math.max(0.01f, Math.min(3.0f, targetZoomFov));
    }

    /**
     * Gets the current target zoom FOV multiplier.
     * Used for adjusting mouse sensitivity.
     */
    public float getTargetZoomFov() {
        return targetZoomFov;
    }

    /**
     * Sets the target zoom FOV multiplier.
     * Used for persistence.
     */
    public void setTargetZoomFov(float fov) {
        this.targetZoomFov = Math.max(0.01f, Math.min(3.0f, fov));
    }

    @Override
    public String getName() {
        return "Zoom";
    }

    @Override
    public float getWeight() {
        return 1.0f;
    }

    @Override
    public boolean isComplete() {
        if (!resetting) return false;
        float fovDifference = Math.abs(current.getFovMultiplier() - 1.0f);
        return fovDifference < 0.01f;
    }

    @Override
    public boolean hasCompletedOutPhase() {
        return false;
    }
}
