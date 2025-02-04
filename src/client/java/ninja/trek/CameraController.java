package ninja.trek;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import ninja.trek.cameramovements.*;

import java.util.ArrayList;
import java.util.List;

public class CameraController {
    private final List<List<ICameraMovement>> movements;
    private final ArrayList<Integer> currentTypes;
    private int currentMovement = -1;


    public CameraController() {
        movements = new ArrayList<>();
        currentTypes = new ArrayList<>();

        // Initialize slots with saved or default configurations
        for (int i = 0; i < 3; i++) {
            ArrayList arr = new ArrayList<ICameraMovement>();
            arr.add(new LinearMovement());
            arr.add(new LinearMovement());
            movements.add(
                   arr
                    );
            currentTypes.add(0);
        }
    }




    // Modified existing methods to work with Lists
    public int getMovementCount() {
        return movements.size();
    }

    public ICameraMovement getMovementAt(int index) {
        if (index >= 0 && index < movements.size()) {
            List<ICameraMovement> slotMovements = movements.get(index);
            int currentType = currentTypes.get(index);
            if (!slotMovements.isEmpty() && currentType < slotMovements.size()) {
                return slotMovements.get(currentType);
            }
        }
        return null;
    }

    public int getCurrentTypeForSlot(int slotIndex) {
        if (slotIndex >= 0 && slotIndex < currentTypes.size()) {
            return currentTypes.get(slotIndex);
        }
        return 0;
    }

    public List<ICameraMovement> getAvailableMovementsForSlot(int slotIndex) {
        if (slotIndex >= 0 && slotIndex < movements.size()) {
            return new ArrayList<>(movements.get(slotIndex));
        }
        return new ArrayList<>();
    }

    public void startTransition(MinecraftClient client, Camera camera, int movementIndex) {
        if (currentMovement != -1) {
            ICameraMovement current = getMovementAt(currentMovement);
            if (current != null) {
                current.reset(client, camera);
            }
        }
        currentMovement = movementIndex;
        ICameraMovement newMovement = getMovementAt(movementIndex);
        if (newMovement != null) {
            newMovement.start(client, camera);
        }
    }

    public void updateTransition(MinecraftClient client, Camera camera) {
        if (currentMovement != -1) {
            ICameraMovement movement = getMovementAt(currentMovement);
            if (movement != null && movement.update(client, camera)) {
                currentMovement = -1;
            }
        }
    }

    public void reset(MinecraftClient client, Camera camera) {
        if (currentMovement != -1) {
            ICameraMovement movement = getMovementAt(currentMovement);
            if (movement != null) {
                movement.reset(client, camera);
            }
            currentMovement = -1;
        }
    }

    public void cycleMovementType(boolean forward) {
        if (currentMovement != -1) {
            List<ICameraMovement> slotMovements = movements.get(currentMovement);
            int currentType = currentTypes.get(currentMovement);
            int newType = forward ?
                    (currentType + 1) % slotMovements.size() :
                    (currentType - 1 + slotMovements.size()) % slotMovements.size();
            currentTypes.set(currentMovement, newType);
        }
    }

    public ICameraMovement getCurrentMovement() {
        if (currentMovement >= 0) {
            return getMovementAt(currentMovement);
        }
        return null;
    }

    public void adjustDistance(int index, boolean increase) {
        ICameraMovement movement = getMovementAt(index);
        if (movement != null) {
            movement.adjustDistance(increase);
        }
    }

    public int getCurrentMovementIndex() {
        return currentMovement;
    }

    public void swapMovements(int slotIndex, int index1, int index2) {
        if (slotIndex >= 0 && slotIndex < movements.size()) {
            List<ICameraMovement> slotMovements = movements.get(slotIndex);
            if (index1 >= 0 && index1 < slotMovements.size() && index2 >= 0 && index2 < slotMovements.size()) {
                ICameraMovement temp = slotMovements.get(index1);
                slotMovements.set(index1, slotMovements.get(index2));
                slotMovements.set(index2, temp);
            }
        }
    }

}