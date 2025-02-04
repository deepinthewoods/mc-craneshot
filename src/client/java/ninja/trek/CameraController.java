package ninja.trek;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import ninja.trek.cameramovements.*;
public class CameraController {
    private final ICameraMovement[][] movements;
    private int currentMovement = -1;
    private final int[] currentTypes;
    public CameraController() {
        movements = new ICameraMovement[][] {
                {new LinearMovement(),},
                {new LinearMovement()},
                {new LinearMovement()}
        };
        currentTypes = new int[] {0, 0, 0};
    }
    public void startTransition(MinecraftClient client, Camera camera, int movementIndex) {
        if (currentMovement != -1) {
            movements[currentMovement][currentTypes[currentMovement]].reset(client, camera);
        }
        currentMovement = movementIndex;
        movements[movementIndex][currentTypes[movementIndex]].startTransition(client, camera);
    }
    public void updateTransition(MinecraftClient client, Camera camera) {
        if (currentMovement != -1) {
            ICameraMovement movement = movements[currentMovement][currentTypes[currentMovement]];
            if (movement.updateTransition(client, camera))
                currentMovement = -1;


        }
    }

    public void reset(MinecraftClient client, Camera camera) {
        if (currentMovement != -1) {
            movements[currentMovement][currentTypes[currentMovement]].reset(client, camera);
            // Remove currentMovement = -1 so we allow updateTransition(...) to keep animating
        }
    }

    public void cycleMovementType(boolean forward) {
        if (currentMovement != -1) {
            int currentType = currentTypes[currentMovement];
            currentTypes[currentMovement] = forward ?
                    (currentType + 1) % movements[currentMovement].length :
                    (currentType - 1 + movements[currentMovement].length) % movements[currentMovement].length;
        }
    }
    public int getCurrentType(int index) {
        if (index >= 0 && index < currentTypes.length) {
            return currentTypes[index];
        }
        return 0;
    }
    public void adjustDistance(int index, boolean increase) {
        if (index >= 0 && index < movements.length) {
            movements[index][currentTypes[index]].adjustDistance(increase);
        }
    }
}