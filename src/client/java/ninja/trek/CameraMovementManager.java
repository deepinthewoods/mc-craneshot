package ninja.trek;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import ninja.trek.cameramovements.*;
import ninja.trek.mixin.client.CameraAccessor;

public class CameraMovementManager {
    private ICameraMovement activeMovement = null;
    private CameraTarget originalTarget = null;
    private CameraTarget freeMovementTarget = null;

    public void addMovement(ICameraMovement movement, MinecraftClient client, Camera camera) {
        // Clear any existing movement first
        if (activeMovement != null) {
            activeMovement.queueReset(client, camera);
        }

        // Start the new movement
        movement.start(client, camera);
        activeMovement = movement;
        originalTarget = CameraTarget.fromCamera(camera);
    }

    public void update(MinecraftClient client, Camera camera) {
        if (activeMovement == null || client.player == null) return;

        MovementState state = activeMovement.calculateState(client, camera);

        if (state.isComplete()) {
            activeMovement = null;
            originalTarget = null;
            freeMovementTarget = null;
            return;
        }

        // Get current post-move states
        AbstractMovementSettings.POST_MOVE_MOUSE currentMouseMode = CameraController.currentMouseMoveMode;
        AbstractMovementSettings.POST_MOVE_KEYS currentKeyMode = CameraController.currentKeyMoveMode;

        // Determine if we're in a free movement mode
        boolean isInFreeMovement = currentKeyMode == AbstractMovementSettings.POST_MOVE_KEYS.MOVE_CAMERA_FLAT ||
                currentKeyMode == AbstractMovementSettings.POST_MOVE_KEYS.MOVE_CAMERA_FREE;

        // Handle position updates
        CameraTarget targetToUse;
        if (isInFreeMovement) {
            // Use the current freeCamPosition from CameraController for position
            targetToUse = new CameraTarget(
                    CameraController.freeCamPosition,
                    camera.getYaw(),
                    camera.getPitch()
            );
        } else {
            // Use the movement's calculated position
            targetToUse = state.getCameraTarget()
                    .withAdjustedPosition(client.player, activeMovement.getRaycastType());
        }

        // Handle rotation updates
        if (currentMouseMode == AbstractMovementSettings.POST_MOVE_MOUSE.ROTATE_CAMERA) {
            // In free rotation mode, keep the calculated position but use the current camera rotation
            targetToUse = new CameraTarget(
                    targetToUse.getPosition(),
                    CameraController.freeCamYaw,
                    CameraController.freeCamPitch
            );
        }

        // Apply raycast adjustments if needed
        CameraTarget finalTarget = targetToUse.withAdjustedPosition(client.player,
                isInFreeMovement ? RaycastType.NONE : activeMovement.getRaycastType());

        // Apply the final camera target
        applyCameraTarget(finalTarget, camera);

        // Update free movement target if in post-move phase
        if (activeMovement instanceof AbstractMovementSettings &&
                ((ICameraMovement)activeMovement).hasCompletedOutPhase() &&
                currentMouseMode != AbstractMovementSettings.POST_MOVE_MOUSE.NONE) {
            freeMovementTarget = new CameraTarget(
                    finalTarget.getPosition(),
                    camera.getYaw(),
                    camera.getPitch()
            );
        }
    }

    public void resetMovement(ICameraMovement movement) {
        if (movement == activeMovement && originalTarget != null) {
            freeMovementTarget = originalTarget;
        }
    }

    private void applyCameraTarget(CameraTarget target, Camera camera) {
        CameraAccessor accessor = (CameraAccessor) camera;
        accessor.invokesetPos(target.getPosition());
        accessor.invokeSetRotation(target.getYaw(), target.getPitch());
    }
}