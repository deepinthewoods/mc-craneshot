package ninja.trek;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import ninja.trek.cameramovements.*;
import ninja.trek.config.MovementConfigManager;

public class CameraController {
    private final ICameraMovement[][] movements;
    private int currentMovement = -1;
    private final int[] currentTypes;

    public CameraController() {
        movements = new ICameraMovement[][] {
                {new LinearMovement()},
                {new LinearMovement()},
                {new LinearMovement()}
        };
        currentTypes = new int[] {0, 0, 0};

        // Load saved configurations
        for (int i = 0; i < movements.length; i++) {
            for (ICameraMovement movement : movements[i]) {
                MovementConfigManager.getInstance().loadMovementConfig(movement, i);
            }
        }
    }

    // Config access methods
    public int getMovementCount() {
        return movements.length;
    }

    public ICameraMovement getMovementAt(int index) {
        if (index >= 0 && index < movements.length) {
            return movements[index][currentTypes[index]];
        }
        return null;
    }

    public int getCurrentTypeForSlot(int slotIndex) {
        if (slotIndex >= 0 && slotIndex < currentTypes.length) {
            return currentTypes[slotIndex];
        }
        return 0;
    }

    public ICameraMovement[] getAvailableMovementsForSlot(int slotIndex) {
        if (slotIndex >= 0 && slotIndex < movements.length) {
            return movements[slotIndex];
        }
        return new ICameraMovement[0];
    }

    // Existing methods remain unchanged
    public void startTransition(MinecraftClient client, Camera camera, int movementIndex) {
        if (currentMovement != -1) {
            movements[currentMovement][currentTypes[currentMovement]].reset(client, camera);
        }
        currentMovement = movementIndex;
        movements[movementIndex][currentTypes[movementIndex]].start(client, camera);
    }

    public void updateTransition(MinecraftClient client, Camera camera) {
        if (currentMovement != -1) {
            ICameraMovement movement = movements[currentMovement][currentTypes[currentMovement]];
            if (movement.update(client, camera))
                currentMovement = -1;
        }
    }

    public void reset(MinecraftClient client, Camera camera) {
        if (currentMovement != -1) {
            movements[currentMovement][currentTypes[currentMovement]].reset(client, camera);
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

    public ICameraMovement getCurrentMovement() {
        if (currentMovement >= 0) {
            return movements[currentMovement][currentTypes[currentMovement]];
        }
        return null;
    }

    public void adjustDistance(int index, boolean increase) {
        if (index >= 0 && index < movements.length) {
            movements[index][currentTypes[index]].adjustDistance(increase);
        }
    }

    public int getCurrentMovementIndex() {
        return currentMovement;
    }
}