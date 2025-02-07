package ninja.trek;


import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import ninja.trek.cameramovements.*;
import ninja.trek.config.SlotMenuSettings;
import ninja.trek.mixin.client.CameraAccessor;
import ninja.trek.mixin.client.MouseAccessor;

import java.util.*;

public class CameraController {
    private List<List<ICameraMovement>> slots;
    private final ArrayList<Integer> currentTypes;
    private CameraMovementManager movementManager;
    private final Map<Integer, ICameraMovement> activeMovementSlots;
    private static final double FIRST_PERSON_THRESHOLD = 2.0;
    private String currentMessage = "";
    private long messageTimer = 0;
    private static final long MESSAGE_DURATION = 2000;
    private boolean mouseControlEnabled = false;
    private boolean moveControlEnabled = false;

    private boolean inFreeControlMode = false;
    private Vec3d freeCamPosition;
    private float freeCamYaw;
    private float freeCamPitch;

    public CameraController() {
        slots = new ArrayList<>();
        currentTypes = new ArrayList<>();
        movementManager = new CameraMovementManager();
        activeMovementSlots = new HashMap<>();
        for (int i = 0; i < 3; i++) {
            currentTypes.add(0);
        }
    }

    public void tick(MinecraftClient client, Camera camera) {
        if (!inFreeControlMode) {
            movementManager.update(client, camera);
            updatePerspective(client, camera);
            checkForFreeControlTransition(client, camera);
        }
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

    private void checkForFreeControlTransition(MinecraftClient client, Camera camera) {
        for (ICameraMovement movement : activeMovementSlots.values()) {
            if (movement instanceof AbstractMovementSettings && movement.hasCompletedOutPhase()) {
                AbstractMovementSettings settings = (AbstractMovementSettings) movement;
                AbstractMovementSettings.POST_MOVE_ACTION action = settings.getPostMoveAction();

                if (action != AbstractMovementSettings.POST_MOVE_ACTION.NONE && !inFreeControlMode) {
                    inFreeControlMode = true;
                    mouseControlEnabled = action == AbstractMovementSettings.POST_MOVE_ACTION.FREE_MOUSE;
                    moveControlEnabled = action == AbstractMovementSettings.POST_MOVE_ACTION.FREE_MOVE;

                    // Store current camera state
                    freeCamPosition = camera.getPos();
                    freeCamYaw = camera.getYaw();
                    freeCamPitch = camera.getPitch();

                    // Clear movement manager
                    movementManager = new CameraMovementManager();
                }
            }
        }
    }

    private void handleFreeControl(MinecraftClient client, Camera camera) {
        if (moveControlEnabled) {
            handleKeyboardMovement(client, camera);
        }
        if (mouseControlEnabled) {
            handleMouseMovement(client, camera);
        }

        // Always update last control position when in free control mode
        freeCamPosition = camera.getPos();
        freeCamYaw = camera.getYaw();
        freeCamPitch = camera.getPitch();
    }

    private void handleKeyboardMovement(MinecraftClient client, Camera camera) {
        float speed = 0.2f;
        Vec3d movement = Vec3d.ZERO;
        float yaw = freeCamYaw; // Use stored yaw for consistent movement
        float pitch = freeCamPitch; // Use stored pitch for consistent movement

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
        if (client.options.leftKey.isPressed()) movement = movement.subtract(right);
        if (client.options.rightKey.isPressed()) movement = movement.add(right);
        if (client.options.jumpKey.isPressed()) movement = movement.add(0, 1, 0);
        if (client.options.sneakKey.isPressed()) movement = movement.add(0, -1, 0);


        movement = movement.normalize().multiply(speed);
        Vec3d newPos = freeCamPosition.add(movement);
        ((CameraAccessor) camera).invokesetPos(newPos);

    }

    private void handleMouseMovement(MinecraftClient client, Camera camera) {
        MouseAccessor mouseAccessor = (MouseAccessor)client.mouse;
        double deltaX = mouseAccessor.getEventDeltaVerticalWheel();
        double deltaY = mouseAccessor.getEventDeltaVerticalWheel();

        if (deltaX != 0 || deltaY != 0) {
            float sensitivity = 0.15f;
            float newYaw = freeCamYaw + (float)deltaX * sensitivity;
            float newPitch = Math.max(-90, Math.min(90, freeCamPitch + (float)deltaY * sensitivity));

            ((CameraAccessor)camera).invokeSetRotation(newYaw, newPitch);
            freeCamYaw = newYaw;
            freeCamPitch = newPitch;
        }
    }

    public void queueFinish(MinecraftClient client, Camera camera) {
        if (inFreeControlMode) {
            // Store final position before exiting free control mode
            freeCamPosition = camera.getPos();
            freeCamYaw = camera.getYaw();
            freeCamPitch = camera.getPitch();
        }

        inFreeControlMode = false;
        mouseControlEnabled = false;
        moveControlEnabled = false;

        for (ICameraMovement movement : activeMovementSlots.values()) {
            movement.queueReset(client, camera);
        }
    }






    public void startTransition(MinecraftClient client, Camera camera, int movementIndex) {
        Craneshot.LOGGER.info("startt");
        ICameraMovement movement = getMovementAt(movementIndex);
        if (movement == null) return;
        Craneshot.LOGGER.info("startt notnull");
        clearAllMovements(client, camera);
        activeMovementSlots.clear();
        activeMovementSlots.put(movementIndex, movement);
        movement.start(client, camera);
        movementManager.addMovement(movement, client, camera);
    }



    private void updatePerspective(MinecraftClient client, Camera camera) {
        if (client.player == null) return;
        double distance = camera.getPos().distanceTo(client.player.getEyePos());
        if (distance > FIRST_PERSON_THRESHOLD &&
                client.options.getPerspective() == Perspective.FIRST_PERSON) {
            client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        } else if (distance < FIRST_PERSON_THRESHOLD &&
                client.options.getPerspective() != Perspective.FIRST_PERSON) {
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