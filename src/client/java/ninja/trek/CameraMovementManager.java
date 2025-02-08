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

        CameraTarget targetToUse;
        if (activeMovement instanceof AbstractMovementSettings &&
                ((ICameraMovement)activeMovement).hasCompletedOutPhase() &&
                ((AbstractMovementSettings)activeMovement).getPostMoveMouse() != AbstractMovementSettings.POST_MOVE_MOUSE.NONE) {

            // Use stored free movement target if it exists, otherwise create one
            if (freeMovementTarget == null) {
                freeMovementTarget = CameraTarget.fromCamera(camera);
            }
            targetToUse = freeMovementTarget;
        } else {
            targetToUse = state.getCameraTarget()
                    .withAdjustedPosition(client.player, activeMovement.getRaycastType());
        }

        // Apply the camera target
        CameraTarget finalTarget = targetToUse
                .withAdjustedPosition(client.player, RaycastType.NONE);
        applyCameraTarget(finalTarget, camera);

        // Update free movement target if needed
        if (activeMovement instanceof AbstractMovementSettings &&
                ((ICameraMovement)activeMovement).hasCompletedOutPhase() &&
                ((AbstractMovementSettings)activeMovement).getPostMoveMouse() != AbstractMovementSettings.POST_MOVE_MOUSE.NONE) {
            freeMovementTarget = CameraTarget.fromCamera(camera);
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