package ninja.trek;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import ninja.trek.cameramovements.AbstractMovementSettings;
import ninja.trek.cameramovements.AbstractMovementSettings.POST_MOVE_KEYS;
import ninja.trek.cameramovements.AbstractMovementSettings.POST_MOVE_MOUSE;
import ninja.trek.cameramovements.CameraTarget;
import ninja.trek.config.FreeCamSettings;
import ninja.trek.mixin.client.CameraAccessor;

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

    private void updateControlStick(MinecraftClient client) {
        // Only update controlStick if we're not in a camera movement mode
        if (currentKeyMoveMode != POST_MOVE_KEYS.MOVE_CAMERA_FLAT &&
                currentKeyMoveMode != POST_MOVE_KEYS.MOVE_CAMERA_FREE) {

            if (client.player == null) return;
            Camera camera = client.gameRenderer.getCamera();
            if (camera != null) {
                if (currentMouseMoveMode == POST_MOVE_MOUSE.ROTATE_CAMERA)
                    controlStick.set(
                            client.player.getEyePos(),
                            controlStick.getYaw(),
                            controlStick.getPitch()
                    );
                else controlStick.set(
                        client.player.getEyePos(),
                        client.player.getYaw(),
                        client.player.getPitch()
                );
            }
        }
    }

    private Vec3d currentVelocity = Vec3d.ZERO;

    private void handleKeyboardMovement(MinecraftClient client, Camera camera) {
        if (client.player == null) return;

        // Base movement speed in blocks per tick
        float baseSpeed = FreeCamSettings.getMoveSpeed();
        // Sprint multiplier
        if (client.options.sprintKey.isPressed()) {
            baseSpeed *= 3.0f;
        }

        Vec3d targetVelocity = Vec3d.ZERO;

        if (currentKeyMoveMode == POST_MOVE_KEYS.MOVE_CAMERA_FREE) {
            // Free movement - Allow full 3D movement based on camera rotation
            float yaw = freeCamYaw;
            float pitch = freeCamPitch;

            // Calculate movement vectors
            Vec3d forward = new Vec3d(
                    -Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)),
                    -Math.sin(Math.toRadians(pitch)),
                    Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch))
            ).normalize();

            Vec3d worldUp = new Vec3d(0, 1, 0);
            Vec3d right = forward.crossProduct(worldUp).normalize();
            if (right.lengthSquared() < 0.001) {
                // Handle edge case when looking straight up/down
                right = new Vec3d(Math.cos(Math.toRadians(yaw)), 0, Math.sin(Math.toRadians(yaw)));
            }
            Vec3d up = right.crossProduct(forward).normalize();

            // Accumulate movement based on pressed keys
            if (client.options.forwardKey.isPressed()) targetVelocity = targetVelocity.add(forward);
            if (client.options.backKey.isPressed()) targetVelocity = targetVelocity.subtract(forward);
            if (client.options.leftKey.isPressed()) targetVelocity = targetVelocity.subtract(right);
            if (client.options.rightKey.isPressed()) targetVelocity = targetVelocity.add(right);
            if (client.options.jumpKey.isPressed()) targetVelocity = targetVelocity.add(up);
            if (client.options.sneakKey.isPressed()) targetVelocity = targetVelocity.subtract(up);

        } else if (currentKeyMoveMode == POST_MOVE_KEYS.MOVE_CAMERA_FLAT) {
            // Flat movement - Only allow movement in horizontal plane
            float yaw = freeCamYaw;

            // Calculate horizontal movement vectors
            Vec3d forward = new Vec3d(
                    -Math.sin(Math.toRadians(yaw)),
                    0,
                    Math.cos(Math.toRadians(yaw))
            ).normalize();

            Vec3d right = new Vec3d(
                    Math.cos(Math.toRadians(yaw)),
                    0,
                    Math.sin(Math.toRadians(yaw))
            ).normalize();

            // Accumulate movement based on pressed keys
            if (client.options.forwardKey.isPressed()) targetVelocity = targetVelocity.add(forward);
            if (client.options.backKey.isPressed()) targetVelocity = targetVelocity.subtract(forward);
            if (client.options.leftKey.isPressed()) targetVelocity = targetVelocity.subtract(right);
            if (client.options.rightKey.isPressed()) targetVelocity = targetVelocity.add(right);

            // Vertical movement is world-aligned in flat mode
            if (client.options.jumpKey.isPressed()) targetVelocity = targetVelocity.add(0, 1, 0);
            if (client.options.sneakKey.isPressed()) targetVelocity = targetVelocity.subtract(0, 1, 0);
        }

        // Normalize and apply speed to target velocity if there's any movement
        if (targetVelocity.lengthSquared() > 0.0001) {
            targetVelocity = targetVelocity.normalize().multiply(baseSpeed);
        }

        // Apply acceleration or deceleration
        float acceleration = FreeCamSettings.getAcceleration();
        float deceleration = FreeCamSettings.getDeceleration();

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

//        // Ensure player input is disabled
//        if (client.player.input instanceof IKeyboardInputMixin) {
//            ((IKeyboardInputMixin) client.player.input).setDisabled(true);
//        }
    }
    public void updateCamera(MinecraftClient client, Camera camera, float delta) {
        updateControlStick(client);

        // Get the base camera state from movement manager - always update to track state
        CameraTarget baseTarget = CraneshotClient.MOVEMENT_MANAGER.update(client, camera);

        if (baseTarget != null) {
            // Only update freeCamPosition from movement if we're not in free movement mode
            if (currentKeyMoveMode != POST_MOVE_KEYS.MOVE_CAMERA_FLAT &&
                    currentKeyMoveMode != POST_MOVE_KEYS.MOVE_CAMERA_FREE) {
                freeCamPosition = baseTarget.getPosition();
                ((CameraAccessor) camera).invokesetPos(freeCamPosition);
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
            ((CameraAccessor) camera).invokeSetRotation(freeCamYaw, freeCamPitch);
        }

        // Handle keyboard movement for camera modes
        if (currentKeyMoveMode == POST_MOVE_KEYS.MOVE_CAMERA_FLAT ||
                currentKeyMoveMode == POST_MOVE_KEYS.MOVE_CAMERA_FREE) {
            handleKeyboardMovement(client, camera);
        }

        updatePerspective(client, camera);
        updateMessageTimer();
    }

    //=== Input & Free Control Handling ===========================================


    /**
     * Updates the camera perspective based on the distance between the camera and the player.
     */
    private void updatePerspective(MinecraftClient client, Camera camera) {
        if (client.player == null) return;
        double distance = camera.getPos().distanceTo(controlStick.getPosition());
        if (distance > FIRST_PERSON_THRESHOLD_MIN && client.options.getPerspective() == Perspective.FIRST_PERSON) {
            client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        } else if (distance < FIRST_PERSON_THRESHOLD_MIN && client.options.getPerspective() != Perspective.FIRST_PERSON) {
            client.options.setPerspective(Perspective.FIRST_PERSON);
        }
    }

    //=== Camera Update – Combining Movement and Free Controls ===================

    /**
     * Called every frame to update the camera. It retrieves the base target from the movement manager,
     * applies any free control modifications, and then applies the final state to the camera.
     */



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

    /**
     * Disables the player's default keyboard movement when free-control modes are active.
     */
    public void setPostMoveStates(AbstractMovementSettings m) {
        if (m == null) {
            currentKeyMoveMode = POST_MOVE_KEYS.NONE;
            currentMouseMoveMode = POST_MOVE_MOUSE.NONE;
            MouseInterceptor.setIntercepting(false);
            // Re-enable keyboard input
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null && client.player.input instanceof IKeyboardInputMixin) {
                ((IKeyboardInputMixin) client.player.input).setDisabled(false);
            }
        } else {
            currentMouseMoveMode = m.getPostMoveMouse();
            currentKeyMoveMode = m.getPostMoveKeys();

            // Disable keyboard input for camera movement modes
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null && client.player.input instanceof IKeyboardInputMixin) {
                boolean shouldDisable = (currentKeyMoveMode == POST_MOVE_KEYS.MOVE_CAMERA_FLAT ||
                        currentKeyMoveMode == POST_MOVE_KEYS.MOVE_CAMERA_FREE);
                ((IKeyboardInputMixin) client.player.input).setDisabled(shouldDisable);
            }

            // Enable mouse interception for camera rotation
            if (currentMouseMoveMode == POST_MOVE_MOUSE.ROTATE_CAMERA) {
                MouseInterceptor.setIntercepting(true);
            }
        }
    }

    private void updateKeyboardInput(MinecraftClient client) {
        if (client.player != null && client.player.input instanceof IKeyboardInputMixin) {
            // Only disable keyboard input during MOVE8 mode
            boolean shouldDisable = currentKeyMoveMode == POST_MOVE_KEYS.MOVE8 ||
                    currentKeyMoveMode == POST_MOVE_KEYS.MOVE_CAMERA_FLAT ||
                    currentKeyMoveMode == POST_MOVE_KEYS.MOVE_CAMERA_FREE;
            ((IKeyboardInputMixin) client.player.input).setDisabled(shouldDisable);
        }
    }

    public void onComplete() {
        currentMouseMoveMode = POST_MOVE_MOUSE.NONE;
        currentKeyMoveMode = POST_MOVE_KEYS.NONE;
    }
}
