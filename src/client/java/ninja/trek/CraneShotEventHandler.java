package ninja.trek;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.text.Text;
import ninja.trek.cameramovements.ICameraMovement;
import ninja.trek.mixin.client.MouseAccessor;

import java.util.HashMap;
import java.util.Map;

public class CraneShotEventHandler {
    private static final double SCROLL_COOLDOWN = 0.1;
    private static double lastScrollTime = 0;
    private static String currentMessage = "";
    private static long messageTimer = 0;
    private static final long MESSAGE_DURATION = 2000;
    private static final Map<Integer, Boolean> keyStates = new HashMap<>();

    public static void register() {
        // Register client tick event for input handling
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            handleScrollInput(client);
            updateMessageTimer();
            CraneshotClient.checkKeybinds();
        });

        // Register HUD rendering for messages
        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            if (!currentMessage.isEmpty() && System.currentTimeMillis() < messageTimer) {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null) {
                    int width = client.getWindow().getScaledWidth();
                    context.drawTextWithShadow(
                            client.textRenderer,
                            Text.literal(currentMessage),
                            width / 2 - client.textRenderer.getWidth(currentMessage) / 2,
                            60,
                            0xFFFFFF
                    );
                }
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

                if (currentlyPressed) {
                    if (!wasPressed) {
                        // Key just pressed - start movement
                        CraneshotClient.CAMERA_CONTROLLER.startTransition(client, camera, i);
                    }
                    anyPressed = true;
                }

                keyStates.put(i, currentlyPressed);
            }

            // If no keys are pressed, reset the camera
            if (!anyPressed) {
                CraneshotClient.CAMERA_CONTROLLER.queueFinish(client, camera);
            }

            // Update camera movements
            if (client.player != null) {
                //CraneshotClient.CAMERA_CONTROLLER.tick(client, camera);
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
                    ICameraMovement movement = CraneshotClient.CAMERA_CONTROLLER.getMovementAt(i);
                    if (movement != null) {
                        showMovementTypeMessage(String.format(
                                "Camera %d: %s Movement",
                                i + 1,
                                movement.getName()
                        ));
                    }
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

    private static void showMovementTypeMessage(String message) {
        currentMessage = message;
        messageTimer = System.currentTimeMillis() + MESSAGE_DURATION;
    }

    private static void updateMessageTimer() {
        if (System.currentTimeMillis() >= messageTimer) {
            currentMessage = "";
        }
    }
}