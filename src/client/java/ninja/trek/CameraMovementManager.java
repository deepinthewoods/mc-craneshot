package ninja.trek;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import ninja.trek.cameramovements.CameraTarget;
import ninja.trek.cameramovements.ICameraMovement;
import ninja.trek.cameramovements.AbstractMovementSettings;
import ninja.trek.cameramovements.MovementState;

import java.util.*;

/**
 * Owns the movement slots and handles ticking/updating the ICameraMovements.
 * It also manages transitions, key toggle states, and slot-related operations.
 */
public class CameraMovementManager {
    // Each slot holds a list of available camera movements.
    private List<List<ICameraMovement>> slots;
    // For each slot, track which movement is currently active.
    private List<Integer> currentTypes;
    // The index of the currently active movement slot (if any).
    private Integer activeMovementSlot;
    // Toggle states for keybindings that use toggle mode.
    private Map<Integer, Boolean> toggledStates;

    // The currently active movement instance.
    private ICameraMovement activeMovement;

    // The most-recently computed camera target from the active movement.
    private CameraTarget baseTarget;
    private boolean isOut;

    public CameraMovementManager() {
        int numSlots = 3; // Set to the desired number of slots.
        slots = new ArrayList<>();
        currentTypes = new ArrayList<>();
        toggledStates = new HashMap<>();
        for (int i = 0; i < numSlots; i++) {
            slots.add(new ArrayList<>());
            currentTypes.add(0);
        }
        activeMovementSlot = null;
        activeMovement = null;
        baseTarget = null;
    }

    //=== Slot Management Methods =================================================

    /**
     * Sets all slots from a saved configuration.
     */
    public void setAllSlots(List<List<ICameraMovement>> savedSlots) {
        this.slots = savedSlots;
        // Reset currentTypes to valid indices.
        currentTypes = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            currentTypes.add(0);
        }
    }

    /**
     * Adds a movement to the given slot.
     */
    public void addMovement(int slotIndex, ICameraMovement movement) {
        if (slotIndex >= 0 && slotIndex < slots.size()) {
            slots.get(slotIndex).add(movement);
        }
    }

    /**
     * Removes a movement from the given slot.
     */
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

    /**
     * Swaps two movements in the specified slot.
     */
    public void swapMovements(int slotIndex, int index1, int index2) {
        if (slotIndex >= 0 && slotIndex < slots.size()) {
            List<ICameraMovement> slotMovements = slots.get(slotIndex);
            if (index1 >= 0 && index1 < slotMovements.size() &&
                    index2 >= 0 && index2 < slotMovements.size()) {
                Collections.swap(slotMovements, index1, index2);
            }
        }
    }

    /**
     * Returns the number of movement slots.
     */
    public int getMovementCount() {
        return slots.size();
    }

    /**
     * Returns a copy of the available movements for the given slot.
     */
    public List<ICameraMovement> getAvailableMovementsForSlot(int slotIndex) {
        if (slotIndex >= 0 && slotIndex < slots.size()) {
            return new ArrayList<>(slots.get(slotIndex));
        }
        return new ArrayList<>();
    }

    /**
     * Returns the movement currently selected in the given slot.
     */
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

    /**
     * Cycles the current movement type in the given slot.
     * @param forward if true, cycles forward; if false, cycles backward.
     * @param wrap whether to wrap around the list.
     */
    public void cycleMovementType(boolean forward, int slotIndex, boolean wrap) {
        if (slotIndex < 0 || slotIndex >= slots.size()) return;
        List<ICameraMovement> slotMovements = slots.get(slotIndex);
        if (slotMovements.isEmpty()) return;

        int currentType = currentTypes.get(slotIndex);
        int newType;
        if (forward) {
            newType = wrap ? (currentType + 1) % slotMovements.size() : Math.min(currentType + 1, slotMovements.size() - 1);
        } else {
            newType = wrap ? (currentType - 1 + slotMovements.size()) % slotMovements.size() : Math.max(currentType - 1, 0);
        }
        currentTypes.set(slotIndex, newType);
    }

    /**
     * Convenience method: cycles the movement type of the currently active slot.
     */
    public void cycleMovementType(boolean forward) {
        if (activeMovementSlot != null) {
            cycleMovementType(forward, activeMovementSlot, false);
        }
    }

    /**
     * Adjusts the distance (or another parameter) of the movement in the given slot.
     */
    public void adjustDistance(int slotIndex, boolean increase) {
        ICameraMovement movement = getMovementAt(slotIndex);
        if (movement != null) {
            movement.adjustDistance(increase);
        }
    }

    //=== Transition and Key State Handling =======================================

    /**
     * Starts a camera movement transition from the movement in the specified slot.
     */
    public void startTransition(MinecraftClient client, Camera camera, int slotIndex) {
        ICameraMovement movement = getMovementAt(slotIndex);
        if (movement == null) return;

        // If the same slot is already active, finish the transition.
        if (activeMovementSlot != null && activeMovementSlot.equals(slotIndex)) {
            finishTransition(client, camera);
            return;
        }

        // If another movement is active, finish it first.
        if (activeMovementSlot != null && !activeMovementSlot.equals(slotIndex)) {
            toggledStates.put(activeMovementSlot, false);
            finishTransition(client, camera);
        }

        activeMovementSlot = slotIndex;
        activeMovement = movement;
        CraneshotClient.CAMERA_CONTROLLER.setPostMoveStates(null);
        movement.start(client, camera);

    }


    public void finishTransition(MinecraftClient client, Camera camera) {
        if (activeMovement != null) {
            activeMovement.queueReset(client, camera);
            // Do not clear activeMovement here; let update() continue to process the reset phase.
        }
    }


    /**
     * Handles key state changes.
     *
     * @param keyIndex The slot index tied to the key.
     * @param pressed  Whether the key was pressed.
     * @param isToggleMode  True if this key is in toggle mode.
     */
    public void handleKeyStateChange(int keyIndex, boolean pressed, MinecraftClient client, Camera camera, boolean isToggleMode) {
        if (pressed) {
            if (activeMovementSlot != null && !activeMovementSlot.equals(keyIndex)) {
                toggledStates.put(activeMovementSlot, false);
                finishTransition(client, camera);
            }
            if (isToggleMode) {
                boolean currentToggle = toggledStates.getOrDefault(keyIndex, false);
                boolean newToggle = !currentToggle;
                toggledStates.put(keyIndex, newToggle);
                if (newToggle) {
                    startTransition(client, camera, keyIndex);
                } else {
                    finishTransition(client, camera);
                }
            } else {
                startTransition(client, camera, keyIndex);
            }
        } else {
            if (!isToggleMode) {
                if (activeMovementSlot != null && activeMovementSlot.equals(keyIndex)) {
                    finishTransition(client, camera);
                }
            }
        }
    }

    //=== Updating / Ticking the Active Movement ==================================

    /**
     * Ticks the active movement and returns the computed CameraTarget.
     * If the movement is complete, it finishes the transition.
     */
    public CameraTarget update(MinecraftClient client, Camera camera) {
        if (activeMovement == null || client.player == null) {
            return null;
        }
        MovementState state = activeMovement.calculateState(client, camera);

        if (!isOut){
            isOut = activeMovement.hasCompletedOutPhase();
            if (isOut)
                CraneshotClient.CAMERA_CONTROLLER.setPostMoveStates((AbstractMovementSettings) activeMovement);
        }
        if (state.isComplete()) {
            CraneshotClient.CAMERA_CONTROLLER.onComplete();
            activeMovement = null;
            activeMovementSlot = null;  // Add this line to clear the slot
            toggledStates.put(activeMovementSlot, false);  // Reset toggle state
            CraneshotClient.CAMERA_CONTROLLER.setPostMoveStates(null);
            return null;
        }
        // Adjust the base target (e.g. using player's position and any raycasting)
        baseTarget = state.getCameraTarget().withAdjustedPosition(client.player, activeMovement.getRaycastType());
        return baseTarget;
    }



    //=== Getters =================================================================

    public Integer getActiveMovementSlot() {
        return activeMovementSlot;
    }

    public ICameraMovement getActiveMovement() {
        return activeMovement;
    }

    public List<List<ICameraMovement>> getSlots() {
        return slots;
    }
}
