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
import ninja.trek.Craneshot;

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

    // Throttled logging when a movement is active
    private long lastMovementLogTimeMs = 0L;

    public CameraMovementManager() {
        int numSlots = 10;
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
        try {
            Craneshot.LOGGER.info(
                "CameraMovementManager.startTransition: slot={} movement={} keyMode={} mouseMode={} cameraSystemActive={} camEnt={} cam(pos={}, yaw={}, pitch={})",
                slotIndex,
                movement.getName(),
                CraneshotClient.CAMERA_CONTROLLER.currentKeyMoveMode,
                CraneshotClient.CAMERA_CONTROLLER.currentMouseMoveMode,
                CameraSystem.getInstance().isCameraActive(),
                MinecraftClient.getInstance() != null ? MinecraftClient.getInstance().getCameraEntity() : null,
                camera != null ? camera.getPos() : null,
                camera != null ? String.format("%.2f", camera.getYaw()) : "-",
                camera != null ? String.format("%.2f", camera.getPitch()) : "-"
            );
        } catch (Throwable ignore) { }
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
                Craneshot.LOGGER.info(
                    "CameraMovementManager.finishTransition: entering FreeCamReturnMovement (keyboard moved) keyMode={} mouseMode={} camSysActive={} camEnt={} pos/yaw/pitch=({}, {}, {})",
                    CraneshotClient.CAMERA_CONTROLLER.currentKeyMoveMode,
                    CraneshotClient.CAMERA_CONTROLLER.currentMouseMoveMode,
                    CameraSystem.getInstance().isCameraActive(),
                    MinecraftClient.getInstance() != null ? MinecraftClient.getInstance().getCameraEntity() : null,
                    camera != null ? String.format("%.3f", camera.getPos().x) : "-",
                    camera != null ? String.format("%.2f", camera.getYaw()) : "-",
                    camera != null ? String.format("%.2f", camera.getPitch()) : "-"
                );
                
                // Store the original active movement to return to after FreeCamReturnMovement completes
                ICameraMovement originalMovement = activeMovement;
                Integer originalSlot = activeMovementSlot;
                
                // Capture the latest freecam transform before disabling the camera system
                // so the return movement starts exactly from the current freecam state
                try {
                    ninja.trek.camera.CameraSystem camSys = ninja.trek.camera.CameraSystem.getInstance();
                    if (camSys.isCameraActive()) {
                        ninja.trek.CameraController.freeCamPosition = camSys.getCameraPosition();
                        ninja.trek.CameraController.freeCamYaw = camSys.getCameraYaw();
                        ninja.trek.CameraController.freeCamPitch = camSys.getCameraPitch();
                    } else if (camera != null) {
                        ninja.trek.CameraController.freeCamPosition = camera.getPos();
                        ninja.trek.CameraController.freeCamYaw = camera.getYaw();
                        ninja.trek.CameraController.freeCamPitch = camera.getPitch();
                    }
                } catch (Throwable ignore) { }

                // Clear post-move settings to disable free camera mode
                CraneshotClient.CAMERA_CONTROLLER.setPostMoveStates(null);
                
                // Start the FreeCamReturnMovement to handle the transition back to normal camera
                FreeCamReturnMovement freeCamReturnMovement = GeneralMenuSettings.getFreeCamReturnMovement();

                
                freeCamReturnMovement.start(client, camera);
                
                // Set the FreeCamReturnMovement as the active movement
                activeMovement = freeCamReturnMovement;
                inFreeCamReturnPhase = true;
                
                // Reset the keyboard movement flag
                CraneshotClient.CAMERA_CONTROLLER.hasMovedWithKeyboard = false;
                
                // We'll keep track of the original movement to queue its reset after FreeCamReturnMovement completes
                return;
            } else if (inFreeCameraMode) {
                // No keyboard movement detected - using regular camera return
                Craneshot.LOGGER.info(
                    "CameraMovementManager.finishTransition: freecam active but no keyboard move; using normal return keyMode={} mouseMode={}",
                    CraneshotClient.CAMERA_CONTROLLER.currentKeyMoveMode,
                    CraneshotClient.CAMERA_CONTROLLER.currentMouseMoveMode
                );
            }
            
            // Normal case - queue reset directly
            // This will trigger return to the player's head position and rotation
            // Ensure we exit free camera modes so the movement can drive the return
            if (inFreeCameraMode) {
                CraneshotClient.CAMERA_CONTROLLER.setPostMoveStates(null);
            }
            try {
                Vec3d prevBase = baseTarget != null ? baseTarget.getPosition() : null;
                Craneshot.LOGGER.info(
                    "CameraMovementManager.finishTransition: queueReset on movement={} prevBaseTargetPos={} cam(pos={}, yaw={}, pitch={})",
                    activeMovement.getName(),
                    prevBase,
                    camera != null ? camera.getPos() : null,
                    camera != null ? String.format("%.2f", camera.getYaw()) : "-",
                    camera != null ? String.format("%.2f", camera.getPitch()) : "-"
                );
            } catch (Throwable ignore) { }
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

    public MovementState calculateState(MinecraftClient client, Camera camera, float tickDelta) {
        if (activeMovement == null || client.player == null) {
            return null;
        }
        
        // Check if we're in FreeCamReturn phase
        if (inFreeCamReturnPhase) {
            FreeCamReturnMovement freeCamReturnMovement = GeneralMenuSettings.getFreeCamReturnMovement();
            if (activeMovement == freeCamReturnMovement) {
                MovementState state = freeCamReturnMovement.calculateState(client, camera, tickDelta);
                baseTarget = state.getCameraTarget().withAdjustedPosition(client.player, activeMovement.getRaycastType());
                
                // Check if FreeCamReturnMovement has completed
                if (state.isComplete() || freeCamReturnMovement.isComplete()) {
                    // logging removed
                    
                    // Get the final ortho factor from the state
                    float finalOrthoFactor = state.getCameraTarget().getOrthoFactor();
                    // logging removed
                    
                    // Log the final orthographic factor for debugging
                    boolean shouldBeInOrthoMode = finalOrthoFactor > 0.5f;
                    // logging removed
                    
                    // Reset the FreeCamReturnMovement phase
                    inFreeCamReturnPhase = false;
                    
                    // Smooth finalize: provide one last frame at the final target
                    // Use the FreeCamReturnMovement's raycast type before clearing activeMovement
                    RaycastType finalRaycastType = freeCamReturnMovement.getRaycastType();
                    CameraTarget finalTarget = state.getCameraTarget().withAdjustedPosition(client.player, finalRaycastType);
                    baseTarget = finalTarget;

                    // Switch back to normal camera movement - normal state
                    activeMovementSlot = null;
                    activeMovement = null;
                    isOut = false;

                    // Restore default camera behavior
                    try {
                        CameraSystem cameraSystem = CameraSystem.getInstance();
                        Craneshot.LOGGER.info("FreeCamReturn complete: deactivating CameraSystem and restoring vanilla");
                        cameraSystem.deactivateCamera();
                    } catch (Throwable ignore) { }

                    // Explicitly reset controller state
                    CraneshotClient.CAMERA_CONTROLLER.freeCamPosition = client.player.getEyePos();
                    CraneshotClient.CAMERA_CONTROLLER.freeCamYaw = client.player.getYaw();
                    CraneshotClient.CAMERA_CONTROLLER.freeCamPitch = client.player.getPitch();
                    CraneshotClient.CAMERA_CONTROLLER.setPostMoveStates(null);
                    ninja.trek.MouseInterceptor.setIntercepting(false);
                    CraneshotClient.CAMERA_CONTROLLER.onComplete();

                    // Return a completed state so the controller applies this final frame cleanly
                    return new MovementState(finalTarget, true);
                }
                
                return state;
            }
        }
        
        // Normal movement state calculation
        MovementState state = activeMovement.calculateState(client, camera, tickDelta);
        if (!isOut) {
            isOut = activeMovement.hasCompletedOutPhase();
            if (isOut) {
                // Get the current target from movement and apply collision
                CameraTarget preAdjust = state.getCameraTarget();
                CameraTarget currentTarget = preAdjust.withAdjustedPosition(client.player, activeMovement.getRaycastType());

                // Log out-phase completion snapshot: raw vs adjusted, and post-move modes
                try {
                    ninja.trek.Craneshot.LOGGER.info(
                        "Linear out complete: raw=({},{},{}) adj=({},{},{}) yaw/pitch=({}/{}) mouseMode={} keyMode={} raycast={} camSysActive={} camEnt={}",
                        String.format("%.2f", preAdjust.getPosition().x),
                        String.format("%.2f", preAdjust.getPosition().y),
                        String.format("%.2f", preAdjust.getPosition().z),
                        String.format("%.2f", currentTarget.getPosition().x),
                        String.format("%.2f", currentTarget.getPosition().y),
                        String.format("%.2f", currentTarget.getPosition().z),
                        String.format("%.1f", currentTarget.getYaw()),
                        String.format("%.1f", currentTarget.getPitch()),
                        CraneshotClient.CAMERA_CONTROLLER.currentMouseMoveMode,
                        CraneshotClient.CAMERA_CONTROLLER.currentKeyMoveMode,
                        activeMovement.getRaycastType(),
                        CameraSystem.getInstance().isCameraActive(),
                        MinecraftClient.getInstance() != null ? MinecraftClient.getInstance().getCameraEntity() : null
                    );
                } catch (Throwable ignore) { }

                // Set the base target for reference
                baseTarget = currentTarget;

                // Store exact position for camera system
                CraneshotClient.CAMERA_CONTROLLER.freeCamPosition = currentTarget.getPosition();
                CraneshotClient.CAMERA_CONTROLLER.freeCamYaw = currentTarget.getYaw();
                CraneshotClient.CAMERA_CONTROLLER.freeCamPitch = currentTarget.getPitch();

                // Apply snap and set post-move states
                AbstractMovementSettings settings = (AbstractMovementSettings) activeMovement;
                if (camera != null) {
                    ((CameraAccessor) camera).invokesetPos(currentTarget.getPosition());
                    ((CameraAccessor) camera).invokeSetRotation(currentTarget.getYaw(), currentTarget.getPitch());
                }
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

    public CameraTarget update(MinecraftClient client, Camera camera, float tickDelta) {
        // When no active movement, stop driving the camera explicitly.
        // Returning null prevents Controller from pinning camera to a stale target.
        if (activeMovement == null || client.player == null) {
            return null;
        }
        
        MovementState state = calculateState(client, camera, tickDelta);
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
        CameraTarget rawTarget = state.getCameraTarget();
        CameraTarget adjustedTarget = rawTarget.withAdjustedPosition(client.player, raycastType);

        // Log any large target jumps (raw or adjusted)
        try {
            final double JUMP_THRESH = 1.0; // blocks
            if (baseTarget != null) {
                double rawJump = rawTarget.getPosition().distanceTo(baseTarget.getPosition());
                double adjJump = adjustedTarget.getPosition().distanceTo(baseTarget.getPosition());
                if (rawJump > JUMP_THRESH || adjJump > JUMP_THRESH) {
                    Craneshot.LOGGER.info(
                        "CameraMovementManager.update: target jump rawDist={} adjDist={} raycast={} prevBase={} newRaw={} newAdj={}",
                        String.format("%.2f", rawJump), String.format("%.2f", adjJump), raycastType,
                        baseTarget.getPosition(), rawTarget.getPosition(), adjustedTarget.getPosition()
                    );
                }
            }
        } catch (Throwable ignore) { }

        // Third-person display switching (hands/body) centralized here
        // Only when not in freecam/key-rotation modes
        boolean freeCamKeys = CameraController.currentKeyMoveMode == AbstractMovementSettings.POST_MOVE_KEYS.MOVE_CAMERA_FLAT ||
                              CameraController.currentKeyMoveMode == AbstractMovementSettings.POST_MOVE_KEYS.MOVE_CAMERA_FREE;
        boolean freeCamMouse = CameraController.currentMouseMoveMode == AbstractMovementSettings.POST_MOVE_MOUSE.ROTATE_CAMERA ||
                               CameraController.currentMouseMoveMode == AbstractMovementSettings.POST_MOVE_MOUSE.NODE_EDIT;
        if (!freeCamKeys && !freeCamMouse) {
            double dist = adjustedTarget.getPosition().distanceTo(client.player.getEyePos());
            CameraSystem cs = CameraSystem.getInstance();
            try {
                if (dist >= CameraSystem.PLAYER_RENDER_THRESHOLD) {
                    cs.activateCamera(CameraSystem.CameraMode.THIRD_PERSON);
                } else {
                    cs.activateCamera(CameraSystem.CameraMode.FIRST_PERSON);
                }
                Craneshot.LOGGER.debug("CameraMovementManager.update: ensured CameraSystem active in {} (dist={})",
                        (dist >= CameraSystem.PLAYER_RENDER_THRESHOLD ? "THIRD_PERSON" : "FIRST_PERSON"),
                        String.format("%.2f", dist));
            } catch (Throwable ignore) { }
        }

        // Throttled debug logging of current camera state and target when a movement is active
        long now = System.currentTimeMillis();
        if (true) {
            try {
                Vec3d camPos = camera != null ? camera.getPos() : Vec3d.ZERO;
                float camYaw = camera != null ? camera.getYaw() : 0f;
                float camPitch = camera != null ? camera.getPitch() : 0f;
                String movementName = activeMovement != null ? activeMovement.getName() : "<none>";

                Craneshot.LOGGER.info(
                    "active={} cam=({}, {}, {}) yaw={} pitch={} target=({}, {}, {}) tyaw={} tpitch={} raycast={} camSysActive={} keyMode={} mouseMode={}",
                    movementName,
                    String.format("%.3f", camPos.x), String.format("%.3f", camPos.y), String.format("%.3f", camPos.z),
                    String.format("%.2f", camYaw), String.format("%.2f", camPitch),
                    String.format("%.3f", adjustedTarget.getPosition().x),
                    String.format("%.3f", adjustedTarget.getPosition().y),
                    String.format("%.3f", adjustedTarget.getPosition().z),
                    String.format("%.2f", adjustedTarget.getYaw()),
                    String.format("%.2f", adjustedTarget.getPitch()),
                    raycastType,
                    CameraSystem.getInstance().isCameraActive(),
                    CameraController.currentKeyMoveMode,
                    CameraController.currentMouseMoveMode
                );
            } catch (Throwable ignore) { }
            lastMovementLogTimeMs = now;
        }

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
            return baseTarget;
        } else if (baseTarget != null && activeMovement == null) {
            // Force clear the stale base target
            try {
                Craneshot.LOGGER.debug("CameraMovementManager.getCurrentTarget: clearing stale baseTarget={} (no active movement)", baseTarget.getPosition());
            } catch (Throwable ignore) { }
            baseTarget = null;
            return null;
        } else {
            return null;
        }
    }
    
    /**
     * Checks if orthographic mode should currently be active based on the active movement's settings
     * @return true if orthographic mode should be active, false otherwise
     */
    public boolean isOrthographicMode() {
        return false;
    }
    
    /**
     * Gets the current orthographic scale factor based on active movement settings
     * @return The ortho scale value, or a default value if no active settings
     */
    public float getOrthoScale() {
        return 20.0f;
    }
}
