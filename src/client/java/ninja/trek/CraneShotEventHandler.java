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
                }
                keyStates.put(i, currentlyPressed);
            }
        });
    }

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

        // Only handle scroll events when select movement type key is NOT pressed
        if (!CraneshotClient.selectMovementType.isPressed()) {
            // Check if any camera key is pressed
            for (int i = 0; i < CraneshotClient.cameraKeyBinds.length; i++) {
                if (CraneshotClient.cameraKeyBinds[i].isPressed()) {
                    CraneshotClient.MOVEMENT_MANAGER.handleMouseScroll(i, scrollUp);
                    lastScrollTime = currentTime;
                    mouseAccessor.setEventDeltaVerticalWheel(0);
                    return;
                }
            }
        }
    }
}