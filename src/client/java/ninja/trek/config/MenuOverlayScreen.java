package ninja.trek.config;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import ninja.trek.CameraMovementRegistry;
import ninja.trek.Craneshot;
import ninja.trek.CraneshotClient;
import ninja.trek.cameramovements.AbstractMovementSettings;
import ninja.trek.cameramovements.ICameraMovement;
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
        isMenuOpen = false;
    }

    @Override
    protected void init() {
        this.guiWidth = this.width - (MARGIN * 2);
        this.guiHeight = this.height - (MARGIN * 2);
        this.centerX = MARGIN;
        this.centerY = MARGIN;
        int visibleStartY = centerY + CONTENT_START_Y+30;
        int visibleEndY = centerY + guiHeight;

        createTabButtons();

        int BUTTON_HEIGHT = 20;
        int MOVEMENT_SPACING = BUTTON_HEIGHT - 5;
        int MOVEMENT_ROW_HEIGHT = BUTTON_HEIGHT + 5;
        int SETTING_HEIGHT = BUTTON_HEIGHT + 5;

        if (selectedTab > 0) {
            int slotIndex = selectedTab - 1;
            createControlsBar(slotIndex, visibleStartY, BUTTON_HEIGHT);
            createMovementList(slotIndex, visibleStartY, visibleEndY, BUTTON_HEIGHT,
                    MOVEMENT_ROW_HEIGHT, MOVEMENT_SPACING, SETTING_HEIGHT);
        } else if (selectedTab == 0) {
            addGeneralSettings();
        }
    }

    private void createTabButtons() {
        int tabCount = CraneshotClient.MOVEMENT_MANAGER.getMovementCount() + 1;
        int tabWidth = Math.min(100, (guiWidth - 20) / tabCount);

        for (int i = 0; i <= CraneshotClient.MOVEMENT_MANAGER.getMovementCount(); i++) {
            int tabIndex = i;
            String tabName = (i == 0) ? "General" : "Slot " + i;
            Text buttonText = Text.literal(tabName);
            if (i != selectedTab) {
                buttonText = buttonText.copy().formatted(Formatting.GRAY);
            }

            ButtonWidget slotBtn = ButtonWidget.builder(buttonText, button -> switchTab(tabIndex))
                    .dimensions(centerX + (i * (tabWidth + 5)), centerY, tabWidth, 20)
                    .build();
            this.addDrawableChild(slotBtn);
        }
    }

    private void createControlsBar(int slotIndex, int visibleStartY, int BUTTON_HEIGHT) {
        if (visibleStartY <= centerY + CONTENT_START_Y + BUTTON_HEIGHT) {
            int addButtonWidth = 60;
            int typeButtonWidth = 120;
            int clipboardButtonWidth = 40;
            int spacing = 10;

            // Add movement button
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Add"), button -> addMovement(slotIndex))
                    .dimensions(centerX + 10, centerY + CONTENT_START_Y, addButtonWidth, BUTTON_HEIGHT)
                    .build());

            // Paste button
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Paste"), button -> pasteMovement(slotIndex))
                    .dimensions(centerX + addButtonWidth + spacing, centerY + CONTENT_START_Y, clipboardButtonWidth, BUTTON_HEIGHT)
                    .build());

            // Movement type selector
            List<CameraMovementRegistry.MovementInfo> movements = CameraMovementRegistry.getAllMovements();
            String currentTypeName = movements.isEmpty() ? "None" : movements.get(selectedMovementTypeIndex).getName();
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Type: " + currentTypeName),
                            button -> cycleMovementType())
                    .dimensions(centerX + addButtonWidth + clipboardButtonWidth + spacing * 2, centerY + CONTENT_START_Y,
                            typeButtonWidth, BUTTON_HEIGHT)
                    .build());

            // Wrap checkbox
            this.addDrawableChild(CheckboxWidget.builder(Text.literal("Wrap"), this.textRenderer)
                    .pos(centerX + addButtonWidth + clipboardButtonWidth + typeButtonWidth + spacing * 3, centerY + CONTENT_START_Y)
                    .checked(SlotMenuSettings.getWrapState(slotIndex))
                    .callback((checkbox, checked) -> SlotMenuSettings.setWrapState(slotIndex, checked))
                    .build());

            // Toggle checkbox - add right after Wrap checkbox
            this.addDrawableChild(CheckboxWidget.builder(Text.literal("Toggle"), this.textRenderer)
                    .pos(centerX + addButtonWidth + clipboardButtonWidth + typeButtonWidth + spacing * 3 + 100, centerY + CONTENT_START_Y)
                    .checked(SlotMenuSettings.getToggleState(slotIndex))
                    .callback((checkbox, checked) -> SlotMenuSettings.setToggleState(slotIndex, checked))
                    .build());
        }
    }

    private void createMovementControls(int slotIndex, int index, ICameraMovement movement, int rowY, int BUTTON_HEIGHT) {
        int controlX = centerX + 10;

        // Movement control buttons
        if (index > 0) {
            addDrawableChild(ButtonWidget.builder(Text.literal("↑"),
                            button -> moveMovement(slotIndex, index, index - 1))
                    .dimensions(controlX, rowY, 20, BUTTON_HEIGHT).build());
        }
        controlX += 25;

        if (index < CraneshotClient.MOVEMENT_MANAGER.getAvailableMovementsForSlot(slotIndex).size() - 1) {
            addDrawableChild(ButtonWidget.builder(Text.literal("↓"),
                            button -> moveMovement(slotIndex, index, index + 1))
                    .dimensions(controlX, rowY, 20, BUTTON_HEIGHT).build());
        }
        controlX += 25;

        if (CraneshotClient.MOVEMENT_MANAGER.getAvailableMovementsForSlot(slotIndex).size() > 1) {
            addDrawableChild(ButtonWidget.builder(Text.literal("×"),
                            button -> deleteMovement(slotIndex, index))
                    .dimensions(controlX, rowY, 20, BUTTON_HEIGHT).build());
        }
        controlX += 25;

        // Rename button
        if (movement instanceof AbstractMovementSettings settings) {
            addDrawableChild(ButtonWidget.builder(Text.literal("r"), button -> {
                        if (client != null) {
                            client.setScreen(new RenameModal(this, settings, this::reinitialize));
                        }
                    })
                    .dimensions(controlX, rowY, 20, BUTTON_HEIGHT)
                    .build());
            controlX += 25;
        }

        // Movement name/expand button
        int remainingWidth = Math.min(200, guiWidth / 3);
        String displayName = movement instanceof AbstractMovementSettings ?
                ((AbstractMovementSettings)movement).getDisplayName() :
                movement.getName();
        addDrawableChild(ButtonWidget.builder(
                        Text.literal((isMovementExpanded(slotIndex, index) ? "▼ " : "▶ ") + displayName),
                        button -> {
                            toggleMovementExpanded(slotIndex, index);
                            reinitialize();
                        })
                .dimensions(controlX, rowY, remainingWidth, BUTTON_HEIGHT)
                .build());

        // Copy button after the name
        controlX += remainingWidth + 5;
        addDrawableChild(ButtonWidget.builder(Text.literal("Copy"), button -> copyMovement(movement))
                .dimensions(controlX, rowY, 30, BUTTON_HEIGHT)
                .build());
    }

    // Update the addGeneralSettings() method in MenuOverlayScreen.java
    private void addGeneralSettings() {
        int yOffset = CONTENT_START_Y + 20;
        int buttonWidth = 200;
        int buttonX = centerX + (guiWidth - buttonWidth) / 2;
        int spacing = 25;

        // Define the necessary dimensions
        int BUTTON_HEIGHT = 20;
        int totalWidth = guiWidth - 40;
        int labelWidth = Math.min(200, totalWidth / 3);
        int controlWidth = Math.min(200, totalWidth / 2);

        // Move Speed Slider
        float currentSpeed = FreeCamSettings.getMoveSpeed();
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Free Camera Speed"), button -> {})
                .dimensions(buttonX, centerY + yOffset, labelWidth, BUTTON_HEIGHT)
                .build());
        this.addDrawableChild(SettingWidget.createSlider(
                buttonX + labelWidth + 10,
                centerY + yOffset,
                controlWidth,
                BUTTON_HEIGHT,
                Text.literal("Free Camera Speed"),
                0.1f,
                2.0f,
                currentSpeed,
                "moveSpeed",
                new AbstractMovementSettings() {
                    @Override
                    public void updateSetting(String key, Object value) {
                        if (key.equals("moveSpeed") && value instanceof Number) {
                            FreeCamSettings.setMoveSpeed(((Number)value).floatValue());
                        }
                    }
                }
        ));
        yOffset += spacing;

        // Acceleration Slider
        float currentAcceleration = FreeCamSettings.getAcceleration();
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Acceleration"), button -> {})
                .dimensions(buttonX, centerY + yOffset, labelWidth, BUTTON_HEIGHT)
                .build());
        this.addDrawableChild(SettingWidget.createSlider(
                buttonX + labelWidth + 10,
                centerY + yOffset,
                controlWidth,
                BUTTON_HEIGHT,
                Text.literal("Acceleration"),
                0.01f,
                0.5f,
                currentAcceleration,
                "acceleration",
                new AbstractMovementSettings() {
                    @Override
                    public void updateSetting(String key, Object value) {
                        if (key.equals("acceleration") && value instanceof Number) {
                            FreeCamSettings.setAcceleration(((Number)value).floatValue());
                        }
                    }
                }
        ));
        yOffset += spacing;

        // Deceleration Slider
        float currentDeceleration = FreeCamSettings.getDeceleration();
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Deceleration"), button -> {})
                .dimensions(buttonX, centerY + yOffset, labelWidth, BUTTON_HEIGHT)
                .build());
        this.addDrawableChild(SettingWidget.createSlider(
                buttonX + labelWidth + 10,
                centerY + yOffset,
                controlWidth,
                BUTTON_HEIGHT,
                Text.literal("Deceleration"),
                0.01f,
                0.5f,
                currentDeceleration,
                "deceleration",
                new AbstractMovementSettings() {
                    @Override
                    public void updateSetting(String key, Object value) {
                        if (key.equals("deceleration") && value instanceof Number) {
                            FreeCamSettings.setDeceleration(((Number)value).floatValue());
                        }
                    }
                }
        ));
        yOffset += spacing;

        // Movement Mode Button
        FreeCamSettings.MovementMode currentMode = FreeCamSettings.getMovementMode();
        this.addDrawableChild(ButtonWidget.builder(
                        Text.literal("Movement Mode: " + currentMode.name()),
                        button -> {
                            FreeCamSettings.MovementMode[] modes = FreeCamSettings.MovementMode.values();
                            int nextOrdinal = (currentMode.ordinal() + 1) % modes.length;
                            FreeCamSettings.setMovementMode(modes[nextOrdinal]);
                            button.setMessage(Text.literal("Movement Mode: " + modes[nextOrdinal].name()));
                        })
                .dimensions(buttonX, centerY + yOffset, buttonWidth, 20)
                .build()
        );
    }
    private void createMovementList(int slotIndex, int visibleStartY, int visibleEndY,
                                    int BUTTON_HEIGHT, int MOVEMENT_ROW_HEIGHT, int MOVEMENT_SPACING, int SETTING_HEIGHT) {
        List<ICameraMovement> movements = CraneshotClient.MOVEMENT_MANAGER.getAvailableMovementsForSlot(slotIndex);
        int yOffset = CONTENT_START_Y + BUTTON_HEIGHT + 10;

        for (int i = 0; i < movements.size(); i++) {
            int index = i;
            ICameraMovement movement = movements.get(i);
            int rowY = centerY + yOffset - scrollOffset;

            if (rowY >= visibleStartY - BUTTON_HEIGHT && rowY <= visibleEndY) {
                createMovementControls(slotIndex, index, movement, rowY, BUTTON_HEIGHT);
            }

            yOffset += MOVEMENT_ROW_HEIGHT;

            if (movement instanceof AbstractMovementSettings settings && isMovementExpanded(slotIndex, index)) {
                yOffset = createSettingsSection(settings, rowY, yOffset, visibleStartY, visibleEndY,
                        BUTTON_HEIGHT, SETTING_HEIGHT, MOVEMENT_SPACING);
            } else {
                yOffset += MOVEMENT_SPACING;
            }
        }

        updateScrollBounds(yOffset);
    }

    private int createSettingsSection(AbstractMovementSettings settings, int rowY, int yOffset,
                                      int visibleStartY, int visibleEndY, int BUTTON_HEIGHT, int SETTING_HEIGHT, int MOVEMENT_SPACING) {
        List<Field> settingFields = new ArrayList<>();
        collectSettingFields(settings, settingFields);

        int totalWidth = guiWidth - 40;
        int labelWidth = Math.min(200, totalWidth / 3);  // Increased from 150 to 200
        int controlWidth = Math.min(200, totalWidth / 2);
        int settingWidth = labelWidth + controlWidth + 10;
        int columnsCount = Math.max(1, Math.min(3, (totalWidth + 20) / (settingWidth + 20)));
        int settingsPerColumn = (int) Math.ceil(settingFields.size() / (double) columnsCount);

        for (int fieldIndex = 0; fieldIndex < settingFields.size(); fieldIndex++) {
            Field field = settingFields.get(fieldIndex);
            MovementSetting annotation = field.getAnnotation(MovementSetting.class);
            field.setAccessible(true);
            try {
                int column = fieldIndex / settingsPerColumn;
                int row = fieldIndex % settingsPerColumn;
                int settingX = centerX + 20 + column * (settingWidth + 20);
                int settingY = centerY + yOffset + (row * SETTING_HEIGHT) - scrollOffset;

                if (settingY >= visibleStartY - BUTTON_HEIGHT && settingY <= visibleEndY) {
                    createSettingControl(settings, field, annotation, settingX, settingY,
                            labelWidth, controlWidth, BUTTON_HEIGHT);
                }
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        return yOffset + (settingsPerColumn * SETTING_HEIGHT) + MOVEMENT_SPACING;
    }

    private void createSettingControl(AbstractMovementSettings settings, Field field,
                                      MovementSetting annotation, int settingX, int settingY,
                                      int labelWidth, int controlWidth, int BUTTON_HEIGHT)
            throws IllegalAccessException {
        if (annotation.type() == MovementSettingType.ENUM) {
            // For enums, create the button
            ButtonWidget enumButton = SettingWidget.createEnumButton(
                    settingX,
                    settingY,
                    labelWidth + controlWidth + 10,
                    BUTTON_HEIGHT,
                    field.getName(),
                    settings,
                    annotation
            );
            addDrawableChild(enumButton);

            // Add warning if needed for postMoveMouse field
            if (field.getName().equals("postMoveMouse")) {
                AbstractMovementSettings.POST_MOVE_MOUSE mouseMode =
                        (AbstractMovementSettings.POST_MOVE_MOUSE) field.get(settings);

                // Get the postMoveKeys field
                try {
                    Field keysField = AbstractMovementSettings.class.getDeclaredField("postMoveKeys");
                    keysField.setAccessible(true);
                    AbstractMovementSettings.POST_MOVE_KEYS keysMode =
                            (AbstractMovementSettings.POST_MOVE_KEYS) keysField.get(settings);

                    // Check warning conditions - only show warning for camera movement modes
                    if (mouseMode == AbstractMovementSettings.POST_MOVE_MOUSE.NONE &&
                            (keysMode == AbstractMovementSettings.POST_MOVE_KEYS.MOVE_CAMERA_FLAT ||
                                    keysMode == AbstractMovementSettings.POST_MOVE_KEYS.MOVE_CAMERA_FREE) &&
                            keysMode != AbstractMovementSettings.POST_MOVE_KEYS.MOVE8 &&
                            keysMode != AbstractMovementSettings.POST_MOVE_KEYS.NONE) {

                        // Create warning button
                        ButtonWidget warningButton = ButtonWidget.builder(
                                        Text.literal("!").formatted(Formatting.GOLD),
                                        button -> {}
                                )
                                .dimensions(settingX + labelWidth + controlWidth + 15, settingY, 20, BUTTON_HEIGHT)
                                .tooltip(Tooltip.of(Text.literal(
                                        "Warning: Camera rotation will be locked, Rotate Camera recommended")))
                                .build();

                        addDrawableChild(warningButton);
                    }
                } catch (Exception e) {
                    Craneshot.LOGGER.error("Error checking warning conditions", e);
                }
            }
        } else {
            // For non-enum settings, keep the original label + control layout
            addDrawableChild(ButtonWidget.builder(Text.literal(annotation.label()), button -> {})
                    .dimensions(settingX, settingY, labelWidth, BUTTON_HEIGHT)
                    .build());
            addDrawableChild(SettingWidget.createSlider(
                    settingX + labelWidth + 10,
                    settingY,
                    controlWidth,
                    BUTTON_HEIGHT,
                    Text.literal(annotation.label()),
                    annotation.min(),
                    annotation.max(),
                    ((Number) field.get(settings)).doubleValue(),
                    field.getName(),
                    settings
            ));
        }
    }

    private void collectSettingFields(AbstractMovementSettings settings, List<Field> settingFields) {
        // Get fields from the concrete class
        for (Field field : settings.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(MovementSetting.class)) {
                settingFields.add(field);
            }
        }
        // Get fields from AbstractMovementSettings
        for (Field field : AbstractMovementSettings.class.getDeclaredFields()) {
            if (field.isAnnotationPresent(MovementSetting.class)) {
                settingFields.add(field);
            }
        }
    }

    private void updateScrollBounds(int yOffset) {
        int contentHeight = yOffset - (CONTENT_START_Y + 20 + 10);
        int visibleHeight = guiHeight - CONTENT_START_Y - 10;
        maxScroll = Math.max(0, contentHeight - visibleHeight);
    }



    // In MenuOverlayScreen.java
    private void cycleMovementType() {
        List<CameraMovementRegistry.MovementInfo> movements = CameraMovementRegistry.getAllMovements();
        if (!movements.isEmpty()) {
            selectedMovementTypeIndex = (selectedMovementTypeIndex + 1) % movements.size();

            // Update the button text immediately
            for (Element child : this.children()) {
                if (child instanceof ButtonWidget button) {
                    String buttonText = button.getMessage().getString();
                    if (buttonText.startsWith("Type: ")) {
                        String currentTypeName = movements.get(selectedMovementTypeIndex).getName();
                        button.setMessage(Text.literal("Type: " + currentTypeName));
                        break;
                    }
                }
            }
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
                CraneshotClient.MOVEMENT_MANAGER.addMovement(slotIndex, newMovement);
                reinitialize();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

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
        CraneshotClient.MOVEMENT_MANAGER.removeMovement(slotIndex, movementIndex);
        reinitialize();
    }

    private void moveMovement(int slotIndex, int fromIndex, int toIndex) {
        CraneshotClient.MOVEMENT_MANAGER.swapMovements(slotIndex, fromIndex, toIndex);
        reinitialize();
    }

    void reinitialize() {
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

    public void toggleMenu() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (isMenuOpen) {
            close();
        } else {
            client.setScreen(this);
            isMenuOpen = true;
        }
    }

    @Override
    public void close() {
        // Save the current slots configuration before closing
        List<List<ICameraMovement>> slots = new ArrayList<>();
        for (int i = 0; i < CraneshotClient.MOVEMENT_MANAGER.getMovementCount(); i++) {
            slots.add(CraneshotClient.MOVEMENT_MANAGER.getAvailableMovementsForSlot(i));
        }
        SlotSettingsIO.saveSlots(slots);
        GeneralSettingsIO.saveSettings();

        if (this.client != null) {
            this.client.setScreen(null);
        }
        isMenuOpen = false;
    }

    private void copyMovement(ICameraMovement movement) {
        SlotSettingsIO.copyMovementToClipboard(movement);
    }

    private void pasteMovement(int slotIndex) {
        try {
            ICameraMovement newMovement = SlotSettingsIO.createMovementFromClipboard();
            if (newMovement != null) {
                CraneshotClient.MOVEMENT_MANAGER.addMovement(slotIndex, newMovement);
                reinitialize();
            }
        } catch (Exception e) {
            Craneshot.LOGGER.error("Failed to paste movement", e);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}