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
import ninja.trek.mixin.client.CameraAccessor;


/**
 * Applies the computed camera state to the actual camera and handles direct keyboard and mouse input.
 */
public class CameraController {
    // The movement manager computes and maintains the base camera state.

    // Fields that hold free-control state (updated when in MOVE/ROTATE modes).
    public static POST_MOVE_KEYS currentKeyMoveMode = POST_MOVE_KEYS.NONE;
    public static POST_MOVE_MOUSE currentMouseMoveMode = POST_MOVE_MOUSE.NONE;
    public static Vec3d freeCamPosition = Vec3d.ZERO;
    public static float freeCamYaw = 0f;
    public static float freeCamPitch = 0f;

    // A “control stick” target (e.g., used to drive the base position).
    public static CameraTarget controlStick = new CameraTarget();

    // On-screen message handling.
    private String currentMessage = "";
    private long messageTimer = 0;
    private static final long MESSAGE_DURATION = 2000;

    public static final double FIRST_PERSON_THRESHOLD_MIN = 2.0;
    public static final double FIRST_PERSON_THRESHOLD_MAX = 5.0;

    public CameraController() {
    }

    //=== Input & Free Control Handling ===========================================

    /**
     * Updates the control stick based on the player's current position and view.
     */
    private void updateControlStick(MinecraftClient client) {
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

    /**
     * Handles keyboard-based free movement.
     */
    private void handleKeyboardMovement(MinecraftClient client, Camera camera) {
        float speed = 0.5f;
        Vec3d movement = Vec3d.ZERO;
        if (currentKeyMoveMode == POST_MOVE_KEYS.MOVE_CAMERA_FREE) {
            float yaw = freeCamYaw;
            float pitch = freeCamPitch;
            Vec3d forward = new Vec3d(
                    -Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)),
                    -Math.sin(Math.toRadians(pitch)),
                    Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch))
            ).normalize();
            Vec3d worldUp = new Vec3d(0, 1, 0);
            Vec3d right = worldUp.crossProduct(forward).normalize();
            Vec3d up = forward.crossProduct(right).normalize();
            if (client.options.forwardKey.isPressed()) movement = movement.add(forward);
            if (client.options.backKey.isPressed()) movement = movement.subtract(forward);
            if (client.options.leftKey.isPressed()) movement = movement.add(right);
            if (client.options.rightKey.isPressed()) movement = movement.subtract(right);
            if (client.options.jumpKey.isPressed()) movement = movement.add(up);
            if (client.options.sneakKey.isPressed()) movement = movement.subtract(up);
        } else if (currentKeyMoveMode == POST_MOVE_KEYS.MOVE_CAMERA_FLAT) {
            float yaw = freeCamYaw;
            Vec3d forward = new Vec3d(
                    -Math.sin(Math.toRadians(yaw)),
                    0,
                    Math.cos(Math.toRadians(yaw))
            );
            Vec3d right = new Vec3d(
                    Math.cos(Math.toRadians(yaw)),
                    0,
                    Math.sin(Math.toRadians(yaw))
            );
            if (client.options.forwardKey.isPressed()) movement = movement.add(forward);
            if (client.options.backKey.isPressed()) movement = movement.subtract(forward);
            if (client.options.leftKey.isPressed()) movement = movement.add(right);
            if (client.options.rightKey.isPressed()) movement = movement.subtract(right);
            if (client.options.jumpKey.isPressed()) movement = movement.add(0, 1, 0);
            if (client.options.sneakKey.isPressed()) movement = movement.add(0, -1, 0);
        }
        if (movement.lengthSquared() > 0) {
            movement = movement.normalize().multiply(speed);
            freeCamPosition = freeCamPosition.add(movement);
            ((CameraAccessor) camera).invokesetPos(freeCamPosition);
        }
    }

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
    public void updateCamera(MinecraftClient client, Camera camera) {
        if (CraneshotClient.MOVEMENT_MANAGER.getActiveMovement() == null) return;
        updateControlStick(client);
        CameraTarget baseTarget = CraneshotClient.MOVEMENT_MANAGER.update(client, camera);
        if (baseTarget != null) {
            // Always update the position according to the current movement.
            freeCamPosition = baseTarget.getPosition();
            ((CameraAccessor) camera).invokesetPos(freeCamPosition);

            // ROTATION:
            // If we're in ROTATE_CAMERA mode, apply mouse deltas (if any) to adjust freeCamYaw/freeCamPitch.
            if (currentMouseMoveMode == POST_MOVE_MOUSE.ROTATE_CAMERA && client.mouse instanceof IMouseMixin) {
                IMouseMixin mouseMixin = (IMouseMixin) client.mouse;
                double deltaX = mouseMixin.getCapturedDeltaX();
                double deltaY = -mouseMixin.getCapturedDeltaY();
                double mouseSensitivity = client.options.getMouseSensitivity().getValue();
                double scaledSensitivity = 0.6 * mouseSensitivity * mouseSensitivity + 0.2;
                // Only update rotation if there is nonzero mouse input
                Craneshot.LOGGER.info("free rotation apdfsfdplied");
                if (deltaX != 0 || deltaY != 0) {
                    freeCamYaw += (float) (deltaX * scaledSensitivity);
                    freeCamPitch = Math.max(-90, Math.min(90, freeCamPitch - (float) (deltaY * scaledSensitivity)));
                    Craneshot.LOGGER.info("free rotation applied");
                }
                // (If no mouse delta occurs, leave freeCamYaw/freeCamPitch unchanged so that the camera keeps its rotated view.)
            } else {
                // In non-rotate mode, follow the movement’s computed rotation.
                freeCamYaw = baseTarget.getYaw();
                freeCamPitch = baseTarget.getPitch();
            }
            ((CameraAccessor) camera).invokeSetRotation(freeCamYaw, freeCamPitch);
        }
        updatePerspective(client, camera);
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
                                   boolean inverseView, float tickDelta, Camera camera) {
        // Verify that both the camera and the focused entity exist.
        if (camera == null || focusedEntity == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) return;

        // Update the camera based on movement-manager and free control states.
        updateCamera(client, camera);

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
            // Make sure keyboard input is re-enabled when no movement is active
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null && client.player.input instanceof IKeyboardInputMixin) {
                ((IKeyboardInputMixin) client.player.input).setDisabled(false);
            }
        } else {
            currentMouseMoveMode = m.getPostMoveMouse();
            currentKeyMoveMode = m.getPostMoveKeys();

            // Only disable keyboard input during MOVE8 mode
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null && client.player.input instanceof IKeyboardInputMixin) {
                ((IKeyboardInputMixin) client.player.input).setDisabled(currentKeyMoveMode == POST_MOVE_KEYS.MOVE8);
            }

            if (currentMouseMoveMode == POST_MOVE_MOUSE.ROTATE_CAMERA) {
                MouseInterceptor.setIntercepting(true);
            }
        }
    }

    private void updateKeyboardInput(MinecraftClient client) {
        if (client.player != null && client.player.input instanceof IKeyboardInputMixin) {
            // Only disable keyboard input during MOVE8 mode
            boolean shouldDisable = currentKeyMoveMode == POST_MOVE_KEYS.MOVE8;
            ((IKeyboardInputMixin) client.player.input).setDisabled(shouldDisable);
        }
    }

    public void onComplete() {
        currentMouseMoveMode = POST_MOVE_MOUSE.NONE;
        currentKeyMoveMode = POST_MOVE_KEYS.NONE;
    }
}
