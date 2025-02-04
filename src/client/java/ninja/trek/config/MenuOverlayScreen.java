package ninja.trek.config;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import ninja.trek.CraneshotClient;
import ninja.trek.cameramovements.AbstractMovementSettings;
import ninja.trek.cameramovements.ICameraMovement;
import ninja.trek.cameramovements.MovementSetting;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class MenuOverlayScreen extends Screen {
    private static final int GUI_WIDTH = 280;
    private static final int GUI_HEIGHT = 180;
    private static final int TAB_HEIGHT = 30;
    private static final int CONTENT_START_Y = TAB_HEIGHT + 10; // Space for tabs + padding
    private static final int SCROLL_SPEED = 15;

    private static boolean isMenuOpen = false;
    private int selectedTab = 0;
    private final List<SettingSlider> settingSliders = new ArrayList<>();
    private int scrollOffset = 0;
    private int maxScroll = 0;

    public MenuOverlayScreen() {
        super(Text.literal("CraneShot Settings"));
        isMenuOpen = true;
    }

    @Override
    protected void init() {
        int centerX = (this.width - GUI_WIDTH) / 2;
        int centerY = (this.height - GUI_HEIGHT) / 2;

        // Tab buttons at fixed position
        for (int i = 0; i <= CraneshotClient.CAMERA_CONTROLLER.getMovementCount(); i++) {
            int tabIndex = i;
            String tabName = (i == 0) ? "General" : "Slot " + i;
            this.addDrawableChild(ButtonWidget.builder(Text.literal(tabName), button -> switchTab(tabIndex))
                    .dimensions(centerX + (i * 70), centerY, 65, 20)
                    .build());
        }

        // Create sliders for the current tab's settings
        updateSliders(centerX, centerY + CONTENT_START_Y);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Handle mouse wheel scrolling
        if (verticalAmount != 0) {
            scroll((int)(-verticalAmount * SCROLL_SPEED));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == CraneshotClient.toggleMenuKey.getDefaultKey().getCode()) toggleMenu();
        return true;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int centerX = (this.width - GUI_WIDTH) / 2;
        int centerY = (this.height - GUI_HEIGHT) / 2;

        // Draw semi-transparent background
        context.fill(centerX, centerY, centerX + GUI_WIDTH, centerY + GUI_HEIGHT, 0x80000000);

        // Draw content area
        context.fill(
                centerX,
                centerY + CONTENT_START_Y,
                centerX + GUI_WIDTH,
                centerY + GUI_HEIGHT,
                0xC0000000
        );

        // Render all widgets
        super.render(context, mouseX, mouseY, delta);

        // Draw slider labels
        int visibleStartY = centerY + CONTENT_START_Y;
        int visibleEndY = centerY + GUI_HEIGHT;
        for (SettingSlider slider : settingSliders) {
            int sliderY = slider.getY();
            if (sliderY + 20 >= visibleStartY && sliderY <= visibleEndY) {
                String label = slider.getLabel().getString();
                context.drawTextWithShadow(
                        this.textRenderer,
                        label,
                        centerX + 10,
                        sliderY + 5,
                        0xFFFFFF
                );
            }
        }

        // Draw scroll indicators if needed
        if (maxScroll > 0) {
            if (scrollOffset > 0) {
                context.drawCenteredTextWithShadow(
                        this.textRenderer,
                        Text.literal("▲"),
                        centerX + GUI_WIDTH - 15,
                        centerY + CONTENT_START_Y,
                        0xFFFFFF
                );
            }
            if (scrollOffset < maxScroll) {
                context.drawCenteredTextWithShadow(
                        this.textRenderer,
                        Text.literal("▼"),
                        centerX + GUI_WIDTH - 15,
                        centerY + GUI_HEIGHT - 15,
                        0xFFFFFF
                );
            }
        }
    }

    private void updateSliders(int centerX, int centerY) {
        settingSliders.clear();
        if (selectedTab > 0) {
            int slotIndex = selectedTab - 1;
            ICameraMovement movement = CraneshotClient.CAMERA_CONTROLLER.getMovementAt(slotIndex);
            if (movement instanceof AbstractMovementSettings settings) {
                int yOffset = 0;
                for (Field field : settings.getClass().getDeclaredFields()) {
                    if (field.isAnnotationPresent(MovementSetting.class)) {
                        MovementSetting annotation = field.getAnnotation(MovementSetting.class);
                        field.setAccessible(true);
                        try {
                            double value = ((Number) field.get(settings)).doubleValue();
                            int sliderY = centerY + yOffset - scrollOffset;
                            int visibleStartY = (this.height - GUI_HEIGHT) / 2 + CONTENT_START_Y;
                            int visibleEndY = (this.height + GUI_HEIGHT) / 2;

                            // Only create and add visible sliders
                            if (sliderY >= visibleStartY - 20 && sliderY <= visibleEndY) {
                                SettingSlider slider = new SettingSlider(
                                        centerX + 110,
                                        sliderY,
                                        150,
                                        20,
                                        Text.literal(annotation.label()),
                                        annotation.min(),
                                        annotation.max(),
                                        value,
                                        field.getName(),
                                        settings
                                );
                                settingSliders.add(slider);
                                this.addDrawableChild(slider);
                            }
                            yOffset += 30;
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        }
                    }
                }
                // Calculate max scroll based on content height
                int contentHeight = yOffset;
                int visibleHeight = GUI_HEIGHT - CONTENT_START_Y - 10;
                maxScroll = Math.max(0, contentHeight - visibleHeight);
            }
        }
    }

    private void scroll(int amount) {
        if (maxScroll > 0) {
            scrollOffset = Math.max(0, Math.min(scrollOffset + amount, maxScroll));
            clearChildren();
            init();
        }
    }



    private void switchTab(int index) {
        selectedTab = index;
        scrollOffset = 0;
        clearChildren();
        init();
    }



    public static void toggleMenu() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (isMenuOpen) {
            client.setScreen(null);
            isMenuOpen = false;
        } else {
            client.setScreen(new MenuOverlayScreen());
            isMenuOpen = true;
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}