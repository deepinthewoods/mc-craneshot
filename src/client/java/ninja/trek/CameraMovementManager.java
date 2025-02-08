package ninja.trek;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.render.Camera;
import ninja.trek.cameramovements.*;
import ninja.trek.mixin.client.CameraAccessor;
import java.util.*;

public class CameraMovementManager {
    private final List<ICameraMovement> activeMovements = new ArrayList<>();
    private Map<ICameraMovement, CameraTarget> originalTargets = new HashMap<>();
    private Map<ICameraMovement, CameraTarget> freeMovementTargets = new HashMap<>();

    public void addMovement(ICameraMovement movement, MinecraftClient client, Camera camera) {
        movement.start(client, camera);
        activeMovements.add(movement);
        originalTargets.put(movement, CameraTarget.fromCamera(camera));
    }

    public void update(MinecraftClient client, Camera camera) {
        if (activeMovements.isEmpty() || client.player == null) return;

        float totalWeight = 0;
        List<WeightedState> states = new ArrayList<>();
        Iterator<ICameraMovement> iterator = activeMovements.iterator();

        while (iterator.hasNext()) {
            ICameraMovement movement = iterator.next();
            MovementState state = movement.calculateState(client, camera);

            if (state.isComplete()) {
                iterator.remove();
                originalTargets.remove(movement);
                freeMovementTargets.remove(movement);
            } else {
                float weight = movement.getWeight();

                CameraTarget targetToUse;
                if (movement instanceof AbstractMovementSettings &&
                        ((ICameraMovement)movement).hasCompletedOutPhase() &&
                        ((AbstractMovementSettings)movement).getPostMoveMouse() != AbstractMovementSettings.POST_MOVE_MOUSE.NONE) {

                    // Use stored free movement target if it exists, otherwise create one
                    targetToUse = freeMovementTargets.computeIfAbsent(movement,
                            k -> CameraTarget.fromCamera(camera));
                } else {
                    targetToUse = state.getCameraTarget()
                            .withAdjustedPosition(client.player, movement.getRaycastType());
                }

                states.add(new WeightedState(targetToUse, weight));
                totalWeight += weight;
            }
        }

        if (!states.isEmpty()) {
            CameraTarget blendedTarget = blendStates(states, totalWeight);
            CameraTarget finalTarget = blendedTarget
                    .withAdjustedPosition(client.player, RaycastType.NONE);
            applyCameraTarget(finalTarget, camera);

            // Store current position for active free movement cameras
            for (ICameraMovement movement : activeMovements) {
                if (movement instanceof AbstractMovementSettings &&
                        ((ICameraMovement)movement).hasCompletedOutPhase() &&
                        ((AbstractMovementSettings)movement).getPostMoveMouse() != AbstractMovementSettings.POST_MOVE_MOUSE.NONE) {
                    freeMovementTargets.put(movement, CameraTarget.fromCamera(camera));
                }
            }
        }
    }

    public void resetMovement(ICameraMovement movement) {
        // When resetting, restore the original target
        CameraTarget originalTarget = originalTargets.get(movement);
        if (originalTarget != null) {
            freeMovementTargets.put(movement, originalTarget);
        }
    }

    private CameraTarget blendStates(List<WeightedState> states, float totalWeight) {
        if (states.size() == 1) {
            return states.get(0).target;
        }

        Vec3d blendedPos = Vec3d.ZERO;
        float blendedYaw = 0;
        float blendedPitch = 0;

        for (WeightedState weighted : states) {
            float normalizedWeight = weighted.weight / totalWeight;
            CameraTarget target = weighted.target;
            blendedPos = blendedPos.add(
                    target.getPosition().multiply(normalizedWeight)
            );
            blendedYaw += target.getYaw() * normalizedWeight;
            blendedPitch += target.getPitch() * normalizedWeight;
        }

        // Use the raycast type from the highest weight movement
        WeightedState highestWeightState = states.stream()
                .max(Comparator.comparing(ws -> ws.weight))
                .orElse(states.get(0));

        return new CameraTarget(blendedPos, blendedYaw, blendedPitch);
    }

    private void applyCameraTarget(CameraTarget target, Camera camera) {
        CameraAccessor accessor = (CameraAccessor) camera;
        accessor.invokesetPos(target.getPosition());
        accessor.invokeSetRotation(target.getYaw(), target.getPitch());

    }

    private static class WeightedState {
        final CameraTarget target;
        final float weight;

        WeightedState(CameraTarget target, float weight) {
            this.target = target;
            this.weight = weight;
        }
    }
}