package ninja.trek;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import ninja.trek.cameramovements.CameraState;
import ninja.trek.cameramovements.ICameraMovement;
import ninja.trek.cameramovements.MovementState;
import ninja.trek.mixin.client.CameraAccessor;
import java.util.*;

public class CameraMovementManager {
    private final List<ICameraMovement> activeMovements = new ArrayList<>();
    private CameraState currentState;

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
                states.add(new WeightedState(state.getCameraState(), weight));
                totalWeight += weight;
            }
        }

        // Blend states based on weights
        if (!states.isEmpty()) {
            CameraState blendedState = blendStates(states, totalWeight);
            applyCameraState(blendedState, camera);
        }
    }

    private CameraState blendStates(List<WeightedState> states, float totalWeight) {
        if (states.size() == 1) {
            return states.get(0).state;
        }

        Vec3d blendedPos = Vec3d.ZERO;
        float blendedYaw = 0;
        float blendedPitch = 0;

        for (WeightedState weighted : states) {
            float normalizedWeight = weighted.weight / totalWeight;
            CameraState state = weighted.state;

            blendedPos = blendedPos.add(
                    state.getPosition().multiply(normalizedWeight)
            );
            blendedYaw += state.getYaw() * normalizedWeight;
            blendedPitch += state.getPitch() * normalizedWeight;
        }

        return new CameraState(blendedPos, blendedYaw, blendedPitch);
    }

    private void applyCameraState(CameraState state, Camera camera) {
        CameraAccessor accessor = (CameraAccessor) camera;
        accessor.invokesetPos(state.getPosition());
        accessor.invokeSetRotation(state.getYaw(), state.getPitch());
    }

    private static class WeightedState {
        final CameraState state;
        final float weight;

        WeightedState(CameraState state, float weight) {
            this.state = state;
            this.weight = weight;
        }
    }
}