package ninja.trek.config;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ScrollableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import ninja.trek.CraneshotClient;
import ninja.trek.cameramovements.AbstractMovementSettings;
import ninja.trek.cameramovements.ICameraMovement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MenuOverlayScreen extends Screen {
    private static final int GUI_WIDTH = 220;
    private static final int GUI_HEIGHT = 180;
    private static boolean isMenuOpen = false;
    private int selectedTab = 0;
    private final Map<String, TextFieldWidget> settingFields = new HashMap<>();
    private ScrollableSettingsPanel scrollPanel;

    public MenuOverlayScreen() {
        super(Text.literal("Overlay Menu"));
        isMenuOpen = true;
    }

    @Override
    protected void init() {
        int centerX = (this.width - GUI_WIDTH) / 2;
        int centerY = (this.height - GUI_HEIGHT) / 2;

        settingFields.clear();

        // Tab buttons
        for (int i = 0; i <= CraneshotClient.CAMERA_CONTROLLER.getMovementCount(); i++) {
            int tabIndex = i;
            String tabName = (i == 0) ? "General" : "Slot " + i;
            this.addDrawableChild(ButtonWidget.builder(Text.literal(tabName), button -> switchTab(tabIndex))
                    .dimensions(centerX + (i * 55), centerY - 30, 50, 20)
                    .build());
        }

        // Close button (now accessible for dynamic height reference)
        // Buttons aligned at the bottom
        int buttonY = this.height - 20; // Position at the very bottom

        ButtonWidget closeButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Close"), button -> closeMenu())
                .dimensions(centerX + 10, buttonY, 100, 20) // Left-side button
                .build());

        ButtonWidget saveButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Save Settings"), button -> saveCurrentSettings())
                .dimensions(centerX + 120, buttonY, 100, 20) // Right-side button, next to Close

                .build());


        int buttonHeight = closeButton.getHeight(); // Get height dynamically
        int panelBottomPadding = buttonHeight + 10; // Extra space below the panel

        // Dynamically adjust panel size based on UI scaling
        int availableWidth = this.width - 20;
        int availableHeight = this.height - (centerY + 20) - panelBottomPadding;

        scrollPanel = new ScrollableSettingsPanel(10, centerY + 20, availableWidth, availableHeight);
        this.addDrawableChild(scrollPanel);

        updateContent();
    }

    private void saveCurrentSettings() {
        if (selectedTab > 0) {
            int slotIndex = selectedTab - 1;
            ICameraMovement movement = CraneshotClient.CAMERA_CONTROLLER.getMovementAt(slotIndex);
            if (movement instanceof AbstractMovementSettings settings) {
                saveSettings(settings);
            }
        }
    }


    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == CraneshotClient.toggleMenuKey.getDefaultKey().getCode()) closeMenu();
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void updateContent() {
        scrollPanel.clear();

        if (selectedTab > 0) {
            int slotIndex = selectedTab - 1;
            ICameraMovement movement = CraneshotClient.CAMERA_CONTROLLER.getMovementAt(slotIndex);
            if (movement instanceof AbstractMovementSettings settings) {
                for (Map.Entry<String, Object> entry : settings.getSettings().entrySet()) {
                    TextFieldWidget field = new TextFieldWidget(this.textRenderer, 10, 0, 160, 20, Text.literal(entry.getKey()));
                    field.setText(entry.getValue().toString());
                    settingFields.put(entry.getKey(), field);
                    scrollPanel.addEntry(field);
                }


            }
        }
    }

    private void saveSettings(AbstractMovementSettings settings) {
        for (Map.Entry<String, TextFieldWidget> entry : settingFields.entrySet()) {
            try {
                double value = Double.parseDouble(entry.getValue().getText());
                settings.updateSetting(entry.getKey(), value);
            } catch (NumberFormatException ignored) {}
        }
    }

    private void switchTab(int index) {
        selectedTab = index;
        clearChildren();
        init();
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        super.resize(client, width, height);

        int buttonHeight = 20; // Default button height
        if (!this.children().isEmpty()) {
            for (var widget : this.children()) {
                if (widget instanceof ButtonWidget button && button.getMessage().getString().equals("Close")) {
                    buttonHeight = button.getHeight(); // Get the real height
                    break;
                }
            }
        }

        int panelBottomPadding = buttonHeight + 10;
        int availableHeight = height - (this.height / 2 - GUI_HEIGHT / 2 + 20) - panelBottomPadding;
        int availableWidth = width - 20;

        scrollPanel.setWidth(availableWidth);
        scrollPanel.setHeight(availableHeight);
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

    private class ScrollableSettingsPanel extends ScrollableWidget {
        private final List<TextFieldWidget> fields = new ArrayList<>();

        public ScrollableSettingsPanel(int x, int y, int width, int height) {
            super(x, y, width, height, Text.literal(""));
            this.setWidth(width);
            this.setHeight(height);
        }

        @Override
        protected int getContentsHeight() {
            return Math.max(fields.size() * 25 + 10, this.height - 20);
        }

        @Override
        protected double getDeltaYPerScroll() {
            return 10.0; // Scroll speed
        }

        @Override
        protected void renderContents(DrawContext context, int mouseX, int mouseY, float delta) {
            int yOffset = 5;
            for (TextFieldWidget field : fields) {
                int fieldY = this.getY() + yOffset - (int) getScrollY();
                field.setY(fieldY);
                field.render(context, mouseX, mouseY, delta);
                yOffset += 25;
            }
        }

        public void addEntry(TextFieldWidget field) {
            fields.add(field);
        }

        public void clear() {
            fields.clear();
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            for (TextFieldWidget field : fields) {
                if (field.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            // No narration needed
        }
    }



}
