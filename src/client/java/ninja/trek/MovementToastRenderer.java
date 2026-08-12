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

    // Simple text toast
    private static String textToastMessage = null;
    private static Long textToastStartTime = null;
    private static boolean textToastShouldRender = false;

    public static void showToast(int slotIndex) {
        currentToastSlot = slotIndex;
        startTime = System.currentTimeMillis();
        shouldRender = true;
    }

    public static void showTextToast(String message) {
        textToastMessage = message;
        textToastStartTime = System.currentTimeMillis();
        textToastShouldRender = true;
    }

    public static void register() {
        HudRenderCallback.EVENT.register((GuiGraphics context, DeltaTracker tickDelta) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) return;

            long currentTime = System.currentTimeMillis();

            // Render movement slot toast
            if (shouldRender && startTime != null && currentToastSlot != null) {
                float timeSinceStart = (currentTime - startTime) / 1000f;

                if (timeSinceStart >= HOLD_DURATION + FADE_DURATION) {
                    shouldRender = false;
                    startTime = null;
                    currentToastSlot = null;
                } else {
                    float opacity = 1.0f;
                    if (timeSinceStart > HOLD_DURATION) {
                        opacity = 1.0f - ((timeSinceStart - HOLD_DURATION) / FADE_DURATION);
                        opacity = Math.max(0.0f, opacity);
                    }

                    if (opacity > 0) {
                        List<ICameraMovement> movements = CraneshotClient.MOVEMENT_MANAGER.getAvailableMovementsForSlot(currentToastSlot);
                        if (!movements.isEmpty()) {
                            int selectedIndex = CraneshotClient.MOVEMENT_MANAGER.getCurrentTypeForSlot(currentToastSlot);

                            Font textRenderer = client.font;
                            int maxWidth = 0;
                            for (ICameraMovement movement : movements) {
                                maxWidth = Math.max(maxWidth, textRenderer.width(movement.getName()));
                            }

                            int totalHeight = movements.size() * LINE_HEIGHT;
                            int screenHeight = client.getWindow().getGuiScaledHeight();
                            int x = MARGIN_LEFT;
                            int y = screenHeight - MARGIN_BOTTOM - totalHeight;

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
                        }
                    }
                }
            }

            // Render simple text toast
            if (textToastShouldRender && textToastStartTime != null && textToastMessage != null) {
                float textTimeSinceStart = (currentTime - textToastStartTime) / 1000f;
                if (textTimeSinceStart >= HOLD_DURATION + FADE_DURATION) {
                    textToastShouldRender = false;
                    textToastStartTime = null;
                    textToastMessage = null;
                } else {
                    float textOpacity = 1.0f;
                    if (textTimeSinceStart > HOLD_DURATION) {
                        textOpacity = 1.0f - ((textTimeSinceStart - HOLD_DURATION) / FADE_DURATION);
                        textOpacity = Math.max(0.0f, textOpacity);
                    }
                    if (textOpacity > 0) {
                        Font font = client.font;
                        int textColor = applyOpacity(WHITE_COLOR, textOpacity);
                        int textX = MARGIN_LEFT + PADDING;
                        int textYPos = client.getWindow().getGuiScaledHeight() - MARGIN_BOTTOM;
                        context.drawString(font, Component.literal(textToastMessage), textX, textYPos, textColor);
                    }
                }
            }
        });
    }

    private static int applyOpacity(int color, float opacity) {
        int alpha = (int) (((color >> 24) & 0xFF) * opacity);
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}
