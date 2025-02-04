package ninja.trek;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.text.Text;
import ninja.trek.mixin.client.MouseAccessor;
public class CraneShotEventHandler {
    private static boolean[] wasPressed = new boolean[3];
    private static double lastScrollTime = 0;
    private static final double SCROLL_COOLDOWN = 0.1;
    private static final String[] MOVEMENT_NAMES = {"Linear", "Circular", "Bezier"};
    private static String currentMessage = "";
    private static long messageTimer = 0;
    private static final long MESSAGE_DURATION = 2000;
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            handleScrollInput(client);
            updateMessageTimer();
        });
        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            if (!currentMessage.isEmpty() && System.currentTimeMillis() < messageTimer) {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null) {
                    int width = client.getWindow().getScaledWidth();
                    context.drawTextWithShadow(client.textRenderer, Text.literal(currentMessage),
                            width / 2 - client.textRenderer.getWidth(currentMessage) / 2,
                            60, 0xFFFFFF);
                }
            }
        });
        WorldRenderEvents.START.register((context) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            Camera camera = client.gameRenderer.getCamera();
            boolean anyPressed = false;
            for (int i = 0; i < 3; i++) {
                if (CraneshotClient.cameraKeyBinds[i].isPressed()) {
                    if (!wasPressed[i]) {
                        CraneshotClient.CAMERA_CONTROLLER.startTransition(client, camera, i);
                    }
                    wasPressed[i] = true;
                    anyPressed = true;
                } else {
                    wasPressed[i] = false;
                }
            }
            if (!anyPressed) {
                CraneshotClient.CAMERA_CONTROLLER.reset(client, camera);

            }
            if (client.player != null) {
                CraneshotClient.CAMERA_CONTROLLER.updateTransition(client, camera);
            }
        });
    }
    private static void handleScrollInput(MinecraftClient client) {
        double currentTime = System.currentTimeMillis() / 1000.0;
        if (currentTime - lastScrollTime < SCROLL_COOLDOWN) return;
        double scrollDelta = ((MouseAccessor)client.mouse).getEventDeltaVerticalWheel();
        if (scrollDelta == 0) return;
        boolean scrollUp = scrollDelta > 0;
        if (CraneshotClient.selectMovementType.isPressed()) {
            CraneshotClient.CAMERA_CONTROLLER.cycleMovementType(scrollUp);
            showMovementTypeMessage();
            lastScrollTime = currentTime;
            ((MouseAccessor)client.mouse).setEventDeltaVerticalWheel(0);
        } else {
            for (int i = 0; i < CraneshotClient.cameraKeyBinds.length; i++) {
                if (CraneshotClient.cameraKeyBinds[i].isPressed()) {
                    CraneshotClient.CAMERA_CONTROLLER.adjustDistance(i, scrollUp);
                    lastScrollTime = currentTime;
                    ((MouseAccessor)client.mouse).setEventDeltaVerticalWheel(0);
                    break;
                }
            }
        }
    }
    private static void showMovementTypeMessage() {
        int currentHotkey = -1;
        for (int i = 0; i < CraneshotClient.cameraKeyBinds.length; i++) {
            if (CraneshotClient.cameraKeyBinds[i].isPressed()) {
                currentHotkey = i;
                break;
            }
        }
        if (currentHotkey != -1) {
            String movementType = MOVEMENT_NAMES[CraneshotClient.CAMERA_CONTROLLER.getCurrentType(currentHotkey)];
            currentMessage = String.format("Camera %d: %s Movement", currentHotkey + 1, movementType);
            messageTimer = System.currentTimeMillis() + MESSAGE_DURATION;
        }
    }
    private static void updateMessageTimer() {
        if (System.currentTimeMillis() >= messageTimer) {
            currentMessage = "";
        }
    }
}