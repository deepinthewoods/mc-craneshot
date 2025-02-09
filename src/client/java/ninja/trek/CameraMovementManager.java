package ninja.trek;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import ninja.trek.cameramovements.CameraTarget;
import ninja.trek.cameramovements.ICameraMovement;
import ninja.trek.cameramovements.AbstractMovementSettings;
import ninja.trek.cameramovements.MovementState;
import ninja.trek.config.GeneralMenuSettings;
import ninja.trek.config.SlotMenuSettings;

import java.util.*;

public class CameraMovementManager {
    private List<List<ICameraMovement>> slots;
    private List<Integer> currentTypes;
    private Integer activeMovementSlot;
    private Map<Integer, Boolean> toggledStates;
    private ICameraMovement activeMovement;
    private CameraTarget baseTarget;
    private boolean isOut;

    // New fields for managing scroll selection
    private Map<Integer, Integer> scrollSelectedTypes;
    private Map<Integer, Boolean> hasScrolledDuringPress;
    private Map<Integer, Long> keyPressStartTimes;

    public CameraMovementManager() {
        int numSlots = 3;
        slots = new ArrayList<>();
        currentTypes = new ArrayList<>();
        toggledStates = new HashMap<>();
        scrollSelectedTypes = new HashMap<>();
        hasScrolledDuringPress = new HashMap<>();
        keyPressStartTimes = new HashMap<>();

        for (int i = 0; i < numSlots; i++) {
            slots.add(new ArrayList<>());
            currentTypes.add(0);
            scrollSelectedTypes.put(i, 0);
        }

        activeMovementSlot = null;
        activeMovement = null;
        baseTarget = null;
    }

    public void setAllSlots(List<List<ICameraMovement>> savedSlots) {
        this.slots = savedSlots;
        currentTypes = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            currentTypes.add(0);
            scrollSelectedTypes.put(i, 0);
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
                if (currentTypes.get(slotIndex) >= slotMovements.size()) {
                    currentTypes.set(slotIndex, slotMovements.size() - 1);
                }
            }
        }
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

    public int getMovementCount() {
        return slots.size();
    }

    public List<ICameraMovement> getAvailableMovementsForSlot(int slotIndex) {
        if (slotIndex >= 0 && slotIndex < slots.size()) {
            return new ArrayList<>(slots.get(slotIndex));
        }
        return new ArrayList<>();
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

    public void handleMouseScroll(int slotIndex, boolean scrollUp) {
        if (!isSlotKeyPressed(slotIndex)) return;

        List<ICameraMovement> slotMovements = slots.get(slotIndex);
        if (slotMovements.isEmpty()) return;

        boolean wrap = SlotMenuSettings.getWrapState(slotIndex);
        int currentType = scrollSelectedTypes.get(slotIndex);
        int newType;

        if (scrollUp) {
            newType = wrap ?
                    (currentType + 1) % slotMovements.size() :
                    Math.min(currentType + 1, slotMovements.size() - 1);
        } else {
            newType = wrap ?
                    (currentType - 1 + slotMovements.size()) % slotMovements.size() :
                    Math.max(currentType - 1, 0);
        }

        scrollSelectedTypes.put(slotIndex, newType);
        hasScrolledDuringPress.put(slotIndex, true);

        // Update the movement name display
        ICameraMovement selectedMovement = slotMovements.get(newType);
        if (selectedMovement != null) {
            CraneshotClient.CAMERA_CONTROLLER.showMessage(
                    "Camera " + (slotIndex + 1) + ": " + selectedMovement.getName()
            );
        }
    }

    private boolean isSlotKeyPressed(int slotIndex) {
        return keyPressStartTimes.containsKey(slotIndex);
    }

    public void startTransition(MinecraftClient client, Camera camera, int slotIndex) {
        ICameraMovement movement = getMovementAt(slotIndex);
        if (movement == null) return;

        if (activeMovementSlot != null && activeMovementSlot.equals(slotIndex)) {
            finishTransition(client, camera);
            return;
        }

        isOut = false;

        if (activeMovementSlot != null && !activeMovementSlot.equals(slotIndex)) {
            toggledStates.put(activeMovementSlot, false);
            finishTransition(client, camera);
        }

        activeMovementSlot = slotIndex;
        activeMovement = movement;
        CraneshotClient.CAMERA_CONTROLLER.setPostMoveStates(null);
        movement.start(client, camera);
        CraneshotClient.CAMERA_CONTROLLER.setPreMoveStates((AbstractMovementSettings) movement);
    }

    public void finishTransition(MinecraftClient client, Camera camera) {
        if (activeMovement != null) {
            activeMovement.queueReset(client, camera);
        }
    }

    public void handleKeyStateChange(int keyIndex, boolean pressed, MinecraftClient client, Camera camera, boolean isToggleMode) {
        if (pressed) {
            // Key press logic
            keyPressStartTimes.put(keyIndex, System.currentTimeMillis());
            hasScrolledDuringPress.put(keyIndex, false);

            // Initialize scroll selection to current type
            scrollSelectedTypes.put(keyIndex, currentTypes.get(keyIndex));

            // Show current movement name
            List<ICameraMovement> movements = slots.get(keyIndex);
            if (!movements.isEmpty()) {
                ICameraMovement movement = movements.get(scrollSelectedTypes.get(keyIndex));
                CraneshotClient.CAMERA_CONTROLLER.showMessage(
                        "Camera " + (keyIndex + 1) + ": " + movement.getName()
                );
            }
        } else {
            // Key release logic
            if (keyPressStartTimes.containsKey(keyIndex)) {
                keyPressStartTimes.remove(keyIndex);

                if (activeMovementSlot != null && !activeMovementSlot.equals(keyIndex)) {
                    toggledStates.put(activeMovementSlot, false);
                    finishTransition(client, camera);
                }

                if (!isToggleMode || (isToggleMode && !toggledStates.getOrDefault(keyIndex, false))) {
                    // Determine which movement to use
                    int selectedType = scrollSelectedTypes.get(keyIndex);
                    boolean hasScrolled = hasScrolledDuringPress.getOrDefault(keyIndex, false);

                    if (!hasScrolled && GeneralMenuSettings.isAutoAdvance()) {
                        // Auto advance to next movement if no scrolling occurred
                        List<ICameraMovement> movements = slots.get(keyIndex);
                        selectedType = (currentTypes.get(keyIndex) + 1) % movements.size();
                    }

                    // Update current type and start transition
                    currentTypes.set(keyIndex, selectedType);
                    startTransition(client, camera, keyIndex);

                    if (isToggleMode) {
                        toggledStates.put(keyIndex, true);
                    }
                } else if (isToggleMode && toggledStates.getOrDefault(keyIndex, false)) {
                    toggledStates.put(keyIndex, false);
                    finishTransition(client, camera);
                }

                // Clear scroll state
                hasScrolledDuringPress.remove(keyIndex);
            }
        }
    }

    public MovementState calculateState(MinecraftClient client, Camera camera) {
        if (activeMovement == null || client.player == null) {
            return null;
        }
        MovementState state = activeMovement.calculateState(client, camera);
        if (!isOut) {
            isOut = activeMovement.hasCompletedOutPhase();
            if (isOut)
                CraneshotClient.CAMERA_CONTROLLER.setPostMoveStates((AbstractMovementSettings) activeMovement);
        }
        if (state.isComplete()) {
            CraneshotClient.CAMERA_CONTROLLER.onComplete();
            activeMovement = null;
            activeMovementSlot = null;
            toggledStates.put(activeMovementSlot, false);
            CraneshotClient.CAMERA_CONTROLLER.setPostMoveStates(null);
            return null;
        }

        baseTarget = state.getCameraTarget().withAdjustedPosition(client.player, activeMovement.getRaycastType());
        return state;
    }

    public CameraTarget update(MinecraftClient client, Camera camera) {
        if (activeMovement == null || client.player == null) {
            return null;
        }
        MovementState state = calculateState(client, camera);
        if (state == null) return null;
        return state.getCameraTarget();
    }

    public Integer getActiveMovementSlot() {
        return activeMovementSlot;
    }

    public ICameraMovement getActiveMovement() {
        return activeMovement;
    }

    public List<List<ICameraMovement>> getSlots() {
        return slots;
    }
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////









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





}
