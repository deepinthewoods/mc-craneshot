package ninja.trek;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import ninja.trek.camera.CameraSystem;
import ninja.trek.cameramovements.*;
import ninja.trek.cameramovements.movements.FreeCamReturnMovement;
import ninja.trek.cameramovements.movements.FollowMovement;
import ninja.trek.cameramovements.movements.BezierMovement;
import ninja.trek.cameramovements.movements.LinearMovement;
import ninja.trek.config.GeneralMenuSettings;
import ninja.trek.config.SlotMenuSettings;
import ninja.trek.mixin.client.CameraAccessor;
import ninja.trek.mixin.client.FovAccessor;

import java.util.*;

public class CameraMovementManager {
    public static final int SLOT_COUNT = 6;

    private List<List<ICameraMovement>> slots;
    private List<Integer> currentTypes;
    private Integer activeMovementSlot;
    private Map<Integer, Boolean> toggledStates;
    private ICameraMovement activeMovement;
    private CameraTarget baseTarget;
    private boolean isOut;

    // For handling free camera return
    private boolean inFreeCamReturnPhase = false;

    // Zoom overlay - applies FOV modification on top of active movement
    private ninja.trek.cameramovements.movements.ZoomMovement zoomOverlay = null;
    private boolean isZoomActive = false;


    // New fields for managing scroll selection
    private Map<Integer, Integer> scrollSelectedTypes;
    private Map<Integer, Boolean> hasScrolledDuringPress;
    private Map<Integer, Long> keyPressStartTimes;

    // Throttled logging when a movement is active
    private long lastMovementLogTimeMs = 0L;


    public CameraMovementManager() {
        slots = new ArrayList<>();
        currentTypes = new ArrayList<>();
        toggledStates = new HashMap<>();
        scrollSelectedTypes = new HashMap<>();
        hasScrolledDuringPress = new HashMap<>();
        keyPressStartTimes = new HashMap<>();

        for (int i = 0; i < SLOT_COUNT; i++) {
            slots.add(new ArrayList<>());
            currentTypes.add(0);
            scrollSelectedTypes.put(i, 0);
        }

        activeMovementSlot = null;
        activeMovement = null;
        baseTarget = null;
    }

    public void setAllSlots(List<List<ICameraMovement>> savedSlots) {
        if (savedSlots == null) {
            savedSlots = Collections.emptyList();
        }

        slots = new ArrayList<>();
        currentTypes = new ArrayList<>();
        scrollSelectedTypes = new HashMap<>();
        toggledStates.clear();
        hasScrolledDuringPress.clear();
        keyPressStartTimes.clear();

        for (int i = 0; i < SLOT_COUNT; i++) {
            List<ICameraMovement> slot = i < savedSlots.size()
                    ? new ArrayList<>(savedSlots.get(i))
                    : new ArrayList<>();
            slots.add(slot);
            currentTypes.add(0);
            scrollSelectedTypes.put(i, 0);
        }

        if (activeMovementSlot != null && activeMovementSlot >= SLOT_COUNT) {
            activeMovementSlot = null;
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

    public void cancelAllMovements(MinecraftClient client, Camera camera) {
        activeMovement = null;
        activeMovementSlot = null;
        inFreeCamReturnPhase = false;
        isOut = false;
        baseTarget = null;

        isZoomActive = false;
        zoomOverlay = null;

        toggledStates.clear();
        hasScrolledDuringPress.clear();
        keyPressStartTimes.clear();

        CraneshotClient.CAMERA_CONTROLLER.setPostMoveStates(null);
        CraneshotClient.CAMERA_CONTROLLER.onComplete();
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
            if (!resumeOutPhaseIfReturning(client, camera)) {
                finishTransition(client, camera);
            }
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

    public void startFollowMovement(MinecraftClient client, Camera camera) {
        FollowMovement follow = GeneralMenuSettings.getFollowMovement();
        if (follow == null) return;
        if (activeMovement == follow) {
            if (follow.isResetting()) {
                follow.resumeOutPhase(client, camera);
                isOut = false;
            }
            return;
        }

        // Override any current movement immediately
        if (activeMovementSlot != null) {
            toggledStates.put(activeMovementSlot, false);
        }
        activeMovementSlot = null;
        activeMovement = follow;
        inFreeCamReturnPhase = false;
        isOut = false;

        CraneshotClient.CAMERA_CONTROLLER.setPostMoveStates(null);
        follow.start(client, camera);
        CraneshotClient.CAMERA_CONTROLLER.setPreMoveStates(follow);
    }

    private boolean resumeOutPhaseIfReturning(MinecraftClient client, Camera camera) {
        if (activeMovement instanceof LinearMovement linear && linear.isResetting()) {
            linear.resumeOutPhase(client, camera);
            isOut = false;
            return true;
        }
        if (activeMovement instanceof BezierMovement bezier && bezier.isResetting()) {
            bezier.resumeOutPhase(client, camera);
            isOut = false;
            return true;
        }
        return false;
    }

    public void stopFollowMovement(MinecraftClient client, Camera camera) {
        FollowMovement follow = GeneralMenuSettings.getFollowMovement();
        if (follow == null) return;
        if (activeMovement != follow) return;
        activeMovement.queueReset(client, camera);
    }

    public void startZoomMovement(MinecraftClient client, Camera camera) {
        ninja.trek.cameramovements.movements.ZoomMovement zoom = GeneralMenuSettings.getZoomMovement();
        if (zoom == null) return;
        if (isZoomActive) return;

        // Activate zoom as an overlay (doesn't replace the active movement)
        zoomOverlay = zoom;
        isZoomActive = true;
        zoom.start(client, camera);
    }

    public void stopZoomMovement(MinecraftClient client, Camera camera) {
        if (!isZoomActive || zoomOverlay == null) return;
        zoomOverlay.queueReset(client, camera);
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
                
                // Store the original active movement to return to after FreeCamReturnMovement completes
                ICameraMovement originalMovement = activeMovement;
                Integer originalSlot = activeMovementSlot;
                
                // Capture the latest freecam transform before disabling the camera system
                // so the return movement starts exactly from the current freecam state
                try {
                    // Check CameraEntity first - when using the dedicated camera entity,
                    // it holds the actual position, not CameraSystem
                    ninja.trek.util.CameraEntity camEnt = ninja.trek.util.CameraEntity.getCamera();
                    if (camEnt != null) {
                        ninja.trek.CameraController.freeCamPosition = new Vec3d(camEnt.getX(), camEnt.getY(), camEnt.getZ());
                        ninja.trek.CameraController.freeCamYaw = camEnt.getYaw();
                        ninja.trek.CameraController.freeCamPitch = camEnt.getPitch();
                    } else {
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
                    }
                } catch (Throwable ignore) { }

                // Clear post-move settings to disable free camera mode
                CraneshotClient.CAMERA_CONTROLLER.setPostMoveStates(null);

                // Fully deactivate the camera system so return movement drives the camera
                CameraSystem.getInstance().deactivateCamera();

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
            }
            
            // Normal case - queue reset directly
            // This will trigger return to the player's head position and rotation
            // Ensure we exit free camera modes so the movement can drive the return
            if (inFreeCameraMode) {
                CraneshotClient.CAMERA_CONTROLLER.setPostMoveStates(null);
            }

            // For movements that use queueReset(), capture current freecam pose first,
            // then deactivate so subsequent frames apply movement transforms smoothly
            Vec3d prevBase = baseTarget != null ? baseTarget.getPosition() : null;
            activeMovement.queueReset(client, camera);

            // Fully deactivate camera system after seeding queueReset() from the freecam entity
            if (inFreeCameraMode) {
                CameraSystem.getInstance().deactivateCamera();
            }
            
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

    public MovementState calculateState(MinecraftClient client, Camera camera, float deltaSeconds) {
        if (client.player == null) {
            return null;
        }

        // Handle zoom overlay deactivation
        if (isZoomActive && zoomOverlay != null && zoomOverlay.isComplete()) {
            isZoomActive = false;
            zoomOverlay = null;
        }

        if (activeMovement == null) {
            return null;
        }

        // Check if we're in FreeCamReturn phase
        if (inFreeCamReturnPhase) {
            FreeCamReturnMovement freeCamReturnMovement = GeneralMenuSettings.getFreeCamReturnMovement();
            if (activeMovement == freeCamReturnMovement) {
                MovementState state = freeCamReturnMovement.calculateState(client, camera, deltaSeconds);
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
                    CameraSystem cameraSystem = CameraSystem.getInstance();
                    cameraSystem.deactivateCamera();

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
        MovementState state = activeMovement.calculateState(client, camera, deltaSeconds);
        if (!isOut) {
            isOut = activeMovement.hasCompletedOutPhase();
            if (isOut) {
                // Get the current target from movement and apply collision
                CameraTarget preAdjust = state.getCameraTarget();
                CameraTarget currentTarget = preAdjust.withAdjustedPosition(client.player, activeMovement.getRaycastType());

                // logging removed

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

        // Apply collision adjustment to the movement target
        CameraTarget rawTarget = state.getCameraTarget();
        baseTarget = rawTarget.withAdjustedPosition(client.player, activeMovement.getRaycastType());

        // Apply zoom overlay if active (modifies FOV only)
        if (isZoomActive && zoomOverlay != null) {
            MovementState zoomState = zoomOverlay.calculateState(client, camera, deltaSeconds);
            if (zoomState != null) {
                float zoomFov = zoomState.getCameraTarget().getFovMultiplier();
                // Create new target with zoom FOV but preserve base movement's position/rotation
                CameraTarget zoomedTarget = new CameraTarget(
                    baseTarget.getPosition(),
                    baseTarget.getYaw(),
                    baseTarget.getPitch(),
                    zoomFov
                );
                baseTarget = zoomedTarget;

                // Also update the state to return
                state = new MovementState(zoomedTarget, state.isComplete());
            }
        }

        return state;
    }

    public CameraTarget update(MinecraftClient client, Camera camera, float deltaSeconds) {
        // If no active movement and allowed, start default idle movement (Linear)
        if ((activeMovement == null) && client.player != null) {
            if (ninja.trek.config.GeneralMenuSettings.isUseDefaultIdleMovement()) {
                ICameraMovement idle = ninja.trek.config.GeneralMenuSettings.getDefaultIdleMovement();
                // Reset any post-move states before starting a fresh movement
                CraneshotClient.CAMERA_CONTROLLER.setPostMoveStates(null);
                idle.start(client, camera);
                CraneshotClient.CAMERA_CONTROLLER.setPreMoveStates((AbstractMovementSettings) idle);
                activeMovement = idle;
                activeMovementSlot = null;
                isOut = false;
            } else {
                // When no active movement and feature disabled, release control
                return null;
            }
        }

        if (client.player == null) {
            return null;
        }
        
        MovementState state = calculateState(client, camera, deltaSeconds);
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
        final double JUMP_THRESH = 1.0; // blocks
        if (baseTarget != null) {
            double rawJump = rawTarget.getPosition().distanceTo(baseTarget.getPosition());
            double adjJump = adjustedTarget.getPosition().distanceTo(baseTarget.getPosition());
            if (rawJump > JUMP_THRESH || adjJump > JUMP_THRESH) {
                // logging removed
            }
        }

        // Always update perspective based on distance threshold
        // Use render camera position when custom camera is active to avoid stale targets.
        CameraSystem cs = CameraSystem.getInstance();
        Vec3d perspectivePos = adjustedTarget.getPosition();
        if (cs.isCameraActive()) {
            Camera renderCamera = client.gameRenderer.getCamera();
            if (renderCamera != null) {
                perspectivePos = renderCamera.getPos();
            } else {
                perspectivePos = cs.getCameraPosition();
            }
        }
        double dist = perspectivePos.distanceTo(client.player.getEyePos());
        if (dist >= CameraSystem.PLAYER_RENDER_THRESHOLD) {
            client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
//                cs.activateCamera(CameraSystem.CameraMode.THIRD_PERSON);
        } else {
            client.options.setPerspective(Perspective.FIRST_PERSON);

//                cs.activateCamera(CameraSystem.CameraMode.FIRST_PERSON);
        }

        // Remove per-frame status logging to reduce noise; rely on targeted Diag logs.

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

    /**
     * Check if movement is in out phase, fully out, or in phase (any POST_MOVE state)
     * Used to determine if node influence should be skipped
     */
    public boolean isMovementInOutOrFullyOut() {
        if (activeMovement == null) return false;
        // If isOut is true, we're in the "fully out" or "in" phase
        // If isOut is false but activeMovement exists, we're in the "out" phase
        return true; // Any active movement means we're in some phase
    }

    /**
     * Sets the active movement for node-triggered return (bypassing normal slot logic)
     */
    public void setActiveMovementForNodeReturn(ICameraMovement movement) {
        this.activeMovement = movement;
        this.activeMovementSlot = null; // Not associated with any slot
        this.inFreeCamReturnPhase = true;
    }

    public AbstractMovementSettings.SCROLL_WHEEL getActiveMouseWheelMode() {
        // Zoom overlay takes priority for scroll wheel
        if (isZoomActive && zoomOverlay != null) {
            return zoomOverlay.mouseWheel;
        }

        if (activeMovement != null && activeMovement instanceof AbstractMovementSettings) {
            return ((AbstractMovementSettings) activeMovement).mouseWheel;
        }
        return AbstractMovementSettings.SCROLL_WHEEL.NONE;
    }

    /**
     * Gets the active zoom overlay if zoom is currently active.
     * @return The zoom movement overlay, or null if zoom is not active.
     */
    public ninja.trek.cameramovements.movements.ZoomMovement getActiveZoomOverlay() {
        return isZoomActive ? zoomOverlay : null;
    }
    
    /**
     * Checks if the current movement is in a return/reset phase.
     * @return true if movement is returning to player, false otherwise
     */
    private boolean isMovementReturning() {
        if (inFreeCamReturnPhase) return true;
        if (activeMovement == null) return false;

        // Check if movement is FreeCamReturnMovement
        if (activeMovement.getClass().getSimpleName().equals("FreeCamReturnMovement")) {
            return true;
        }

        // Use reflection to check for isResetting() method
        try {
            java.lang.reflect.Method method = activeMovement.getClass().getMethod("isResetting");
            Object result = method.invoke(activeMovement);
            if (result instanceof Boolean) {
                return (Boolean) result;
            }
        } catch (Exception e) {
            // Movement doesn't have isResetting() method
        }

        return false;
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
