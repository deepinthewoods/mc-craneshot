package ninja.trek;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.render.Camera;
import ninja.trek.cameramovements.CameraTarget;
import ninja.trek.cameramovements.ICameraMovement;
import ninja.trek.cameramovements.MovementState;
import ninja.trek.mixin.client.CameraAccessor;
import java.util.*;

public class CameraMovementManager {
    private final List<ICameraMovement> activeMovements = new ArrayList<>();

    public void addMovement(ICameraMovement movement, MinecraftClient client, Camera camera) {
        movement.start(client, camera);
        activeMovements.add(movement);
    }

    public void update(MinecraftClient client, Camera camera) {
        if (activeMovements.isEmpty()) return;

        // Calculate states and total weight
        float totalWeight = 0;
        List<WeightedState> states = new ArrayList<>();
        Iterator<ICameraMovement> iterator = activeMovements.iterator();

        while (iterator.hasNext()) {
            ICameraMovement movement = iterator.next();
            MovementState state = movement.calculateState(client, camera);

            if (state.isComplete()) {
                iterator.remove();
            } else {
                float weight = movement.getWeight();
                states.add(new WeightedState(state.getCameraTarget(), weight));
                totalWeight += weight;
            }
        }

        // Blend states based on weights
        if (!states.isEmpty()) {
            CameraTarget blendedTarget = blendStates(states, totalWeight);
            applyCameraTarget(blendedTarget, camera);
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