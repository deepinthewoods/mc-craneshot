package ninja.trek;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import ninja.trek.cameramovements.*;
import ninja.trek.cameramovements.movements.LinearMovement;
import ninja.trek.config.*;
import ninja.trek.mixin.client.CameraAccessor;

import java.util.*;

public class CameraController {
    private List<List<ICameraMovement>> slots;
    private final ArrayList<Integer> currentTypes;
    private CameraMovementManager movementManager;
    private final Map<Integer, ICameraMovement> activeMovementSlots;
    private static final double FIRST_PERSON_THRESHOLD = 2.0;


    public CameraController() {
        slots = new ArrayList<>();
        currentTypes = new ArrayList<>();
        movementManager = new CameraMovementManager();
        activeMovementSlots = new HashMap<>();

//         Initialize slots ?
        for (int i = 0; i < 3; i++) {
            currentTypes.add(0);
        }
    }

    public void startTransition(MinecraftClient client, Camera camera, int movementIndex) {
        Craneshot.LOGGER.info("startt");
        ICameraMovement movement = getMovementAt(movementIndex);
        if (movement == null) return;
        Craneshot.LOGGER.info("startt notnull");


        clearAllMovements(client, camera);
        activeMovementSlots.clear();
        activeMovementSlots.put(movementIndex, movement);
        movement.start(client, camera);
        movementManager.addMovement(movement, client, camera);


    }

    public void tick(MinecraftClient client, Camera camera) {
        movementManager.update(client, camera);
        updatePerspective(client, camera);
        clearCompletedMovements(client, camera);

    }

    private void clearAllMovements(MinecraftClient client, Camera camera) {
        for (ICameraMovement movement : activeMovementSlots.values()) {
            movement.queueReset(client, camera);
        }
        movementManager = new CameraMovementManager();
        activeMovementSlots.clear();
    }

    public void queueFinish(MinecraftClient client, Camera camera) {
        for (ICameraMovement movement : activeMovementSlots.values()) {
            movement.queueReset(client, camera);
        }
    }

    private void clearCompletedMovements(MinecraftClient client, Camera camera) {
        Iterator<Map.Entry<Integer, ICameraMovement>> iterator = activeMovementSlots.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, ICameraMovement> entry = iterator.next();
            if (entry.getValue().isComplete()) {
                entry.getValue().queueReset(client, camera);
                iterator.remove();
            }
        }
    }

    private void updatePerspective(MinecraftClient client, Camera camera) {
        if (client.player == null) return;
        double distance = camera.getPos().distanceTo(client.player.getEyePos());
        if (distance > FIRST_PERSON_THRESHOLD &&
                client.options.getPerspective() == Perspective.FIRST_PERSON) {
            client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        } else if (distance < FIRST_PERSON_THRESHOLD &&
                client.options.getPerspective() != Perspective.FIRST_PERSON) {
            client.options.setPerspective(Perspective.FIRST_PERSON);
        }
    }

    // Movement management methods
    public void cycleMovementType(boolean forward) {
        for (Map.Entry<Integer, ICameraMovement> entry : activeMovementSlots.entrySet()) {
            int slotIndex = entry.getKey();
            List<ICameraMovement> slotMovements = slots.get(slotIndex);
            int currentType = currentTypes.get(slotIndex);
            boolean wrap = SlotMenuSettings.getWrapState(slotIndex);
            if (forward) {
                if (currentType < slotMovements.size() - 1 || wrap) {
                    currentTypes.set(slotIndex, wrap ?
                            (currentType + 1) % slotMovements.size() :
                            Math.min(currentType + 1, slotMovements.size() - 1));
                }
            } else {
                if (currentType > 0 || wrap) {
                    currentTypes.set(slotIndex, wrap ?
                            (currentType - 1 + slotMovements.size()) % slotMovements.size() :
                            Math.max(currentType - 1, 0));
                }
            }
        }
    }

    public void adjustDistance(int index, boolean increase) {
        ICameraMovement movement = getMovementAt(index);
        if (movement != null) {
            movement.adjustDistance(increase);
        }
    }

    // Slot management methods
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
                if (currentTypes.get(slotIndex) >= slotMovements.size()) {
                    currentTypes.set(slotIndex, slotMovements.size() - 1);
                }
            }
        }
    }

    // Getter methods
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

    public List<ICameraMovement> getAvailableMovementsForSlot(int slotIndex) {
        if (slotIndex >= 0 && slotIndex < slots.size()) {
            return new ArrayList<>(slots.get(slotIndex));
        }
        return new ArrayList<>();
    }

    public int getCurrentTypeForSlot(int slotIndex) {
        if (slotIndex >= 0 && slotIndex < currentTypes.size()) {
            return currentTypes.get(slotIndex);
        }
        return 0;
    }

    public void swapMovements(int slotIndex, int index1, int index2) {
        if (slotIndex >= 0 && slotIndex < slots.size()) {
            List<ICameraMovement> slotMovements = slots.get(slotIndex);
            if (index1 >= 0 && index1 < slotMovements.size() &&
                    index2 >= 0 && index2 < slotMovements.size()) {
                Collections.swap(slotMovements, index1, index2);
            }
        }
    }

    public void setAllSlots(List<List<ICameraMovement>> savedSlots) {
        this.slots = savedSlots;
    }

    public void handleCameraUpdate(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, Camera camera) {
        tick(MinecraftClient.getInstance(), camera);

    }
}