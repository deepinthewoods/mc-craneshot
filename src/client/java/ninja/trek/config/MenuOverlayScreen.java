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
    private static boolean isMenuOpen = false;
    private int selectedTab = 0;
    private final List<SettingSlider> settingSliders = new ArrayList<>();
    private int scrollOffset = 0;
    private static final int SCROLL_AMOUNT = 20;

    public MenuOverlayScreen() {
        super(Text.literal("CraneShot Settings"));
        isMenuOpen = true;
    }

    @Override
    protected void init() {
        int centerX = (this.width - GUI_WIDTH) / 2;
        int centerY = (this.height - GUI_HEIGHT) / 2;

        // Tab buttons
        for (int i = 0; i <= CraneshotClient.CAMERA_CONTROLLER.getMovementCount(); i++) {
            int tabIndex = i;
            String tabName = (i == 0) ? "General" : "Slot " + i;
            this.addDrawableChild(ButtonWidget.builder(Text.literal(tabName), button -> switchTab(tabIndex))
                    .dimensions(centerX + (i * 70), centerY - 30, 65, 20)
                    .build());
        }

        // Create sliders for the current tab's settings
        updateSliders(centerX, centerY);

        // Add scroll buttons if content exceeds view
        this.addDrawableChild(ButtonWidget.builder(Text.literal("▲"), button -> scroll(-SCROLL_AMOUNT))
                .dimensions(this.width - 30, centerY, 20, 20)
                .build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("▼"), button -> scroll(SCROLL_AMOUNT))
                .dimensions(this.width - 30, centerY + GUI_HEIGHT - 40, 20, 20)
                .build());

//        // Close button
//        this.addDrawableChild(ButtonWidget.builder(Text.literal("Save & Close"), button -> closeMenu())
//                .dimensions(centerX + GUI_WIDTH - 100, centerY + GUI_HEIGHT - 30, 90, 20)
//                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Fill with a semi-transparent background without blur
//        context.fill(0, 0, this.width, this.height, 0xC0000000);

        int centerX = (this.width - GUI_WIDTH) / 2;
        int centerY = (this.height - GUI_HEIGHT) / 2;

        // Draw main panel background
//        context.fill(
//                centerX,
//                centerY - 40,
//                centerX + GUI_WIDTH,
//                centerY + GUI_HEIGHT,
//                0xE0000000
//        );

        // Draw panel border
//        context.drawBorder(
//                centerX,
//                centerY - 40,
//                GUI_WIDTH,
//                GUI_HEIGHT + 40,
//                0xFFFFFFFF
//        );



        super.render(context, mouseX, mouseY, delta);
        // Draw slider labels
        for (SettingSlider slider : settingSliders) {
            String label = slider.getLabel().getString();
            int textX = centerX + 10;
            int textY = slider.getY() + 5;

            // Draw text with shadow for better visibility
            context.drawTextWithShadow(
                    this.textRenderer,
                    label,
                    textX,
                    textY,
                    0xFFFFFF
            );

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
                            SettingSlider slider = new SettingSlider(
                                    centerX + 110,
                                    centerY + 20 + yOffset - scrollOffset,
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
                            yOffset += 30;
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    private void scroll(int amount) {
        int maxScroll = Math.max(0, (settingSliders.size() * 30) - GUI_HEIGHT + 60);
        scrollOffset = Math.max(0, Math.min(scrollOffset + amount, maxScroll));
        clearChildren();
        init();
    }

    private static class SettingSlider extends SliderWidget {
        private final double min;
        private final double max;
        private final String fieldName;
        private final AbstractMovementSettings settings;
        private final Text label;

        public SettingSlider(int x, int y, int width, int height, Text label,
                             double min, double max, double value,
                             String fieldName, AbstractMovementSettings settings) {
            super(x, y, width, height, label, (value - min) / (max - min));
            this.min = min;
            this.max = max;
            this.fieldName = fieldName;
            this.settings = settings;
            this.label = label;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal(String.format("%.2f", getValue())));
        }

        @Override
        protected void applyValue() {
            double value = min + (max - min) * this.value;
            settings.updateSetting(fieldName, value);
        }

        public Text getLabel() {
            return label;
        }

        private double getValue() {
            return min + (max - min) * this.value;
        }
    }

    private void switchTab(int index) {
        selectedTab = index;
        scrollOffset = 0;
        clearChildren();
        init();
    }

    private void closeMenu() {
        isMenuOpen = false;
        MinecraftClient.getInstance().setScreen(null);
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