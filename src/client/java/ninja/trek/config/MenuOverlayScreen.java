package ninja.trek.config;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import ninja.trek.CraneshotClient;
import ninja.trek.cameramovements.AbstractMovementSettings;
import ninja.trek.cameramovements.ICameraMovement;
import ninja.trek.cameramovements.LinearMovement;
import ninja.trek.cameramovements.MovementSetting;

import java.lang.reflect.Field;
import java.util.*;

public class MenuOverlayScreen extends Screen {
    private static final Map<Integer, Set<Integer>> expandedMovements = new HashMap<>();
    private static final int MARGIN = 20;
    private static final int TAB_HEIGHT = 30;
    private static final int CONTENT_START_Y = TAB_HEIGHT - 10;
    private static final double SCROLL_SPEED = 10;
    private static boolean isMenuOpen = false;
    private int selectedTab = 0;
    private final List<SettingSlider> settingSliders = new ArrayList<>();
    private int scrollOffset = 0;
    private int maxScroll = 0;

    private int guiWidth;
    private int guiHeight;
    private int centerX;
    private int centerY;

    public MenuOverlayScreen() {
        super(Text.literal("CraneShot Settings"));
        isMenuOpen = true;
    }

    @Override
    protected void init() {
        this.guiWidth = this.width - (MARGIN * 2);
        this.guiHeight = this.height - (MARGIN * 2);
        this.centerX = MARGIN;
        this.centerY = MARGIN;

        int visibleStartY = centerY + CONTENT_START_Y;
        int visibleEndY = centerY + guiHeight;
        int height = 0;

        int tabCount = CraneshotClient.CAMERA_CONTROLLER.getMovementCount() + 1;
        int tabWidth = Math.min(100, (guiWidth - 20) / tabCount);

        // Tab buttons
        for (int i = 0; i <= CraneshotClient.CAMERA_CONTROLLER.getMovementCount(); i++) {
            int tabIndex = i;
            String tabName = (i == 0) ? "General" : "Slot " + i;
            ButtonWidget slotBtn = ButtonWidget.builder(Text.literal(tabName), button -> switchTab(tabIndex))
                    .dimensions(centerX + (i * (tabWidth + 5)), centerY, tabWidth, 20)
                    .build();
            height = slotBtn.getHeight();
            this.addDrawableChild(slotBtn);
        }

        int BUTTON_HEIGHT = height;
        int MOVEMENT_SPACING = BUTTON_HEIGHT - 5;
        int MOVEMENT_ROW_HEIGHT = BUTTON_HEIGHT + 5;
        int SETTING_HEIGHT = BUTTON_HEIGHT + 5;

        if (selectedTab > 0) {
            int slotIndex = selectedTab - 1;

            if (visibleStartY <= centerY + CONTENT_START_Y + BUTTON_HEIGHT) {
                this.addDrawableChild(ButtonWidget.builder(Text.literal("+"), button -> addMovement(slotIndex))
                        .dimensions(centerX + 10, centerY + CONTENT_START_Y, 20, BUTTON_HEIGHT)
                        .build());
                this.addDrawableChild(CheckboxWidget.builder(Text.literal("Wrap"), this.textRenderer)
                        .pos(centerX + 40, centerY + CONTENT_START_Y)
                        .checked(WrapSettings.getWrapState(slotIndex))
                        .callback((checkbox, checked) -> WrapSettings.setWrapState(slotIndex, checked))
                        .build());
            }

            List<ICameraMovement> movements = CraneshotClient.CAMERA_CONTROLLER.getAvailableMovementsForSlot(slotIndex);
            int yOffset = CONTENT_START_Y + BUTTON_HEIGHT + 10;

            for (int i = 0; i < movements.size(); i++) {
                int index = i;
                ICameraMovement movement = movements.get(i);
                int rowY = centerY + yOffset - scrollOffset;

                if (rowY >= visibleStartY - BUTTON_HEIGHT && rowY <= visibleEndY) {
                    // Control buttons now on the left
                    int controlX = centerX + 10;

                    if (i > 0) {
                        this.addDrawableChild(ButtonWidget.builder(Text.literal("↑"), button -> moveMovement(slotIndex, index, index - 1))
                                .dimensions(controlX, rowY, 20, BUTTON_HEIGHT)
                                .build());
                    }
                    controlX += 25;

                    if (i < movements.size() - 1) {
                        this.addDrawableChild(ButtonWidget.builder(Text.literal("↓"), button -> moveMovement(slotIndex, index, index + 1))
                                .dimensions(controlX, rowY, 20, BUTTON_HEIGHT)
                                .build());
                    }
                    controlX += 25;

                    if (movements.size() > 1) {
                        this.addDrawableChild(ButtonWidget.builder(Text.literal("×"), button -> deleteMovement(slotIndex, index))
                                .dimensions(controlX, rowY, 20, BUTTON_HEIGHT)
                                .build());
                    }
                    controlX += 25;

                    // Movement name button
                    int movementButtonWidth = Math.min(200, guiWidth / 3);
                    this.addDrawableChild(ButtonWidget.builder(
                                    Text.literal((isMovementExpanded(slotIndex, index) ? "▼ " : "▶ ") + movement.getName()),
                                    button -> {
                                        toggleMovementExpanded(slotIndex, index);
                                        reinitialize();
                                    })
                            .dimensions(controlX, rowY, movementButtonWidth, BUTTON_HEIGHT)
                            .build());
                }

                yOffset += MOVEMENT_ROW_HEIGHT;

                // Settings layout in multiple columns
                if (movement instanceof AbstractMovementSettings settings && isMovementExpanded(slotIndex, index)) {
                    List<Field> settingFields = new ArrayList<>();
                    for (Field field : settings.getClass().getDeclaredFields()) {
                        if (field.isAnnotationPresent(MovementSetting.class)) {
                            settingFields.add(field);
                        }
                    }

                    // Calculate number of columns and widths
                    int totalWidth = guiWidth - 40;  // Available width minus margins
                    int labelWidth = Math.min(150, totalWidth / 4);  // Original label width
                    int sliderWidth = Math.min(200, totalWidth / 2);  // Original slider width
                    int settingWidth = labelWidth + sliderWidth + 10;  // Total width for one setting
                    int columnsCount = Math.max(1, Math.min(3, (totalWidth + 20) / (settingWidth + 20)));
                    int settingsPerColumn = (int) Math.ceil(settingFields.size() / (double) columnsCount);

                    for (int fieldIndex = 0; fieldIndex < settingFields.size(); fieldIndex++) {
                        Field field = settingFields.get(fieldIndex);
                        MovementSetting annotation = field.getAnnotation(MovementSetting.class);
                        field.setAccessible(true);

                        try {
                            double value = ((Number) field.get(settings)).doubleValue();

                            // Calculate position in grid
                            int column = fieldIndex / settingsPerColumn;
                            int row = fieldIndex % settingsPerColumn;

                            int settingX = centerX + 20 + column * (settingWidth + 20);
                            int settingY = centerY + yOffset + (row * SETTING_HEIGHT) - scrollOffset;

                            if (settingY >= visibleStartY - BUTTON_HEIGHT && settingY <= visibleEndY) {
                                // Add setting label as a text-like button
                                ButtonWidget labelButton = ButtonWidget.builder(
                                                Text.literal(annotation.label()),
                                                button -> {}
                                        )
                                        .dimensions(settingX, settingY, labelWidth, BUTTON_HEIGHT)

                                        .build();

                                // Custom renderer for text-like appearance


                                this.addDrawableChild(labelButton);

                                // Add slider with original width
                                SettingSlider slider = new SettingSlider(
                                        settingX + labelWidth + 10,
                                        settingY,
                                        sliderWidth,
                                        BUTTON_HEIGHT,
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
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        }
                    }

                    // Adjust yOffset based on the height of all settings
                    yOffset += (settingsPerColumn * SETTING_HEIGHT) + MOVEMENT_SPACING;
                } else {
                    yOffset += MOVEMENT_SPACING;
                }
            }

            int contentHeight = yOffset - (CONTENT_START_Y + BUTTON_HEIGHT + 10);
            int visibleHeight = guiHeight - CONTENT_START_Y - 10;
            maxScroll = Math.max(0, contentHeight - visibleHeight);
        }
    }

    // ... [rest of the methods remain unchanged]



    @Override
    public void resize(MinecraftClient client, int width, int height) {
        super.resize(client, width, height);
        this.scrollOffset = 0; // Reset scroll position on resize
        this.reinitialize();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Draw semi-transparent background for entire screen
        context.fill(0, 0, this.width, this.height, 0x80000000);

        // Draw content area
        context.fill(
                centerX,
                centerY + CONTENT_START_Y,
                centerX + guiWidth,
                centerY + guiHeight,
                0xC0000000
        );

        // Render all widgets
        super.render(context, mouseX, mouseY, delta);

        // Draw scroll indicators if needed
        if (maxScroll > 0) {
            if (scrollOffset > 0) {
                context.drawCenteredTextWithShadow(
                        this.textRenderer,
                        Text.literal("▲"),
                        centerX + guiWidth - 15,
                        centerY + CONTENT_START_Y,
                        0xFFFFFF
                );
            }
            if (scrollOffset < maxScroll) {
                context.drawCenteredTextWithShadow(
                        this.textRenderer,
                        Text.literal("▼"),
                        centerX + guiWidth - 15,
                        centerY + guiHeight - 15,
                        0xFFFFFF
                );
            }
        }
    }

    // Keep existing helper methods
    private boolean isMovementExpanded(int slotIndex, int movementIndex) {
        return expandedMovements.computeIfAbsent(slotIndex, k -> new HashSet<>()).contains(movementIndex);
    }

    private void toggleMovementExpanded(int slotIndex, int movementIndex) {
        Set<Integer> expanded = expandedMovements.computeIfAbsent(slotIndex, k -> new HashSet<>());
        if (!expanded.remove(movementIndex)) {
            expanded.add(movementIndex);
        }
    }

    private void addMovement(int slotIndex) {
        CraneshotClient.CAMERA_CONTROLLER.addMovement(slotIndex, new LinearMovement());
        reinitialize();
    }

    private void deleteMovement(int slotIndex, int movementIndex) {
        CraneshotClient.CAMERA_CONTROLLER.removeMovement(slotIndex, movementIndex);
        reinitialize();
    }

    private void moveMovement(int slotIndex, int fromIndex, int toIndex) {
        CraneshotClient.CAMERA_CONTROLLER.swapMovements(slotIndex, fromIndex, toIndex);
        reinitialize();
    }

    private void reinitialize() {
        this.clearChildren();
        this.init();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
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