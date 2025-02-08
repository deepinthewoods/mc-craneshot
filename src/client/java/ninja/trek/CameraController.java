package ninja.trek;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import ninja.trek.cameramovements.*;
import ninja.trek.config.SlotMenuSettings;
import ninja.trek.mixin.client.*;

import java.util.*;

public class CameraController {
    private List<List<ICameraMovement>> slots;
    private final ArrayList<Integer> currentTypes;
    private CameraMovementManager movementManager;
    private final Map<Integer, ICameraMovement> activeMovementSlots;
    public static final double FIRST_PERSON_THRESHOLD_MIN = 2.0;
    public static final double FIRST_PERSON_THRESHOLD_MAX = 5.0;
    private String currentMessage = "";
    private long messageTimer = 0;
    private static final long MESSAGE_DURATION = 2000;
    private boolean mouseControlEnabled = false;
    private boolean cameraMovementEnabled = false;
    private AbstractMovementSettings.POST_MOVE_KEYS currentMoveMode = AbstractMovementSettings.POST_MOVE_KEYS.NONE;
    public static boolean inFreeControlMode = false;
    public static Vec3d freeCamPosition;
    public static float freeCamYaw;
    public static float freeCamPitch;

    public CameraController() {
        slots = new ArrayList<>();
        currentTypes = new ArrayList<>();
        movementManager = new CameraMovementManager();
        activeMovementSlots = new HashMap<>();
        for (int i = 0; i < 3; i++) {
            currentTypes.add(0);
        }
    }

    private void handleFreeControl(MinecraftClient client, Camera camera) {


        if (cameraMovementEnabled) {
            handleKeyboardMovement(client, camera);
        }

        if (mouseControlEnabled && client.mouse instanceof IMouseMixin) {
            Double mouseSensitivity = MinecraftClient.getInstance().options.getMouseSensitivity().getValue();
            double scaledSensitivity = 0.6 * mouseSensitivity * mouseSensitivity + 0.2;
            IMouseMixin mouseMixin = (IMouseMixin) client.mouse;
            double deltaX = mouseMixin.getCapturedDeltaX();
            double deltaY = -mouseMixin.getCapturedDeltaY();
            freeCamYaw += (float)(deltaX * scaledSensitivity);
            freeCamPitch = Math.max(-90, Math.min(90, freeCamPitch - (float)(deltaY * scaledSensitivity)));
            ((CameraAccessor)camera).invokeSetRotation(freeCamYaw, freeCamPitch);
        }

        ((CameraAccessor)camera).invokesetPos(freeCamPosition);
    }

    private void handleKeyboardMovement(MinecraftClient client, Camera camera) {
        float speed = 0.5f; // Adjust speed as needed
        Vec3d movement = Vec3d.ZERO;

        if (currentMoveMode == AbstractMovementSettings.POST_MOVE_KEYS.MOVE_CAMERA_FREE) {
            // Full camera-relative movement including pitch
            float yaw = freeCamYaw;
            float pitch = freeCamPitch;
            Vec3d forward = new Vec3d(
                    -Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)),
                    -Math.sin(Math.toRadians(pitch)),
                    Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch))
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
        else if (currentMoveMode == AbstractMovementSettings.POST_MOVE_KEYS.MOVE_CAMERA_FLAT) {
            // Y-axis locked camera-relative movement
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
        else if (currentMoveMode == AbstractMovementSettings.POST_MOVE_KEYS.MOVE8) {
            // Move the player in 8 directions relative to camera view
            if (client.player != null) {
                float cameraYaw = freeCamYaw;

                // Determine the target yaw based on key combinations
                float targetYaw = cameraYaw;
                boolean moving = false;

                if (client.options.forwardKey.isPressed()) {
                    if (client.options.leftKey.isPressed()) {
                        targetYaw += 45;
                        moving = true;
                    } else if (client.options.rightKey.isPressed()) {
                        targetYaw -= 45;
                        moving = true;
                    } else {
                        moving = true;
                    }
                } else if (client.options.backKey.isPressed()) {
                    if (client.options.leftKey.isPressed()) {
                        targetYaw += 135;
                        moving = true;
                    } else if (client.options.rightKey.isPressed()) {
                        targetYaw -= 135;
                        moving = true;
                    } else {
                        targetYaw += 180;
                        moving = true;
                    }
                } else if (client.options.leftKey.isPressed()) {
                    targetYaw += 90;
                    moving = true;
                } else if (client.options.rightKey.isPressed()) {
                    targetYaw -= 90;
                    moving = true;
                }

                // Normalize the target yaw
                while (targetYaw > 180) targetYaw -= 360;
                while (targetYaw < -180) targetYaw += 360;

                // Set the player's rotation
                client.player.setYaw(targetYaw);

                // If moving, simulate forward movement
                if (moving) {
                    if (client.player.input instanceof IKeyboardInputMixin) {
                        IKeyboardInputMixin input = (IKeyboardInputMixin) client.player.input;
                        input.setDisabled(false);  // Enable input temporarily
                        client.player.input.movementForward = 1.0f;  // Simulate forward movement
                        client.player.input.movementSideways = 0.0f; // No sideways movement needed
                    }
                }

                // Handle jumping and sneaking
                if (client.options.jumpKey.isPressed() && client.player.isOnGround()) {
                    client.player.jump();
                }
                client.player.setSneaking(client.options.sneakKey.isPressed());

                return; // Return early since we're controlling the player directly
            }
        }

        if (movement.lengthSquared() > 0) {
            movement = movement.normalize().multiply(speed);
            Vec3d newPos = freeCamPosition.add(movement);
            ((CameraAccessor)camera).invokesetPos(newPos);
            freeCamPosition = newPos;
        }
    }

    public void queueFinish(MinecraftClient client, Camera camera) {
        if (inFreeControlMode) {
            if (mouseControlEnabled) {
                MouseInterceptor.setIntercepting(false);
            }
            updateKeyboardInput(client);
            freeCamPosition = camera.getPos();
            freeCamYaw = camera.getYaw();
            freeCamPitch = camera.getPitch();
        }
        inFreeControlMode = false;
        mouseControlEnabled = false;
        cameraMovementEnabled = false;
        currentMoveMode = AbstractMovementSettings.POST_MOVE_KEYS.NONE;

        for (ICameraMovement movement : activeMovementSlots.values()) {
            movement.queueReset(client, camera);
        }
    }

    private void updateKeyboardInput(MinecraftClient client) {
        if (client.player != null && client.player.input instanceof IKeyboardInputMixin) {
            boolean shouldDisable = cameraMovementEnabled;
            ((IKeyboardInputMixin) client.player.input).setDisabled(shouldDisable);
        }
    }

    public void tick(MinecraftClient client, Camera camera) {

        if (!inFreeControlMode) {
            movementManager.update(client, camera);

            for (ICameraMovement movement : activeMovementSlots.values()) {
                if (movement instanceof AbstractMovementSettings && movement.hasCompletedOutPhase()) {
                    AbstractMovementSettings settings = (AbstractMovementSettings) movement;

                    boolean enableMouse = settings.getPostMoveMouse() == AbstractMovementSettings.POST_MOVE_MOUSE.FREE_MOUSE;
                    boolean enableKeys = settings.getPostMoveKeys() != AbstractMovementSettings.POST_MOVE_KEYS.NONE;

                    if (enableMouse || enableKeys) {
                        inFreeControlMode = true;
                        mouseControlEnabled = enableMouse;
                        cameraMovementEnabled = enableKeys;
                        currentMoveMode = settings.getPostMoveKeys();

                        Vec3d finalPos = movement.calculateState(client, camera).getCameraTarget().getPosition();
                        freeCamPosition = finalPos;
                        freeCamYaw = camera.getYaw();
                        freeCamPitch = camera.getPitch();

                        ((CameraAccessor)camera).invokesetPos(freeCamPosition);
                        ((CameraAccessor)camera).invokeSetRotation(freeCamYaw, freeCamPitch);

                        if (mouseControlEnabled) {
                            MouseInterceptor.setIntercepting(true);
                        }
                        return;
                    }
                }
            }
        }
        updatePerspective(client, camera);
    }

    public void handleCameraUpdate(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, Camera camera) {
        if (camera == null || focusedEntity == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) return;

        if (inFreeControlMode) {
            handleFreeControl(client, camera);
        } else {
            tick(client, camera);
        }
        updateKeyboardInput(client);

        clearCompletedMovements(client, camera);
        updateMessageTimer();
    }








    private void clearCompletedMovements(MinecraftClient client, Camera camera) {
        if (!inFreeControlMode) {
            Iterator<Map.Entry<Integer, ICameraMovement>> iterator = activeMovementSlots.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Integer, ICameraMovement> entry = iterator.next();
                if (entry.getValue().isComplete()) {
                    entry.getValue().queueReset(client, camera);
                    iterator.remove();
                }
            }
        }
    }






    public void startTransition(MinecraftClient client, Camera camera, int movementIndex) {
//        Craneshot.LOGGER.info("startt");
        ICameraMovement movement = getMovementAt(movementIndex);
        if (movement == null) return;
//        Craneshot.LOGGER.info("startt notnull");
        clearAllMovements(client, camera);
        activeMovementSlots.clear();
        activeMovementSlots.put(movementIndex, movement);
        movement.start(client, camera);
        movementManager.addMovement(movement, client, camera);
    }



    private void updatePerspective(MinecraftClient client, Camera camera) {
        //we do this just to switch the player model rendering on or off
        if (client.player == null) return;
        double distance = camera.getPos().distanceTo(client.player.getEyePos());
        if (distance > FIRST_PERSON_THRESHOLD_MIN &&
                client.options.getPerspective() == Perspective.FIRST_PERSON) {
            Craneshot.LOGGER.info("first person p");
            client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        } else if (distance < FIRST_PERSON_THRESHOLD_MIN &&
                client.options.getPerspective() != Perspective.FIRST_PERSON) {
            Craneshot.LOGGER.info("threds person p");

            client.options.setPerspective(Perspective.FIRST_PERSON);
        }
    }

    // Message handling methods
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

    public void showMovementTypeMessage(int slotIndex) {
        ICameraMovement movement = getMovementAt(slotIndex);
        if (movement != null) {
            showMessage(String.format(
                    "Camera %d: %s Movement",
                    slotIndex + 1,
                    movement.getName()
            ));
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


    private void clearAllMovements(MinecraftClient client, Camera camera) {
        for (ICameraMovement movement : activeMovementSlots.values()) {
            movement.queueReset(client, camera);
        }
        movementManager = new CameraMovementManager();
        activeMovementSlots.clear();
    }

    public CameraMovementManager getMovementManager() {
        return movementManager;
    }

}