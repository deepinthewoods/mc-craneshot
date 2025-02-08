package ninja.trek;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.text.Text;
import ninja.trek.cameramovements.ICameraMovement;
import ninja.trek.config.SlotMenuSettings;
import ninja.trek.mixin.client.MouseAccessor;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles client tick, HUD rendering, and world render events.
 * This class delegates key and scroll input to the movement manager and camera controller.
 */
public class CraneShotEventHandler {
    private static final double SCROLL_COOLDOWN = 0.1;
    private static double lastScrollTime = 0;
    private static final Map<Integer, Boolean> keyStates = new HashMap<>();

    public static void register() {
        // Register client tick event for input handling.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            handleScrollInput(client);
            CraneshotClient.checkKeybinds();
        });

        // Register HUD rendering for on-screen messages.
        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null && CraneshotClient.CAMERA_CONTROLLER.hasActiveMessage()) {
                String message = CraneshotClient.CAMERA_CONTROLLER.getCurrentMessage();
                int width = client.getWindow().getScaledWidth();
                context.drawTextWithShadow(
                        client.textRenderer,
                        Text.literal(message),
                        width / 2 - client.textRenderer.getWidth(message) / 2,
                        60,
                        0xFFFFFF
                );
            }
        });

        // Register world render event to update camera and handle key state changes.
        WorldRenderEvents.START.register(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            Camera camera = client.gameRenderer.getCamera();

            // Process each key binding state.
            for (int i = 0; i < CraneshotClient.cameraKeyBinds.length; i++) {
                boolean currentlyPressed = CraneshotClient.cameraKeyBinds[i].isPressed();
                boolean wasPressed = keyStates.getOrDefault(i, false);

                // Only act on changes in state.
                if (currentlyPressed != wasPressed) {
                    boolean isToggle = SlotMenuSettings.getToggleState(i);
                    // Delegate key state handling to the movement manager.
                    CraneshotClient.MOVEMENT_MANAGER.handleKeyStateChange(i, currentlyPressed, client, camera, isToggle);
                }
                keyStates.put(i, currentlyPressed);
            }
        });
    }

    /**
     * Processes mouse scroll input. When the "select movement type" key is pressed,
     * the scroll wheel cycles the current movement type. Otherwise, scroll
     * events are used to adjust the distance of the active movement.
     */
    private static void handleScrollInput(MinecraftClient client) {
        double currentTime = System.currentTimeMillis() / 1000.0;
        if (currentTime - lastScrollTime < SCROLL_COOLDOWN) {
            return;
        }

        MouseAccessor mouseAccessor = (MouseAccessor) client.mouse;
        double scrollDelta = mouseAccessor.getEventDeltaVerticalWheel();
        if (scrollDelta == 0) {
            return;
        }

        boolean scrollUp = scrollDelta > 0;

        // If the movement type selector key is pressed, cycle movement types.
        if (CraneshotClient.selectMovementType.isPressed()) {
            CraneshotClient.MOVEMENT_MANAGER.cycleMovementType(scrollUp);

            // Optionally display a message showing the active movement type.
            for (int i = 0; i < CraneshotClient.cameraKeyBinds.length; i++) {
                if (CraneshotClient.cameraKeyBinds[i].isPressed()) {
                    ICameraMovement movement = CraneshotClient.MOVEMENT_MANAGER.getMovementAt(i);
                    if (movement != null) {
                        CraneshotClient.CAMERA_CONTROLLER.showMessage(
                                "Camera " + (i + 1) + ": " + movement.getName() + " Movement"
                        );
                    }
                    break;
                }
            }
            lastScrollTime = currentTime;
            mouseAccessor.setEventDeltaVerticalWheel(0);
        }
        // Otherwise, use the scroll to adjust the distance parameter.
        else {
            for (int i = 0; i < CraneshotClient.cameraKeyBinds.length; i++) {
                if (CraneshotClient.cameraKeyBinds[i].isPressed()) {
                    CraneshotClient.MOVEMENT_MANAGER.adjustDistance(i, !scrollUp);
                    lastScrollTime = currentTime;
                    mouseAccessor.setEventDeltaVerticalWheel(0);
                    break;
                }
            }
        }
    }
}
