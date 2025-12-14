package ninja.trek;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import ninja.trek.camera.CameraSystem;
import ninja.trek.cameramovements.AbstractMovementSettings;
import ninja.trek.cameramovements.AbstractMovementSettings.POST_MOVE_KEYS;
import ninja.trek.cameramovements.AbstractMovementSettings.POST_MOVE_MOUSE;
import ninja.trek.cameramovements.CameraTarget;
import ninja.trek.config.FreeCamSettings;
import ninja.trek.config.GeneralMenuSettings;
import ninja.trek.mixin.client.CameraAccessor;
import ninja.trek.mixin.client.FovAccessor;

public class CameraController {
    public static POST_MOVE_KEYS currentKeyMoveMode = POST_MOVE_KEYS.NONE;
    public static POST_MOVE_MOUSE currentMouseMoveMode = POST_MOVE_MOUSE.NONE;
    public static Vec3d freeCamPosition = Vec3d.ZERO;
    public static float freeCamYaw = 0f;
    public static float freeCamPitch = 0f;
    public static CameraTarget controlStick = new CameraTarget();

    // Track whether the camera has been moved with keyboard input
    public static boolean hasMovedWithKeyboard = false;

    // Track if camera was activated by node influence
    private static boolean cameraActivatedByNodes = false;
    private static double lastNodeInfluence = 0.0;

    private String currentMessage = "";
    private long messageTimer = 0;
    private static final long MESSAGE_DURATION = 2000;
    public static final double FIRST_PERSON_THRESHOLD_MIN = 2.0;
    public static final double FIRST_PERSON_THRESHOLD_MAX = 5.0;

    public static AbstractMovementSettings.END_TARGET currentEndTarget = AbstractMovementSettings.END_TARGET.HEAD_BACK;
    private Vec3d lastPlayerPos = Vec3d.ZERO;
    private Vec3d cumulativeMovement = Vec3d.ZERO;
    private float targetYaw = 0f;
    private static final double FULL_ROTATE_DISTANCE = 2.0; // Blocks to move for full rotation

    private Vec3d currentVelocity = Vec3d.ZERO;

    private void updateControlStick(MinecraftClient client, float tickDelta) {
        if (currentKeyMoveMode != POST_MOVE_KEYS.MOVE_CAMERA_FLAT &&
                currentKeyMoveMode != POST_MOVE_KEYS.MOVE_CAMERA_FREE) {

            if (client.player == null) return;
            Camera camera = client.gameRenderer.getCamera();
            if (camera != null) {
                Vec3d eyePos = client.player.getCameraPosVec(tickDelta);
                float yaw = client.player.getYaw(tickDelta);
                float pitch = client.player.getPitch(tickDelta);

                // Update movement tracking for VELOCITY targets
                if (currentEndTarget == AbstractMovementSettings.END_TARGET.VELOCITY_BACK ||
                        currentEndTarget == AbstractMovementSettings.END_TARGET.VELOCITY_FRONT) {
                    updateMovementTracking(new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ()));
                }

                // Calculate final angles based on target type
                float finalYaw = calculateTargetYaw(yaw);
                float finalPitch = calculateTargetPitch(pitch);

                controlStick.set(eyePos, finalYaw, finalPitch);
                // No per-frame logging here; keep noise low.
            }
        }
    }

    private float calculateTargetYaw(float playerYaw) {
        switch (currentEndTarget) {
            case HEAD_BACK:
                return playerYaw;
            case HEAD_FRONT:
                return (playerYaw + 180);
            case VELOCITY_BACK:
                return 360-targetYaw;
            case VELOCITY_FRONT:
                return (360-targetYaw + 180)%360;
            case FIXED_BACK:
                return playerYaw;
            case FIXED_FRONT:
                return (playerYaw + 180);
            default:
                return playerYaw;
        }
    }

    private float calculateTargetPitch(float playerPitch) {
        switch (currentEndTarget) {
            case HEAD_FRONT:
                return -playerPitch;
            case HEAD_BACK:
                return playerPitch;
            case VELOCITY_FRONT:
            case FIXED_FRONT:
                return 45f;  // Looking down at player
            case VELOCITY_BACK:
            case FIXED_BACK:
                return 45f; // Looking up from behind
            default:
                return playerPitch;
        }
    }

    private void updateMovementTracking(Vec3d currentPos) {
        if (lastPlayerPos.equals(Vec3d.ZERO)) {
            lastPlayerPos = currentPos;
            return;
        }

        // Calculate movement in XZ plane
        Vec3d movement = new Vec3d(
                currentPos.x - lastPlayerPos.x,
                0,
                currentPos.z - lastPlayerPos.z
        );

        if (movement.lengthSquared() > 0.001) { // Only update if there's significant movement
            cumulativeMovement = cumulativeMovement.add(movement);

            // Calculate movement direction (Minecraft coordinates)
            double movementYaw = Math.toDegrees(Math.atan2(movement.x, movement.z));
            while (movementYaw < 0) movementYaw += 360;

            // Linear interpolation based on cumulative movement distance
            double moveDistance = cumulativeMovement.length();
            double progress = Math.min(moveDistance / FULL_ROTATE_DISTANCE, 1.0);

            // Update target yaw
            targetYaw = (float)movementYaw;

            // Reset cumulative movement if we've reached full rotation
            if (moveDistance >= FULL_ROTATE_DISTANCE) {
                cumulativeMovement = Vec3d.ZERO;
            }
        }

        lastPlayerPos = currentPos;
    }

    public void setPreMoveStates(AbstractMovementSettings m){
        currentEndTarget = m.getEndTarget();
        // Reset any FOV modifications when starting a new movement
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.gameRenderer instanceof FovAccessor) {
            ((FovAccessor) client.gameRenderer).setFovModifier(1.0f);
        }
    }

    public void setPostMoveStates(AbstractMovementSettings m) {
        // If node edit is active, ignore requests to clear post-move state so freecam persists
        if (m == null) {
            // Reset state when movement ends
            currentKeyMoveMode = POST_MOVE_KEYS.NONE;
            currentMouseMoveMode = POST_MOVE_MOUSE.NONE;
            MouseInterceptor.setIntercepting(false);
            
            // Reset tracking variables
            lastPlayerPos = Vec3d.ZERO;
            cumulativeMovement = Vec3d.ZERO;
            
            // Reset keyboard input handling
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null && client.player.input instanceof IKeyboardInputMixin) {
                ((IKeyboardInputMixin) client.player.input).setDisabled(false);
            }
            // Close node editor if open
            net.minecraft.client.MinecraftClient _mc = net.minecraft.client.MinecraftClient.getInstance();
            if (_mc != null && _mc.currentScreen instanceof ninja.trek.nodes.ui.NodeEditorScreen) {
                _mc.setScreen(null);
            }
            ninja.trek.nodes.NodeManager.get().setEditing(false);
            CameraSystem cs = CameraSystem.getInstance();
            boolean wasActive = cs.isCameraActive();
            
        } else {
            // Set new movement modes
            currentMouseMoveMode = m.getPostMoveMouse();
            currentKeyMoveMode = m.getPostMoveKeys();

            MinecraftClient client = MinecraftClient.getInstance();
            Camera camera = client.gameRenderer.getCamera();
            
            // Reset the keyboard movement tracking flag when entering a new camera mode
            hasMovedWithKeyboard = false;
            
            // This is where the position must be preserved
            // freeCamPosition, freeCamYaw, and freeCamPitch should already be set by CameraMovementManager
            // before this method is called, so we don't need to capture them again here
            
            // Handle input disabling
            if (client.player != null && client.player.input instanceof IKeyboardInputMixin) {
                boolean shouldDisable = (currentKeyMoveMode == POST_MOVE_KEYS.MOVE_CAMERA_FLAT ||
                        currentKeyMoveMode == POST_MOVE_KEYS.MOVE_CAMERA_FREE);
                ((IKeyboardInputMixin) client.player.input).setDisabled(shouldDisable);
            }

            // Enable mouse interception for camera rotation
            if (currentMouseMoveMode == POST_MOVE_MOUSE.ROTATE_CAMERA) {
                MouseInterceptor.setIntercepting(true);
            }
            // Handle node edit overlay activation
            if (currentMouseMoveMode == POST_MOVE_MOUSE.NODE_EDIT) {
                boolean keysOk = (currentKeyMoveMode == POST_MOVE_KEYS.MOVE_CAMERA_FLAT || currentKeyMoveMode == POST_MOVE_KEYS.MOVE_CAMERA_FREE);
                if (keysOk) {
                    ninja.trek.nodes.NodeManager.get().setEditing(true);
                    if (!(client.currentScreen instanceof ninja.trek.nodes.ui.NodeEditorScreen)) {
                        client.setScreen(new ninja.trek.nodes.ui.NodeEditorScreen());
                    }
                    // Do NOT intercept mouse globally while screen is open; screen handles drag
                    MouseInterceptor.setIntercepting(false);
                } else {
                    currentMouseMoveMode = POST_MOVE_MOUSE.NONE;
                    ninja.trek.nodes.NodeManager.get().setEditing(false);
                }
            } else {
                // Ensure editor closed if not in node edit mode
                if (client.currentScreen instanceof ninja.trek.nodes.ui.NodeEditorScreen) {
                    client.setScreen(null);
                }
                ninja.trek.nodes.NodeManager.get().setEditing(false);
            }
            // Determine if we should activate custom camera mode (include NODE_EDIT)
            boolean isFreeCamMode = (currentKeyMoveMode == POST_MOVE_KEYS.MOVE_CAMERA_FLAT ||
                                    currentKeyMoveMode == POST_MOVE_KEYS.MOVE_CAMERA_FREE ||
                                    currentMouseMoveMode == POST_MOVE_MOUSE.ROTATE_CAMERA ||
                                    currentMouseMoveMode == POST_MOVE_MOUSE.NODE_EDIT);
            
            boolean isOutPosition = (currentEndTarget == AbstractMovementSettings.END_TARGET.HEAD_BACK ||
                                    currentEndTarget == AbstractMovementSettings.END_TARGET.FIXED_BACK ||
                                    currentEndTarget == AbstractMovementSettings.END_TARGET.VELOCITY_BACK);
            
            // Capture existing camera position before activating any new camera mode
            Vec3d existingCameraPos = camera.getPos();
            float existingYaw = camera.getYaw();
            float existingPitch = camera.getPitch();
            
            // Activate the appropriate camera mode
            CameraSystem cameraSystem = CameraSystem.getInstance();
            if (isFreeCamMode) {
                // Set the position first, so it's available during activation
                cameraSystem.setCameraPosition(freeCamPosition);
                cameraSystem.setCameraRotation(freeCamYaw, freeCamPitch);
                
                // Then activate the camera
                cameraSystem.activateCamera(CameraSystem.CameraMode.FREE_CAMERA);
                
                // Update the camera immediately to apply our position
                cameraSystem.updateCamera(camera);
                
            }
        }
    }

    private void handleKeyboardMovement(MinecraftClient client, Camera camera) {
        if (client.player == null) return;

        // Let the camera system handle movement
        CameraSystem cameraSystem = CameraSystem.getInstance();
        if (cameraSystem.isCameraActive()) {
            FreeCamSettings settings = GeneralMenuSettings.getFreeCamSettings();
            boolean moved = cameraSystem.handleMovementInput(
                settings.getMoveSpeed(),
                settings.getAcceleration(),
                settings.getDeceleration()
            );
            
            // Track if camera moved when using the camera system
            if (moved) {
                hasMovedWithKeyboard = true;
            }
            return;
        }

        // Legacy movement code
        // Base movement speed in blocks per tick
        float baseSpeed = GeneralMenuSettings.getFreeCamSettings().getMoveSpeed();

        // Sprint multiplier
        if (client.options.sprintKey.isPressed()) {
            baseSpeed *= 3.0f;
        }

        Vec3d targetVelocity = Vec3d.ZERO;
        
        // Calculate movement direction
        if (currentKeyMoveMode == POST_MOVE_KEYS.MOVE_CAMERA_FREE) {
            // Free camera movement in all directions
            double x = 0, y = 0, z = 0;
            
            if (client.options.forwardKey.isPressed()) {
                z += 1.0;
            }
            if (client.options.backKey.isPressed()) {
                z -= 1.0;
            }
            if (client.options.leftKey.isPressed()) {
                x += 1.0;
            }
            if (client.options.rightKey.isPressed()) {
                x -= 1.0;
            }
            if (client.options.jumpKey.isPressed()) {
                y += 1.0;
            }
            if (client.options.sneakKey.isPressed()) {
                y -= 1.0;
            }
            
            // Normalize if moving in multiple directions simultaneously
            if ((x != 0 && z != 0) || (x != 0 && y != 0) || (z != 0 && y != 0)) {
                double length = Math.sqrt(x * x + y * y + z * z);
                x /= length;
                y /= length;
                z /= length;
            }
            
            // Convert to camera-relative movement
            float yaw = freeCamYaw;
            float pitch = freeCamPitch;
            double xFactor = Math.sin(yaw * Math.PI / 180.0);
            double zFactor = Math.cos(yaw * Math.PI / 180.0);
            
            targetVelocity = new Vec3d(
                (x * zFactor - z * xFactor), 
                y, 
                (z * zFactor + x * xFactor)
            );
            
        } else if (currentKeyMoveMode == POST_MOVE_KEYS.MOVE_CAMERA_FLAT) {
            // Y-axis locked camera movement
            double x = 0, z = 0;
            
            if (client.options.forwardKey.isPressed()) {
                z += 1.0;
            }
            if (client.options.backKey.isPressed()) {
                z -= 1.0;
            }
            if (client.options.leftKey.isPressed()) {
                x += 1.0;
            }
            if (client.options.rightKey.isPressed()) {
                x -= 1.0;
            }
            
            // Y movement from jump/sneak
            double y = 0;
            if (client.options.jumpKey.isPressed()) {
                y += 1.0;
            }
            if (client.options.sneakKey.isPressed()) {
                y -= 1.0;
            }
            
            // Normalize XZ movement
            if (x != 0 && z != 0) {
                double length = Math.sqrt(x * x + z * z);
                x /= length;
                z /= length;
            }
            
            // Convert to camera-relative XZ movement with free Y
            float yaw = freeCamYaw;
            double xFactor = Math.sin(yaw * Math.PI / 180.0);
            double zFactor = Math.cos(yaw * Math.PI / 180.0);
            
            targetVelocity = new Vec3d(
                (x * zFactor - z * xFactor), 
                y, 
                (z * zFactor + x * xFactor)
            );
        }

        // Normalize and apply speed to target velocity if there's any movement
        if (targetVelocity.lengthSquared() > 0.0001) {
            targetVelocity = targetVelocity.normalize().multiply(baseSpeed);
        }

        // Apply acceleration or deceleration
        float acceleration = GeneralMenuSettings.getFreeCamSettings().getAcceleration();
        float deceleration = GeneralMenuSettings.getFreeCamSettings().getDeceleration();

        if (targetVelocity.lengthSquared() > 0.0001) {
            // Accelerating
            currentVelocity = currentVelocity.add(
                    targetVelocity.subtract(currentVelocity).multiply(acceleration)
            );
            
            // Mark as moved with keyboard if acceleration is happening
            if (!hasMovedWithKeyboard) {
                hasMovedWithKeyboard = true;
            }
        } else {
            // Decelerating
            currentVelocity = currentVelocity.multiply(1.0 - deceleration);
            // Zero out very small velocities to prevent perpetual drift
            if (currentVelocity.lengthSquared() < 0.0001) {
                currentVelocity = Vec3d.ZERO;
            }
        }

        // Apply movement
        freeCamPosition = freeCamPosition.add(currentVelocity);
        ((CameraAccessor) camera).invokesetPos(freeCamPosition);
    }

    public void updateCamera(MinecraftClient client, Camera camera, float tickDelta, float deltaSeconds) {
        updateControlStick(client, tickDelta);

        // Get the base camera state from movement manager - always update to track state
        CameraTarget baseTarget = CraneshotClient.MOVEMENT_MANAGER.update(client, camera, deltaSeconds);

        // Skip node influence when:
        // - In freecam/edit modes (manual camera control)
        boolean skipNodeInfluence = ninja.trek.nodes.NodeManager.get().isEditing()
                || currentKeyMoveMode != POST_MOVE_KEYS.NONE
                || currentMouseMoveMode != POST_MOVE_MOUSE.NONE
                ;

        baseTarget = ninja.trek.nodes.NodeManager.get().applyInfluence(baseTarget, skipNodeInfluence);

        // Handle node-based camera activation/deactivation
        CameraSystem cameraSystem = CameraSystem.getInstance();
        double currentNodeInfluence = 0.0;
        if (!skipNodeInfluence && client.player != null) {
            currentNodeInfluence = ninja.trek.nodes.NodeManager.get().getTotalInfluence(client.player.getEyePos());
        }

        // Activate camera when nodes start influencing
        if (currentNodeInfluence > 0.0 && !cameraActivatedByNodes && !skipNodeInfluence) {
            // Only activate if not already active from another source
            if (!cameraSystem.isCameraActive()) {
                cameraSystem.setCameraPosition(baseTarget.getPosition());
                cameraSystem.setCameraRotation(baseTarget.getYaw(), baseTarget.getPitch());
                cameraSystem.activateCamera(CameraSystem.CameraMode.NODE_DRIVEN);
                cameraActivatedByNodes = true;
            }
        }
        // Deactivate and trigger return when influence drops to 0
        // BUT: Don't deactivate if we're in freecam/edit mode (those modes own the camera now)
        else if (currentNodeInfluence <= 0.0 && cameraActivatedByNodes && lastNodeInfluence > 0.0 && !skipNodeInfluence) {
            // Camera was active due to nodes, but influence dropped to zero
            // Capture current camera position before deactivating
            freeCamPosition = cameraSystem.getCameraPosition();
            freeCamYaw = cameraSystem.getCameraYaw();
            freeCamPitch = cameraSystem.getCameraPitch();

            // Deactivate camera system
            cameraSystem.deactivateCamera();

            // Start FreeCamReturnMovement to smoothly return to player
            if (client != null) {
                ninja.trek.cameramovements.movements.FreeCamReturnMovement returnMovement =
                    ninja.trek.config.GeneralMenuSettings.getFreeCamReturnMovement();
                returnMovement.start(client, camera);

                // Manually set it as the active movement in the manager
                // (this mimics what finishTransition does)
                CraneshotClient.MOVEMENT_MANAGER.setActiveMovementForNodeReturn(returnMovement);
            }

            cameraActivatedByNodes = false;
        }

        lastNodeInfluence = currentNodeInfluence;

        // Check if we have an active camera system
        boolean cameraSystemActive = cameraSystem.isCameraActive();

        if (baseTarget != null) {
            // Update FOV in game renderer
            if (client.gameRenderer instanceof FovAccessor) {
                float fovMultiplier = (float) baseTarget.getFovMultiplier();
                ((FovAccessor) client.gameRenderer).setFovModifier(fovMultiplier);
            }
            
            if (cameraSystemActive) {
                // Let the camera system update its state
                boolean rotating = (currentMouseMoveMode == POST_MOVE_MOUSE.ROTATE_CAMERA) || ninja.trek.nodes.NodeManager.get().isEditing();
                boolean entityFreecam = (ninja.trek.util.CameraEntity.getCamera() != null);

                // If camera is being controlled by nodes, force position/rotation from baseTarget
                if (cameraActivatedByNodes && currentNodeInfluence > 0.0) {
                    // Nodes are controlling the camera - override position/rotation
                    cameraSystem.setCameraPosition(baseTarget.getPosition());
                    cameraSystem.setCameraRotation(baseTarget.getYaw(), baseTarget.getPitch());

                    // Synchronize CameraEntity if it exists
                    if (entityFreecam) {
                        ninja.trek.util.CameraEntity camEnt = ninja.trek.util.CameraEntity.getCamera();
                        if (camEnt != null) {
                            Vec3d pos = baseTarget.getPosition();
                            camEnt.setPos(pos.x, pos.y, pos.z);
                            camEnt.setCameraRotations(baseTarget.getYaw(), baseTarget.getPitch());
                            camEnt.setVelocity(Vec3d.ZERO); // Prevent physics interference
                        }
                    }
                }
                // Avoid per-frame logs; rely on Diag guards inside CameraSystem
                else if (entityFreecam) {
                    // If using the dedicated camera entity: do not push manual camera pos/rot
                    if (rotating && client.mouse instanceof IMouseMixin) {
                        IMouseMixin mouseMixin = (IMouseMixin) client.mouse;
                        double deltaX = mouseMixin.getCapturedDeltaX();
                        double deltaY = -mouseMixin.getCapturedDeltaY();
                        if (deltaX != 0 || deltaY != 0) {
                            double mouseSensitivity = client.options.getMouseSensitivity().getValue();
                            double calculatedSensitivity = 0.6 * mouseSensitivity * mouseSensitivity * mouseSensitivity + 0.2;
                            ninja.trek.util.CameraEntity camEnt = ninja.trek.util.CameraEntity.getCamera();
                            if (camEnt != null) {
                                // Invert Y like vanilla: moving mouse up should decrease pitch
                                camEnt.updateCameraRotations((float)(deltaX * calculatedSensitivity), (float)(-deltaY * calculatedSensitivity));
                            }
                        }
                    }
                } else {
                    if (rotating && client.mouse instanceof IMouseMixin) {
                        IMouseMixin mouseMixin = (IMouseMixin) client.mouse;
                        double deltaX = mouseMixin.getCapturedDeltaX();
                        double deltaY = -mouseMixin.getCapturedDeltaY();
                        if (deltaX != 0 || deltaY != 0) {
                            double mouseSensitivity = client.options.getMouseSensitivity().getValue();
                            double calculatedSensitivity = 0.6 * mouseSensitivity * mouseSensitivity * mouseSensitivity + 0.2;
                            cameraSystem.updateRotation(
                                deltaX * calculatedSensitivity * 0.55D,
                                deltaY * calculatedSensitivity * 0.55D,
                                1.0
                            );
                        }
                    } else if (!cameraActivatedByNodes) {
                        // Use the movement manager's position/rotation if not freely rotating and not controlled by nodes
                        cameraSystem.setCameraPosition(baseTarget.getPosition());
                        cameraSystem.setCameraRotation(baseTarget.getYaw(), baseTarget.getPitch());
                    }
                }

                // Let the camera system update the camera (if not using CameraEntity)
                if (!entityFreecam || cameraActivatedByNodes) {
                    cameraSystem.updateCamera(camera);
                }

                // Update our tracking variables for legacy code support
                freeCamPosition = cameraSystem.getCameraPosition();
                freeCamYaw = cameraSystem.getCameraYaw();
                freeCamPitch = cameraSystem.getCameraPitch();
            } else {
                // Legacy camera handling
                // Only update freeCamPosition from movement if we're not in free movement mode
                if (currentKeyMoveMode != POST_MOVE_KEYS.MOVE_CAMERA_FLAT &&
                        currentKeyMoveMode != POST_MOVE_KEYS.MOVE_CAMERA_FREE) {
                    freeCamPosition = baseTarget.getPosition();
                }

                // Handle rotation based on movement mode
                if (currentMouseMoveMode == POST_MOVE_MOUSE.ROTATE_CAMERA && client.mouse instanceof IMouseMixin) {
                    IMouseMixin mouseMixin = (IMouseMixin) client.mouse;
                    double deltaX = mouseMixin.getCapturedDeltaX();
                    double deltaY = -mouseMixin.getCapturedDeltaY();
                    double mouseSensitivity = client.options.getMouseSensitivity().getValue();
                    double calculatedSensitivity = 0.6 * mouseSensitivity * mouseSensitivity * mouseSensitivity + 0.2;
                    deltaX *= calculatedSensitivity * 0.55D;
                    deltaY *= calculatedSensitivity * 0.55D;
                    if (deltaX != 0 || deltaY != 0) {
                        freeCamYaw += deltaX;
                        freeCamPitch = (float) Math.max(-90.0F, Math.min(90.0F, freeCamPitch - deltaY));
                    }
                } else {
                    freeCamYaw = baseTarget.getYaw();
                    freeCamPitch = baseTarget.getPitch();
                }

                // Apply the camera position and rotation (no log)
                ((CameraAccessor) camera).invokesetPos(freeCamPosition);
                ((CameraAccessor) camera).invokeSetRotation(freeCamYaw, freeCamPitch);
            }
        } else if (cameraSystemActive) {
            // If we have no target but the camera system is active, let it update
            cameraSystem.updateCamera(camera);
            
            // Update tracking variables
            freeCamPosition = cameraSystem.getCameraPosition();
            freeCamYaw = cameraSystem.getCameraYaw();
            freeCamPitch = cameraSystem.getCameraPitch();
        }

        // Handle keyboard movement for camera modes (also when editor is open)
        if (currentKeyMoveMode == POST_MOVE_KEYS.MOVE_CAMERA_FLAT ||
            currentKeyMoveMode == POST_MOVE_KEYS.MOVE_CAMERA_FREE) {
            handleKeyboardMovement(client, camera);
        }

        updateMessageTimer();
    }



    //=== Message Handling ========================================================

    public void showMessage(String message) {
        currentMessage = message;
        messageTimer = System.currentTimeMillis() + MESSAGE_DURATION;
    }

    public String getCurrentMessage() {
        return currentMessage;
    }

    public boolean hasActiveMessage() {
        return System.currentTimeMillis() < messageTimer;
    }

    private void updateMessageTimer() {
        if (System.currentTimeMillis() >= messageTimer) {
            currentMessage = "";
        }
    }

    /**
     * Updates the camera each frame. This method (which used to be called handleCameraUpdate)
     * is responsible for applying the computed camera state (from the movement manager) as well as
     * processing any free keyboard/mouse input. It should be placed in CameraController.
     */
    public void handleCameraUpdate(BlockView area, Entity focusedEntity, boolean thirdPerson,
                                   boolean inverseView, float tickDelta, float frameSeconds, Camera camera) {
        // Verify that both the camera and the focused entity exist.
        if (camera == null || focusedEntity == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) return;

        // Update the camera based on movement-manager and free control states.
        updateCamera(client, camera, tickDelta, frameSeconds);

        // Optionally update keyboard input (e.g. disable it when free control is active)
        updateKeyboardInput(client);
    }

    private void updateKeyboardInput(MinecraftClient client) {
        if (client.player != null && client.player.input instanceof IKeyboardInputMixin) {
            // Only disable player movement when our post-move mode requires camera keyboard control.
            // Do NOT blanket-disable just because the camera system is active; Bezier out-phase needs player input.
            boolean shouldDisable = currentKeyMoveMode == POST_MOVE_KEYS.MOVE8 ||
                                   currentKeyMoveMode == POST_MOVE_KEYS.MOVE_CAMERA_FLAT ||
                                   currentKeyMoveMode == POST_MOVE_KEYS.MOVE_CAMERA_FREE;
            ((IKeyboardInputMixin) client.player.input).setDisabled(shouldDisable);
        }
    }

    public void onComplete() {
        // If node edit is active, preserve freecam and input states
        if (ninja.trek.nodes.NodeManager.get().isEditing()) {
            return;
        }

        // Reset all movement modes completely
        currentMouseMoveMode = POST_MOVE_MOUSE.NONE;
        currentKeyMoveMode = POST_MOVE_KEYS.NONE;

        // Reset the keyboard movement tracking flag
        hasMovedWithKeyboard = false;

        // Reset node activation tracking
        cameraActivatedByNodes = false;
        lastNodeInfluence = 0.0;

        // Ensure keyboard input is enabled for the player
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null && client.player.input instanceof IKeyboardInputMixin) {
            ((IKeyboardInputMixin) client.player.input).setDisabled(false);
        }

        // Disable mouse interception
        MouseInterceptor.setIntercepting(false);

        // Make sure to restore default camera behavior by deactivating the camera system
        CameraSystem cameraSystem = CameraSystem.getInstance();
        if (cameraSystem.isCameraActive()) {
            cameraSystem.deactivateCamera();
        }

        // Reset the camera position to follow the player
        if (client != null && client.player != null) {
            freeCamPosition = client.player.getEyePos();
            freeCamYaw = client.player.getYaw();
            freeCamPitch = client.player.getPitch();

        }

        // Reset FOV to default
        if (client != null && client.gameRenderer instanceof FovAccessor) {
            ((FovAccessor) client.gameRenderer).setFovModifier(1.0f);
        }
    }
}
