package ninja.trek;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import ninja.trek.cameramovements.AbstractMovementSettings;
import ninja.trek.cameramovements.ICameraMovement;
import ninja.trek.config.SlotMenuSettings;

import java.util.HashMap;
import java.util.Map;

public class CraneShotEventHandler {
    private static final double SCROLL_COOLDOWN = 0.1;
    private static double lastScrollTime = 0;
    private static final Map<Integer, Boolean> keyStates = new HashMap<>();
    private static Integer lastActiveSlot = null;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            handleScrollInput(client);
            CraneshotClient.checkKeybinds();
        });

        MovementToastRenderer.register();

        WorldRenderEvents.START.register(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            Camera camera = client.gameRenderer.getCamera();

            for (int i = 0; i < CraneshotClient.cameraKeyBinds.length; i++) {
                boolean currentlyPressed = CraneshotClient.cameraKeyBinds[i].isPressed();
                boolean wasPressed = keyStates.getOrDefault(i, false);

                if (currentlyPressed != wasPressed) {
                    boolean isToggle = SlotMenuSettings.getToggleState(i);
                    CraneshotClient.MOVEMENT_MANAGER.handleKeyStateChange(i, currentlyPressed, client, camera, isToggle);
                    if (currentlyPressed) {
                        lastActiveSlot = i;
                    }
                }
                keyStates.put(i, currentlyPressed);
            }
        });
    }

    /**
     * Safely get the scroll value from the mouse mixin
     * @param client The Minecraft client instance
     * @return The scroll value, or 0 if it couldn't be accessed
     */
    private static double getScrollValue(MinecraftClient client) {
        try {
            if (client.mouse instanceof IMouseMixin mouseMixin) {
                return mouseMixin.getLastScrollValue();
            }
        } catch (Exception e) {
            Craneshot.LOGGER.error("Error accessing mouse mixin: " + e.getMessage());
        }
        return 0;
    }
    
    /**
     * Safely reset the scroll value in the mouse mixin
     * @param client The Minecraft client instance
     */
    private static void resetScrollValue(MinecraftClient client) {
        try {
            if (client.mouse instanceof IMouseMixin mouseMixin) {
                mouseMixin.setLastScrollValue(0);
            }
        } catch (Exception e) {
            Craneshot.LOGGER.error("Error resetting scroll value: " + e.getMessage());
        }
    }

    private static void handleScrollInput(MinecraftClient client) {
        double currentTime = System.currentTimeMillis() / 1000.0;
        if (currentTime - lastScrollTime < SCROLL_COOLDOWN) {
            return;
        }

        double scrollDelta = getScrollValue(client);
        if (scrollDelta == 0) {
            return;
        }

        boolean scrollUp = scrollDelta < 0;

        // Check for active movement with scroll modes
        AbstractMovementSettings.SCROLL_WHEEL activeScrollMode =
                CraneshotClient.MOVEMENT_MANAGER.getActiveMouseWheelMode();
        ICameraMovement activeMovement = CraneshotClient.MOVEMENT_MANAGER.getActiveMovement();

        if (activeMovement != null) {
            if (activeScrollMode == AbstractMovementSettings.SCROLL_WHEEL.DISTANCE) {
                activeMovement.adjustDistance(!scrollUp, client);
                lastScrollTime = currentTime;
                resetScrollValue(client);
                return;
            } else if (activeScrollMode == AbstractMovementSettings.SCROLL_WHEEL.FOV) {
                if (activeMovement instanceof AbstractMovementSettings) {
                    ((AbstractMovementSettings) activeMovement).adjustFov(!scrollUp, client);
                    lastScrollTime = currentTime;
                    resetScrollValue(client);
                    return;
                }
            }
        }

        // Handle orthographic camera zoom if in orthographic mode
        if (OrthographicCameraManager.isOrthographicMode()) {
            // Use shift key as a modifier for orthographic zoom
            boolean shiftPressed = client.options.sneakKey.isPressed();
            if (shiftPressed) {
                if (scrollUp) {
                    OrthographicCameraManager.zoomIn();
                } else {
                    OrthographicCameraManager.zoomOut();
                }
                lastScrollTime = currentTime;
                resetScrollValue(client);
                return;
            }
        }
        
        // Handle normal slot scrolling if no active scroll modes
        for (int i = 0; i < CraneshotClient.cameraKeyBinds.length; i++) {
            if (CraneshotClient.cameraKeyBinds[i].isPressed()) {
                CraneshotClient.MOVEMENT_MANAGER.handleMouseScroll(i, scrollUp);
                lastScrollTime = currentTime;
                resetScrollValue(client);
                return;
            }
        }

        // Handle scroll with select movement key pressed
        if (CraneshotClient.selectMovementType.isPressed() && lastActiveSlot != null) {
            CraneshotClient.MOVEMENT_MANAGER.handleMouseScroll(lastActiveSlot, scrollUp);
            lastScrollTime = currentTime;
            resetScrollValue(client);
        }
    }
}