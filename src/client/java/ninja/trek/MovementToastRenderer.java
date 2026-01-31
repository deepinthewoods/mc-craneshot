package ninja.trek;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import ninja.trek.cameramovements.ICameraMovement;
import java.util.List;

public class MovementToastRenderer {
    private static final int PADDING = 4;
    private static final int MARGIN_BOTTOM = 40;
    private static final int MARGIN_LEFT = 10;
    private static final int LINE_HEIGHT = 12;
    private static final int WHITE_COLOR = 0xFFFFFFFF;
    private static final int GRAY_COLOR = 0xFF808080;
    private static final float HOLD_DURATION = 1.0f;
    private static final float FADE_DURATION = 0.5f;

    private static Long startTime = null;
    private static Integer currentToastSlot = null;
    private static boolean shouldRender = false;

    public static void showToast(int slotIndex) {
        currentToastSlot = slotIndex;
        startTime = System.currentTimeMillis();
        shouldRender = true;
    }

    public static void register() {
        HudRenderCallback.EVENT.register((GuiGraphics context, DeltaTracker tickDelta) -> {
            // Early exit if we shouldn't render
            if (!shouldRender || startTime == null || currentToastSlot == null) {
                return;
            }

            Minecraft client = Minecraft.getInstance();
            if (client.player == null) return;

            long currentTime = System.currentTimeMillis();
            float timeSinceStart = (currentTime - startTime) / 1000f;

            // Check if we should stop rendering entirely
            if (timeSinceStart >= HOLD_DURATION + FADE_DURATION) {
                shouldRender = false;
                startTime = null;
                currentToastSlot = null;
                return;
            }

            float opacity = 1.0f;
            if (timeSinceStart > HOLD_DURATION) {
                opacity = 1.0f - ((timeSinceStart - HOLD_DURATION) / FADE_DURATION);
                opacity = Math.max(0.0f, opacity);
                if (opacity <= 0) {
                    shouldRender = false;
                    startTime = null;
                    currentToastSlot = null;
                    return;
                }
            }

            // Get movements for the current slot
            List<ICameraMovement> movements = CraneshotClient.MOVEMENT_MANAGER.getAvailableMovementsForSlot(currentToastSlot);
            if (movements.isEmpty()) return;

            int selectedIndex = CraneshotClient.MOVEMENT_MANAGER.getCurrentTypeForSlot(currentToastSlot);

            // Calculate dimensions
            Font textRenderer = client.font;
            int maxWidth = 0;
            for (ICameraMovement movement : movements) {
                maxWidth = Math.max(maxWidth, textRenderer.width(movement.getName()));
            }

            int totalHeight = movements.size() * LINE_HEIGHT;
            int width = maxWidth + (PADDING * 2);

            // Calculate position
            int screenHeight = client.getWindow().getGuiScaledHeight();
            int x = MARGIN_LEFT;
            int y = screenHeight - MARGIN_BOTTOM - totalHeight;

            // Draw movement names
            for (int i = 0; i < movements.size(); i++) {
                ICameraMovement movement = movements.get(i);
                int textY = y + (i * LINE_HEIGHT);

                int baseColor = (i == selectedIndex) ? WHITE_COLOR : GRAY_COLOR;
                int color = applyOpacity(baseColor, opacity);

                context.drawString(
                        textRenderer,
                        Component.literal(movement.getName()),
                        x + PADDING,
                        textY + (LINE_HEIGHT - textRenderer.lineHeight) / 2,
                        color
                );
            }
        });
    }

    private static int applyOpacity(int color, float opacity) {
        int alpha = (int) (((color >> 24) & 0xFF) * opacity);
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}