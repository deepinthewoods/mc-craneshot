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

public class CraneShotEventHandler {
    private static final double SCROLL_COOLDOWN = 0.1;
    private static double lastScrollTime = 0;
    private static final Map<Integer, Boolean> keyStates = new HashMap<>();
    private static final Map<Integer, Boolean> toggledStates = new HashMap<>();

    public static void register() {
        // Register client tick event for input handling
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            handleScrollInput(client);
            CraneshotClient.checkKeybinds();
        });

        // Register HUD rendering for messages
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

        // Register world render event for camera updates
        WorldRenderEvents.START.register(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            Camera camera = client.gameRenderer.getCamera();

            // Handle camera movement key states
            boolean anyPressed = false;
            for (int i = 0; i < CraneshotClient.cameraKeyBinds.length; i++) {
                boolean currentlyPressed = CraneshotClient.cameraKeyBinds[i].isPressed();
                boolean wasPressed = keyStates.getOrDefault(i, false);
                boolean isToggled = toggledStates.getOrDefault(i, false);
                boolean isToggleMode = SlotMenuSettings.getToggleState(i);

                if (isToggleMode) {
                    // Handle toggle mode
                    if (currentlyPressed && !wasPressed) {
                        // Key just pressed - toggle the state
                        isToggled = !isToggled;
                        toggledStates.put(i, isToggled);
                        if (isToggled) {
                            CraneshotClient.CAMERA_CONTROLLER.startTransition(client, camera, i);
                        }
                    }
                    if (isToggled) {
                        anyPressed = true;
                    }
                } else {
                    // Handle momentary mode (original behavior)
                    if (currentlyPressed) {
                        if (!wasPressed) {
                            // Key just pressed - start movement
                            CraneshotClient.CAMERA_CONTROLLER.startTransition(client, camera, i);
                        }
                        anyPressed = true;
                    }
                }
                keyStates.put(i, currentlyPressed);
            }

            // If no keys are pressed or toggled, reset the camera
            if (!anyPressed) {
                CraneshotClient.CAMERA_CONTROLLER.queueFinish(client, camera);
                // Clear toggle states when resetting
                toggledStates.clear();
            }
        });
    }

    private static void handleScrollInput(MinecraftClient client) {
        double currentTime = System.currentTimeMillis() / 1000.0;
        if (currentTime - lastScrollTime < SCROLL_COOLDOWN) {
            return;
        }

        MouseAccessor mouseAccessor = (MouseAccessor)client.mouse;
        double scrollDelta = mouseAccessor.getEventDeltaVerticalWheel();
        if (scrollDelta == 0) {
            return;
        }

        boolean scrollUp = scrollDelta > 0;

        // Handle movement type cycling
        if (CraneshotClient.selectMovementType.isPressed()) {
            CraneshotClient.CAMERA_CONTROLLER.cycleMovementType(scrollUp);

            // Find the active movement to display its type
            for (int i = 0; i < CraneshotClient.cameraKeyBinds.length; i++) {
                if (CraneshotClient.cameraKeyBinds[i].isPressed()) {
                    CraneshotClient.CAMERA_CONTROLLER.showMovementTypeMessage(i);
                    break;
                }
            }

            lastScrollTime = currentTime;
            mouseAccessor.setEventDeltaVerticalWheel(0);
        }
        // Handle distance adjustment
        else {
            for (int i = 0; i < CraneshotClient.cameraKeyBinds.length; i++) {
                if (CraneshotClient.cameraKeyBinds[i].isPressed()) {
                    CraneshotClient.CAMERA_CONTROLLER.adjustDistance(i, !scrollUp);
                    lastScrollTime = currentTime;
                    mouseAccessor.setEventDeltaVerticalWheel(0);
                    break;
                }
            }
        }
    }
}