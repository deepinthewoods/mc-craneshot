package ninja.trek.config;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.text.Text;
import ninja.trek.CameraMovementRegistry;
import ninja.trek.CraneshotClient;
import ninja.trek.cameramovements.ICameraMovement;
import ninja.trek.cameramovements.movements.EasingMovement;

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
    private int selectedMovementTypeIndex = 0;

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
                // Add button group
                int addButtonWidth = 60;
                int typeButtonWidth = 120;
                int spacing = 10;

                // Add movement button
                this.addDrawableChild(ButtonWidget.builder(Text.literal("Add"), button -> addMovement(slotIndex))
                        .dimensions(centerX + 10, centerY + CONTENT_START_Y, addButtonWidth, BUTTON_HEIGHT)
                        .build());

                // Get current movement type for display
                List<CameraMovementRegistry.MovementInfo> movements = CameraMovementRegistry.getAllMovements();
                String currentTypeName = movements.isEmpty() ? "None" :
                        movements.get(selectedMovementTypeIndex).getName();

                // Movement type selector button
                this.addDrawableChild(ButtonWidget.builder(
                                Text.literal("Type: " + currentTypeName),
                                button -> cycleMovementType())
                        .dimensions(centerX + addButtonWidth + spacing + 10, centerY + CONTENT_START_Y,
                                typeButtonWidth, BUTTON_HEIGHT)
                        .build());

                // Wrap checkbox (moved to the right)
                this.addDrawableChild(CheckboxWidget.builder(Text.literal("Wrap"), this.textRenderer)
                        .pos(centerX + addButtonWidth + typeButtonWidth + spacing + 20, centerY + CONTENT_START_Y)
                        .checked(WrapSettings.getWrapState(slotIndex))
                        .callback((checkbox, checked) -> WrapSettings.setWrapState(slotIndex, checked))
                        .build());
            }

            // Rest of the menu implementation...
            List<ICameraMovement> movements = CraneshotClient.CAMERA_CONTROLLER.getAvailableMovementsForSlot(slotIndex);
            int yOffset = CONTENT_START_Y + BUTTON_HEIGHT + 10;
            for (int i = 0; i < movements.size(); i++) {
                // Existing movement list rendering code...
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
                    // Existing settings rendering code...
                    List<Field> settingFields = new ArrayList<>();
                    for (Field field : settings.getClass().getDeclaredFields()) {
                        if (field.isAnnotationPresent(MovementSetting.class)) {
                            settingFields.add(field);
                        }
                    }

                    int totalWidth = guiWidth - 40;
                    int labelWidth = Math.min(150, totalWidth / 4);
                    int sliderWidth = Math.min(200, totalWidth / 2);
                    int settingWidth = labelWidth + sliderWidth + 10;
                    int columnsCount = Math.max(1, Math.min(3, (totalWidth + 20) / (settingWidth + 20)));
                    int settingsPerColumn = (int) Math.ceil(settingFields.size() / (double) columnsCount);

                    for (int fieldIndex = 0; fieldIndex < settingFields.size(); fieldIndex++) {
                        Field field = settingFields.get(fieldIndex);
                        MovementSetting annotation = field.getAnnotation(MovementSetting.class);
                        field.setAccessible(true);
                        try {
                            double value = ((Number) field.get(settings)).doubleValue();
                            int column = fieldIndex / settingsPerColumn;
                            int row = fieldIndex % settingsPerColumn;
                            int settingX = centerX + 20 + column * (settingWidth + 20);
                            int settingY = centerY + yOffset + (row * SETTING_HEIGHT) - scrollOffset;

                            if (settingY >= visibleStartY - BUTTON_HEIGHT && settingY <= visibleEndY) {
                                ButtonWidget labelButton = ButtonWidget.builder(
                                                Text.literal(annotation.label()),
                                                button -> {}
                                        )
                                        .dimensions(settingX, settingY, labelWidth, BUTTON_HEIGHT)
                                        .build();
                                this.addDrawableChild(labelButton);

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
                    yOffset += (settingsPerColumn * SETTING_HEIGHT) + MOVEMENT_SPACING;
                } else {
                    yOffset += MOVEMENT_SPACING;
                }
            }

            int contentHeight = yOffset - (CONTENT_START_Y + BUTTON_HEIGHT + 10);
            int visibleHeight = guiHeight - CONTENT_START_Y - 10;
            maxScroll = Math.max(0, contentHeight - visibleHeight);
        } else if (selectedTab == 0) {
            addGeneralSettings();
        }
    }

    private void cycleMovementType() {
        List<CameraMovementRegistry.MovementInfo> movements = CameraMovementRegistry.getAllMovements();
        if (!movements.isEmpty()) {
            selectedMovementTypeIndex = (selectedMovementTypeIndex + 1) % movements.size();
            reinitialize();
        }
    }

    private void addMovement(int slotIndex) {
        List<CameraMovementRegistry.MovementInfo> movements = CameraMovementRegistry.getAllMovements();
        if (!movements.isEmpty()) {
            try {
                ICameraMovement newMovement = movements.get(selectedMovementTypeIndex)
                        .getMovementClass()
                        .getDeclaredConstructor()
                        .newInstance();
                CraneshotClient.CAMERA_CONTROLLER.addMovement(slotIndex, newMovement);
                reinitialize();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // ... rest of the existing methods (deleteMovement, moveMovement, etc.) remain unchanged

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        super.resize(client, width, height);
        this.scrollOffset = 0;
        this.reinitialize();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0x80000000);
        context.fill(
                centerX,
                centerY + CONTENT_START_Y,
                centerX + guiWidth,
                centerY + guiHeight,
                0xC0000000
        );
        super.render(context, mouseX, mouseY, delta);
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





    private void addGeneralSettings() {
        int yOffset = CONTENT_START_Y + 20;
        int buttonWidth = 200;
        int buttonX = centerX + (guiWidth - buttonWidth) / 2;

        // Add Transition Mode selection
        this.addDrawableChild(ButtonWidget.builder(
                        Text.literal("Transition Mode: " + TransitionModeManager.getCurrentMode().getDisplayName()),
                        button -> {
                            // Cycle through transition modes
                            TransitionMode[] modes = TransitionMode.values();
                            int currentIndex = Arrays.asList(modes).indexOf(TransitionModeManager.getCurrentMode());
                            int nextIndex = (currentIndex + 1) % modes.length;
                            TransitionModeManager.setCurrentMode(modes[nextIndex]);
                            button.setMessage(Text.literal("Transition Mode: " + modes[nextIndex].getDisplayName()));
                        })
                .dimensions(buttonX, centerY + yOffset, buttonWidth, 20)
                .build());
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