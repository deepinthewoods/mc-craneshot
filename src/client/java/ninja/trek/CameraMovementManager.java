package ninja.trek;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import ninja.trek.camera.CameraSystem;
import ninja.trek.cameramovements.*;
import ninja.trek.cameramovements.movements.FreeCamReturnMovement;
import ninja.trek.config.GeneralMenuSettings;
import ninja.trek.config.SlotMenuSettings;
import ninja.trek.mixin.client.CameraAccessor;
import ninja.trek.mixin.client.FovAccessor;

import java.util.*;

public class CameraMovementManager {
    private List<List<ICameraMovement>> slots;
    private List<Integer> currentTypes;
    private Integer activeMovementSlot;
    private Map<Integer, Boolean> toggledStates;
    private ICameraMovement activeMovement;
    private CameraTarget baseTarget;
    private boolean isOut;
    
    // For handling free camera return
    private boolean inFreeCamReturnPhase = false;

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

        if (slotIndex < 0 || slotIndex >= slots.size()) return;
        Craneshot.LOGGER.info("scroll up:{}", scrollUp);

        List<ICameraMovement> slotMovements = slots.get(slotIndex);
        if (slotMovements.isEmpty()) return;

        boolean wrap = SlotMenuSettings.getWrapState(slotIndex);
        int currentType = currentTypes.get(slotIndex);

        // Calculate new movement type index
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

        // Update current type immediately
        currentTypes.set(slotIndex, newType);
        scrollSelectedTypes.put(slotIndex, newType);
        hasScrolledDuringPress.put(slotIndex, true);
        // Show movement name and toast
        ICameraMovement selectedMovement = slotMovements.get(newType);
        if (selectedMovement != null) {
            CraneshotClient.CAMERA_CONTROLLER.showMessage(
                    "Camera " + (slotIndex + 1) + ": " + selectedMovement.getName()
            );
            MovementToastRenderer.showToast(slotIndex);
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

        // Show the toast when starting a new movement
        MovementToastRenderer.showToast(slotIndex);
    }

    public void finishTransition(MinecraftClient client, Camera camera) {
        if (activeMovement != null) {
            // Check if we're in free camera mode before initiating return
            boolean inFreeCameraMode = 
                CraneshotClient.CAMERA_CONTROLLER.currentKeyMoveMode == AbstractMovementSettings.POST_MOVE_KEYS.MOVE_CAMERA_FLAT ||
                CraneshotClient.CAMERA_CONTROLLER.currentKeyMoveMode == AbstractMovementSettings.POST_MOVE_KEYS.MOVE_CAMERA_FREE ||
                CraneshotClient.CAMERA_CONTROLLER.currentMouseMoveMode == AbstractMovementSettings.POST_MOVE_MOUSE.ROTATE_CAMERA;

            // Only use FreeCamReturnMovement if we've actually moved with keyboard
            if (inFreeCameraMode && CraneshotClient.CAMERA_CONTROLLER.hasMovedWithKeyboard) {
                ninja.trek.Craneshot.LOGGER.info("Starting FreeCamReturnMovement to handle return from free camera (keyboard moved)");
                
                // Store the original active movement to return to after FreeCamReturnMovement completes
                ICameraMovement originalMovement = activeMovement;
                Integer originalSlot = activeMovementSlot;
                
                // Clear post-move settings to disable free camera mode
                CraneshotClient.CAMERA_CONTROLLER.setPostMoveStates(null);
                
                // Start the FreeCamReturnMovement to handle the transition back to normal camera
                FreeCamReturnMovement freeCamReturnMovement = GeneralMenuSettings.getFreeCamReturnMovement();
                
                // Set the FreeCamReturnMovement's endTarget to match the original movement
                if (originalMovement instanceof AbstractMovementSettings) {
                    AbstractMovementSettings originalSettings = (AbstractMovementSettings) originalMovement;
                    freeCamReturnMovement.endTarget = originalSettings.getEndTarget();
                }
                
                // Check if we have a current target and preserve its orthographic state
                if (baseTarget != null) {
                    // Get the orthographic state from the base target
                    float orthoFactor = baseTarget.getOrthoFactor();
                    boolean shouldBeOrtho = orthoFactor > 0.5f;
                    
                    ninja.trek.Craneshot.LOGGER.info("Preserving ortho factor {} during FreeCamReturn", 
                                                     orthoFactor);
                    
                    // Set the orthographic state for the return movement
                    freeCamReturnMovement.setForcedOrthoState(shouldBeOrtho);
                }
                
                freeCamReturnMovement.start(client, camera);
                
                // Set the FreeCamReturnMovement as the active movement
                activeMovement = freeCamReturnMovement;
                inFreeCamReturnPhase = true;
                
                // Reset the keyboard movement flag
                CraneshotClient.CAMERA_CONTROLLER.hasMovedWithKeyboard = false;
                
                // We'll keep track of the original movement to queue its reset after FreeCamReturnMovement completes
                return;
            } else if (inFreeCameraMode) {
                // If we're in free camera mode but haven't moved with keyboard, log this information
                ninja.trek.Craneshot.LOGGER.info("In free camera mode but no keyboard movement detected - using regular camera return");
                
                // Log the current camera modes for debugging
                AbstractMovementSettings.POST_MOVE_KEYS keyMode = CraneshotClient.CAMERA_CONTROLLER.currentKeyMoveMode;
                AbstractMovementSettings.POST_MOVE_MOUSE mouseMode = CraneshotClient.CAMERA_CONTROLLER.currentMouseMoveMode;
                ninja.trek.Craneshot.LOGGER.info("Current modes - Key: {}, Mouse: {}", keyMode, mouseMode);
            }
            
            // Normal case - queue reset directly
            // This will trigger return to the player's head position and rotation
            activeMovement.queueReset(client, camera);
            
            // Reset the keyboard movement flag
            CraneshotClient.CAMERA_CONTROLLER.hasMovedWithKeyboard = false;
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
        
        // Check if we're in FreeCamReturn phase
        if (inFreeCamReturnPhase) {
            FreeCamReturnMovement freeCamReturnMovement = GeneralMenuSettings.getFreeCamReturnMovement();
            if (activeMovement == freeCamReturnMovement) {
                MovementState state = freeCamReturnMovement.calculateState(client, camera);
                baseTarget = state.getCameraTarget().withAdjustedPosition(client.player, activeMovement.getRaycastType());
                
                // Check if FreeCamReturnMovement has completed
                if (state.isComplete() || freeCamReturnMovement.isComplete()) {
                    ninja.trek.Craneshot.LOGGER.info("FreeCamReturnMovement completed, returning to normal camera");
                    
                    // Get the final ortho factor from the state
                    float finalOrthoFactor = state.getCameraTarget().getOrthoFactor();
                    ninja.trek.Craneshot.LOGGER.info("Final ortho factor: {}", finalOrthoFactor);
                    
                    // Log the final orthographic factor for debugging
                    boolean shouldBeInOrthoMode = finalOrthoFactor > 0.5f;
                    ninja.trek.Craneshot.LOGGER.info("Final orthographic state: {}", shouldBeInOrthoMode ? "enabled" : "disabled");
                    
                    // Reset the FreeCamReturnMovement phase
                    inFreeCamReturnPhase = false;
                    
                    // Switch back to normal camera movement - normal state
                    activeMovementSlot = null;
                    activeMovement = null;
                    isOut = false;
                    
                    // Make sure to fully deactivate the camera system to restore default camera behavior
                    CameraSystem cameraSystem = CameraSystem.getInstance();
                    cameraSystem.deactivateCamera();
                    
                    // Explicitly reset camera state in controller - this prevents camera lock
                    CraneshotClient.CAMERA_CONTROLLER.freeCamPosition = client.player.getEyePos();
                    CraneshotClient.CAMERA_CONTROLLER.freeCamYaw = client.player.getYaw();
                    CraneshotClient.CAMERA_CONTROLLER.freeCamPitch = client.player.getPitch();
                    
                    // Clear any post-move states to ensure default behavior
                    CraneshotClient.CAMERA_CONTROLLER.setPostMoveStates(null);
                    
                    // Notify controller of completion
                    CraneshotClient.CAMERA_CONTROLLER.onComplete();
                    
                    // CRITICAL FIX: Set baseTarget to null to force default camera behavior
                    // This fixes the issue with orthographic projections persisting
                    baseTarget = null;
                    
                    // Log the reset for debugging
                    ninja.trek.Craneshot.LOGGER.info("Camera reset complete - base target nullified, ortho factor reset");
                    
                    return null;
                }
                
                return state;
            }
        }
        
        // Normal movement state calculation
        MovementState state = activeMovement.calculateState(client, camera);
        if (!isOut) {
            isOut = activeMovement.hasCompletedOutPhase();
            if (isOut) {
                // CRITICAL FIX: Get the exact current target position from the movement
                CameraTarget currentTarget = state.getCameraTarget();
                
                // Apply raycast adjustment for collision
                currentTarget = currentTarget.withAdjustedPosition(client.player, activeMovement.getRaycastType());
                
                // Set the base target for reference
                baseTarget = currentTarget;
                
                // Store the exact position in static controller variables
                CraneshotClient.CAMERA_CONTROLLER.freeCamPosition = currentTarget.getPosition();
                CraneshotClient.CAMERA_CONTROLLER.freeCamYaw = currentTarget.getYaw();
                CraneshotClient.CAMERA_CONTROLLER.freeCamPitch = currentTarget.getPitch();
                
                // Note the FOV multiplier is already being properly interpolated in the movement's
                // calculateState method, we're just capturing it here for post-move phase
                // The value in currentTarget already has the correct FOV multiplier
                ninja.trek.Craneshot.LOGGER.info("OUT PHASE - Current FOV multiplier: {}", currentTarget.getFovMultiplier());
                
                // Set the camera directly to this position before applying post-move settings
                if (camera != null) {
                    ((CameraAccessor) camera).invokesetPos(currentTarget.getPosition());
                    ((CameraAccessor) camera).invokeSetRotation(currentTarget.getYaw(), currentTarget.getPitch());
                    
                    // Log position for debugging
                    ninja.trek.Craneshot.LOGGER.info("OUT PHASE - Setting exact camera position: {} {} {}", 
                        currentTarget.getPosition().getX(), 
                        currentTarget.getPosition().getY(), 
                        currentTarget.getPosition().getZ());
                }
                
                // Apply post-move settings AFTER we've captured and set the position
                AbstractMovementSettings settings = (AbstractMovementSettings) activeMovement;
                CraneshotClient.CAMERA_CONTROLLER.setPostMoveStates(settings);
            }
        }
        if (state.isComplete()) {
            // Store final camera position before ending movement
            CameraTarget finalTarget = state.getCameraTarget().withAdjustedPosition(client.player, activeMovement.getRaycastType());
            baseTarget = finalTarget;
            
            // Clean up movement state
            Integer previousSlot = activeMovementSlot;
            ICameraMovement previousMovement = activeMovement;
            
            // Reset movement tracking variables
            activeMovement = null;
            activeMovementSlot = null;
            if (previousSlot != null) {
                toggledStates.put(previousSlot, false);
            }
            
            // Only clear post-move states if we don't have an active post-movement setting
            if (previousMovement instanceof AbstractMovementSettings) {
                AbstractMovementSettings settings = (AbstractMovementSettings) previousMovement;
                boolean hasPostSettings = settings.getPostMoveMouse() != AbstractMovementSettings.POST_MOVE_MOUSE.NONE || 
                                          settings.getPostMoveKeys() != AbstractMovementSettings.POST_MOVE_KEYS.NONE;
                                          
                if (!hasPostSettings) {
                    CraneshotClient.CAMERA_CONTROLLER.setPostMoveStates(null);
                }
            } else {
                CraneshotClient.CAMERA_CONTROLLER.setPostMoveStates(null);
            }
            
            // Notify controller of completion, but maintain final camera position
            CraneshotClient.CAMERA_CONTROLLER.onComplete();
            
            // Return the final state to ensure one last smooth frame
            return new MovementState(finalTarget, true);
        }

        baseTarget = state.getCameraTarget().withAdjustedPosition(client.player, activeMovement.getRaycastType());
        return state;
    }

    public CameraTarget update(MinecraftClient client, Camera camera) {
        // First check if active movement is null
        if (activeMovement == null || client.player == null) {
            return baseTarget; // Return the last known target instead of null
        }
        
        MovementState state = calculateState(client, camera);
        if (state == null) {
            // If we have no state but had a previous target, return it
            return baseTarget;
        }
        
        // Get the raycast type safely with a null check
        RaycastType raycastType = RaycastType.NONE; // Default to NONE
        if (activeMovement != null) { // Explicit null check before calling getRaycastType
            raycastType = activeMovement.getRaycastType();
        }
        
        // At this point we have a valid state and raycast type
        CameraTarget adjustedTarget = state.getCameraTarget().withAdjustedPosition(client.player, raycastType);
        ninja.trek.Craneshot.LOGGER.debug("CameraMovementManager.update - orthoFactor={}", 
                                         adjustedTarget != null ? adjustedTarget.getOrthoFactor() : "null");
        return adjustedTarget;
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

    // Add this method to CameraMovementManager class
    public int getCurrentTypeForSlot(int slotIndex) {
        if (slotIndex >= 0 && slotIndex < currentTypes.size()) {
            return currentTypes.get(slotIndex);
        }
        return 0;
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

    public boolean hasActiveMovement() {
        return activeMovement != null;
    }

    public AbstractMovementSettings.SCROLL_WHEEL getActiveMouseWheelMode() {
        if (activeMovement != null && activeMovement instanceof AbstractMovementSettings) {
            return ((AbstractMovementSettings) activeMovement).mouseWheel;
        }
        return AbstractMovementSettings.SCROLL_WHEEL.NONE;
    }
    
    /**
     * Gets the current camera target, which is used for rendering and projection calculations.
     * @return The current camera target, or null if no active movement.
     */
    public CameraTarget getCurrentTarget() {
        // Only return a target if we have an active movement
        if (activeMovement != null && baseTarget != null) {
            ninja.trek.Craneshot.LOGGER.debug("getCurrentTarget returning baseTarget with orthoFactor={}", baseTarget.getOrthoFactor());
            return baseTarget;
        } else if (baseTarget != null && activeMovement == null) {
            // If we have a baseTarget but no active movement, log this inconsistency and return null
            ninja.trek.Craneshot.LOGGER.warn("Inconsistent state: baseTarget exists but no active movement - returning null");
            // Force clear the stale base target
            baseTarget = null;
            return null;
        } else {
            ninja.trek.Craneshot.LOGGER.debug("getCurrentTarget returning null (no active movement)");
            return null;
        }
    }
    
    /**
     * Checks if orthographic mode should currently be active based on the active movement's settings
     * @return true if orthographic mode should be active, false otherwise
     */
    public boolean isOrthographicMode() {
        // First check if we have an active camera target with orthographic factor
        CameraTarget target = getCurrentTarget();
        if (target != null && target.getOrthoFactor() > 0.5f) {
            return true;
        }
        
        // If no target or ortho factor is low, check active movement settings
        if (activeMovement != null && activeMovement instanceof AbstractMovementSettings) {
            AbstractMovementSettings settings = (AbstractMovementSettings) activeMovement;
            return settings.getProjection() == AbstractMovementSettings.PROJECTION.ORTHO;
        }
        
        // Default to perspective mode if no active target or movement
        return false;
    }
    
    /**
     * Gets the current orthographic scale factor based on active movement settings
     * @return The ortho scale value, or a default value if no active settings
     */
    public float getOrthoScale() {
        // Check if we have active movement settings
        if (activeMovement != null && activeMovement instanceof AbstractMovementSettings) {
            return ((AbstractMovementSettings) activeMovement).getOrthoScale();
        }
        
        // Default scale if no active movement
        return 20.0f;
    }
}
