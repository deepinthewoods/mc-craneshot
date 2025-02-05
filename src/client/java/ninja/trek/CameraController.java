package ninja.trek;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import ninja.trek.cameramovements.*;
import ninja.trek.config.TransitionMode;
import ninja.trek.config.TransitionModeManager;
import ninja.trek.config.WrapSettings;

import java.util.*;

public class CameraController {
    private final List<List<ICameraMovement>> slots;
    private final ArrayList<Integer> currentTypes;
    private int currentMovement = -1;
    private Queue<MovementRequest> movementQueue = new LinkedList<>();

    private static class MovementRequest {
        final int movementIndex;
        final MinecraftClient client;
        final Camera camera;

        MovementRequest(int movementIndex, MinecraftClient client, Camera camera) {
            this.movementIndex = movementIndex;
            this.client = client;
            this.camera = camera;
        }
    }

    public CameraController() {
        slots = new ArrayList<>();
        currentTypes = new ArrayList<>();

        // Initialize slots with saved or default configurations
        for (int i = 0; i < 3; i++) {
            ArrayList arr = new ArrayList<ICameraMovement>();
            arr.add(new LinearMovement());
            arr.add(new LinearMovement());
            slots.add(
                   arr
                    );
            currentTypes.add(0);
        }
    }


    public void addMovement(int slotIndex, ICameraMovement movement) {
        if (slotIndex >= 0 && slotIndex < slots.size()) {
            slots.get(slotIndex).add(movement);
        }
    }

    public void removeMovement(int slotIndex, int movementIndex) {
        if (slotIndex >= 0 && slotIndex < slots.size()) {
            List<ICameraMovement> slotMovements = slots.get(slotIndex);
            if (movementIndex >= 0 && movementIndex < slotMovements.size() && slotMovements.size() > 1) {
                slotMovements.remove(movementIndex);
                // Update current type if needed
                if (currentTypes.get(slotIndex) >= slotMovements.size()) {
                    currentTypes.set(slotIndex, slotMovements.size() - 1);
                }
            }
        }
    }


    public void cycleMovementType(boolean forward) {
        if (currentMovement != -1) {
            List<ICameraMovement> slotMovements = slots.get(currentMovement);
            int currentType = currentTypes.get(currentMovement);
            boolean wrap = WrapSettings.getWrapState(currentMovement);

            if (forward) {
                if (currentType < slotMovements.size() - 1 || wrap) {
                    int newType = wrap ?
                            (currentType + 1) % slotMovements.size() :
                            Math.min(currentType + 1, slotMovements.size() - 1);
                    currentTypes.set(currentMovement, newType);
                }
            } else {
                if (currentType > 0 || wrap) {
                    int newType = wrap ?
                            (currentType - 1 + slotMovements.size()) % slotMovements.size() :
                            Math.max(currentType - 1, 0);
                    currentTypes.set(currentMovement, newType);
                }
            }
        }
    }

    // Modified existing methods to work with Lists
    public int getMovementCount() {
        return slots.size();
    }

    public ICameraMovement getMovementAt(int index) {
        if (index >= 0 && index < slots.size()) {
            List<ICameraMovement> slotMovements = slots.get(index);
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
        if (slotIndex >= 0 && slotIndex < slots.size()) {
            return new ArrayList<>(slots.get(slotIndex));
        }
        return new ArrayList<>();
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
        if (slotIndex >= 0 && slotIndex < slots.size()) {
            List<ICameraMovement> slotMovements = slots.get(slotIndex);
            if (index1 >= 0 && index1 < slotMovements.size() && index2 >= 0 && index2 < slotMovements.size()) {
                ICameraMovement temp = slotMovements.get(index1);
                slotMovements.set(index1, slotMovements.get(index2));
                slotMovements.set(index2, temp);
            }
        }
    }

    public void startTransition(MinecraftClient client, Camera camera, int movementIndex) {
        switch (TransitionModeManager.getCurrentMode()) {
            case QUEUE:
                if (currentMovement == -1) {
                    // If no movement is active, start immediately
                    startMovementImmediate(client, camera, movementIndex);
                } else {
                    // Otherwise, queue the movement
                    movementQueue.offer(new MovementRequest(movementIndex, client, camera));
                }
                break;
            case INTERPOLATE:
                // Placeholder for future interpolation implementation
                startMovementImmediate(client, camera, movementIndex);
                break;
            case IMMEDIATE:
            default:
                startMovementImmediate(client, camera, movementIndex);
                break;
        }
    }

    private void startMovementImmediate(MinecraftClient client, Camera camera, int movementIndex) {
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

    public void tick(MinecraftClient client, Camera camera) {
        if (currentMovement != -1) {
            ICameraMovement movement = getMovementAt(currentMovement);
            if (movement != null && movement.update(client, camera)) {
                // Movement is complete
                currentMovement = -1;

                // If there are queued movements and we're in queue mode, start the next one
                if (TransitionModeManager.getCurrentMode() == TransitionMode.QUEUE && !movementQueue.isEmpty()) {
                    MovementRequest next = movementQueue.poll();
                    startMovementImmediate(next.client, next.camera, next.movementIndex);
                }
            }
        }
    }

    public void queueFinish(MinecraftClient client, Camera camera) {
        if (currentMovement != -1) {
            ICameraMovement movement = getMovementAt(currentMovement);
            if (movement != null) {
                movement.reset(client, camera);
            }
        }
    }

}