package ninja.trek;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.option.Perspective;
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

    private void updateControlStick(MinecraftClient client) {
        if (currentKeyMoveMode != POST_MOVE_KEYS.MOVE_CAMERA_FLAT &&
                currentKeyMoveMode != POST_MOVE_KEYS.MOVE_CAMERA_FREE) {

            if (client.player == null) return;
            Camera camera = client.gameRenderer.getCamera();
            if (camera != null) {
                Vec3d eyePos = client.player.getEyePos();
                float yaw = client.player.getYaw();
                float pitch = client.player.getPitch();

                // Update movement tracking for VELOCITY targets
                if (currentEndTarget == AbstractMovementSettings.END_TARGET.VELOCITY_BACK ||
                        currentEndTarget == AbstractMovementSettings.END_TARGET.VELOCITY_FRONT) {
                    updateMovementTracking(client.player.getPos());
                }

                // Calculate final angles based on target type
                float finalYaw = calculateTargetYaw(yaw);
                float finalPitch = calculateTargetPitch(pitch);

                controlStick.set(eyePos, finalYaw, finalPitch);
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
            
            // Disable camera system
            CameraSystem.getInstance().deactivateCamera();
        } else {
            // Set new movement modes
            currentMouseMoveMode = m.getPostMoveMouse();
            currentKeyMoveMode = m.getPostMoveKeys();

            MinecraftClient client = MinecraftClient.getInstance();
            
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
            
            // Determine if we should activate custom camera mode
            boolean isFreeCamMode = (currentKeyMoveMode == POST_MOVE_KEYS.MOVE_CAMERA_FLAT ||
                                    currentKeyMoveMode == POST_MOVE_KEYS.MOVE_CAMERA_FREE);
            
            boolean isOutPosition = (currentEndTarget == AbstractMovementSettings.END_TARGET.HEAD_BACK ||
                                    currentEndTarget == AbstractMovementSettings.END_TARGET.FIXED_BACK ||
                                    currentEndTarget == AbstractMovementSettings.END_TARGET.VELOCITY_BACK);
            
            // Activate the appropriate camera mode
            CameraSystem cameraSystem = CameraSystem.getInstance();
            if (isFreeCamMode) {
                cameraSystem.activateCamera(CameraSystem.CameraMode.FREE_CAMERA);
            } else if (isOutPosition) {
                cameraSystem.activateCamera(CameraSystem.CameraMode.THIRD_PERSON);
            }
        }
    }

    private void handleKeyboardMovement(MinecraftClient client, Camera camera) {
        if (client.player == null) return;

        // Let the camera system handle movement
        CameraSystem cameraSystem = CameraSystem.getInstance();
        if (cameraSystem.isCameraActive()) {
            FreeCamSettings settings = GeneralMenuSettings.getFreeCamSettings();
            cameraSystem.handleMovementInput(
                settings.getMoveSpeed(),
                settings.getAcceleration(),
                settings.getDeceleration()
            );
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

    public void updateCamera(MinecraftClient client, Camera camera, float delta) {
        updateControlStick(client);

        // Get the base camera state from movement manager - always update to track state
        CameraTarget baseTarget = CraneshotClient.MOVEMENT_MANAGER.update(client, camera);

        if (baseTarget != null) {
            // Update FOV in game renderer
            if (client.gameRenderer instanceof FovAccessor) {
                float fovMultiplier = (float) baseTarget.getFovMultiplier();
                ((FovAccessor) client.gameRenderer).setFovModifier(fovMultiplier);
                // Only log significant FOV changes to reduce console spam
                if (Math.abs(fovMultiplier - 1.0) > 0.001) {
                    Craneshot.LOGGER.debug("set fov {}", fovMultiplier);
                }
            }
            
            // Check if camera system is active
            CameraSystem cameraSystem = CameraSystem.getInstance();
            
            if (cameraSystem.isCameraActive()) {
                // Let the camera system update its state
                if (currentMouseMoveMode == POST_MOVE_MOUSE.ROTATE_CAMERA && client.mouse instanceof IMouseMixin) {
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
                } else {
                    // Use the movement manager's position/rotation if not freely rotating
                    cameraSystem.setCameraPosition(baseTarget.getPosition());
                    cameraSystem.setCameraRotation(baseTarget.getYaw(), baseTarget.getPitch());
                }
                
                // Update our tracking variables for legacy code support
                freeCamPosition = cameraSystem.getCameraPosition();
                freeCamYaw = cameraSystem.getCameraYaw();
                freeCamPitch = cameraSystem.getCameraPitch();
                
                // Let the camera system update the camera
                cameraSystem.updateCamera(camera);
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
                
                // Apply the camera position and rotation
                ((CameraAccessor) camera).invokesetPos(freeCamPosition);
                ((CameraAccessor) camera).invokeSetRotation(freeCamYaw, freeCamPitch);
            }
        }

        // Handle keyboard movement for camera modes
        if (currentKeyMoveMode == POST_MOVE_KEYS.MOVE_CAMERA_FLAT ||
                currentKeyMoveMode == POST_MOVE_KEYS.MOVE_CAMERA_FREE) {
            handleKeyboardMovement(client, camera);
        }

        updatePerspective(client, camera);
        updateMessageTimer();
    }

    /**
     * Updates the camera perspective based on the distance between the camera and the player.
     */
    private void updatePerspective(MinecraftClient client, Camera camera) {
        if (client.player == null) return;
        
        // In orthographic mode, we always want to be in third person
        if (OrthographicCameraManager.isOrthographicMode() && 
            client.options.getPerspective() == Perspective.FIRST_PERSON) {
            client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            return;
        }
        
        // Check if camera system is controlling rendering
        CameraSystem cameraSystem = CameraSystem.getInstance();
        if (cameraSystem.isCameraActive()) {
            // Let camera system handle perspective
            if (cameraSystem.shouldRenderPlayerModel() && 
                client.options.getPerspective() == Perspective.FIRST_PERSON) {
                client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            } else if (!cameraSystem.shouldRenderPlayerModel() && 
                       client.options.getPerspective() != Perspective.FIRST_PERSON) {
                client.options.setPerspective(Perspective.FIRST_PERSON);
            }
            return;
        }
        
        // Legacy perspective handling based on distance
        double distance = camera.getPos().distanceTo(controlStick.getPosition());
        if (distance > FIRST_PERSON_THRESHOLD_MIN && client.options.getPerspective() == Perspective.FIRST_PERSON) {
            client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        } else if (distance < FIRST_PERSON_THRESHOLD_MIN && client.options.getPerspective() != Perspective.FIRST_PERSON) {
            client.options.setPerspective(Perspective.FIRST_PERSON);
        }
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
                                   boolean inverseView, float tickDelta, Camera camera) {
        // Verify that both the camera and the focused entity exist.
        if (camera == null || focusedEntity == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) return;

        // Update the camera based on movement-manager and free control states.
        updateCamera(client, camera, tickDelta);

        // Optionally update keyboard input (e.g. disable it when free control is active)
        updateKeyboardInput(client);
    }

    private void updateKeyboardInput(MinecraftClient client) {
        if (client.player != null && client.player.input instanceof IKeyboardInputMixin) {
            // Check if camera system is active and should disable player movement
            CameraSystem cameraSystem = CameraSystem.getInstance();
            if (cameraSystem.isCameraActive()) {
                ((IKeyboardInputMixin) client.player.input).setDisabled(true);
                return;
            }
            
            // Legacy keyboard input control
            boolean shouldDisable = currentKeyMoveMode == POST_MOVE_KEYS.MOVE8 ||
                                   currentKeyMoveMode == POST_MOVE_KEYS.MOVE_CAMERA_FLAT ||
                                   currentKeyMoveMode == POST_MOVE_KEYS.MOVE_CAMERA_FREE;
            ((IKeyboardInputMixin) client.player.input).setDisabled(shouldDisable);
        }
    }

    public void onComplete() {
        currentMouseMoveMode = POST_MOVE_MOUSE.NONE;
        currentKeyMoveMode = POST_MOVE_KEYS.NONE;
        
        // Make sure to deactivate camera system
        CameraSystem.getInstance().deactivateCamera();
    }
}