package ninja.trek.config;

import ninja.trek.CameraMovementRegistry;
import ninja.trek.Craneshot;
import ninja.trek.CraneshotClient;
import ninja.trek.cameramovements.AbstractMovementSettings;
import ninja.trek.cameramovements.ICameraMovement;
import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import ninja.trek.config.FollowerConfig.FollowerEntry;


public class MenuOverlayScreen extends Screen {
    private static final Map<Integer, Set<Integer>> expandedMovements = new HashMap<>();
    private static final Set<String> expandedSettings = new HashSet<>();
    private static final int MARGIN = 0;
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
    private int followerMovementTypeIndex = 0;
    private EditBox targetPlayerNameField;
    private EditBox followerTargetPlayerNameField;
    private FollowerConfig followerConfig;
    private static final Map<Integer, Boolean> expandedFollowers = new HashMap<>();

    public MenuOverlayScreen() {
        super(Component.literal("CraneShot Settings"));
        isMenuOpen = false;
        followerConfig = FollowerSettingsIO.loadFollowers();
    }

    @Override
    protected void init() {
        this.guiWidth = this.width - (MARGIN * 2);
        this.guiHeight = this.height - (MARGIN * 2);
        this.centerX = MARGIN;
        this.centerY = MARGIN;
        int visibleStartY = centerY + CONTENT_START_Y+40;
        int visibleEndY = centerY + guiHeight+20;

        createTabButtons();

        int BUTTON_HEIGHT = 20;
        int MOVEMENT_SPACING = BUTTON_HEIGHT - 5;
        int MOVEMENT_ROW_HEIGHT = BUTTON_HEIGHT + 5;
        int SETTING_HEIGHT = BUTTON_HEIGHT + 5;

        if (selectedTab == 0) {
            addGeneralSettings();
        } else if (selectedTab == 1) {
            addFollowerSettings();
        } else if (selectedTab > 1) {
            int slotIndex = selectedTab - 2;
            createControlsBar(slotIndex, visibleStartY, BUTTON_HEIGHT);
            createMovementList(slotIndex, visibleStartY, visibleEndY, BUTTON_HEIGHT,
                    MOVEMENT_ROW_HEIGHT, MOVEMENT_SPACING, SETTING_HEIGHT);
        }
    }

    private void createTabButtons() {
        // Tab 0 = General, Tab 1 = Followers, Tabs 2..7 = Slots 1..6
        int tabCount = CraneshotClient.MOVEMENT_MANAGER.getMovementCount() + 2; // +2 for General and Followers
        int tabWidth = Math.min(100, (guiWidth - 20) / tabCount);

        for (int i = 0; i < tabCount; i++) {
            int tabIndex = i;
            String tabName;
            if (i == 0) {
                tabName = "General";
            } else if (i == 1) {
                tabName = "Followers";
            } else {
                tabName = String.valueOf(i - 1); // Slot number
            }
            Component buttonText = Component.literal(tabName);
            if (i != selectedTab) {
                buttonText = buttonText.copy().withStyle(ChatFormatting.GRAY);
            }

            Button slotBtn = Button.builder(buttonText, button -> switchTab(tabIndex))
                    .bounds(centerX + (i * (tabWidth + 5)), centerY, tabWidth, 20)
                    .build();
            this.addRenderableWidget(slotBtn);
        }
    }

    private void createControlsBar(int slotIndex, int visibleStartY, int BUTTON_HEIGHT) {
        // Remove the visibility check since these controls should always be visible
        int addButtonWidth = 60;
        int typeButtonWidth = 120;
        int clipboardButtonWidth = 40;
        int spacing = 10;

        // Add movement button
        this.addRenderableWidget(Button.builder(Component.literal("Add"), button -> addMovement(slotIndex))
                .bounds(centerX + 10, centerY + CONTENT_START_Y, addButtonWidth, BUTTON_HEIGHT)
                .build());

        // Paste button
        this.addRenderableWidget(Button.builder(Component.literal("Paste"), button -> pasteMovement(slotIndex))
                .bounds(centerX + addButtonWidth + spacing, centerY + CONTENT_START_Y, clipboardButtonWidth, BUTTON_HEIGHT)
                .build());

        // Movement type selector
        List<CameraMovementRegistry.MovementInfo> movements = CameraMovementRegistry.getAllMovements();
        String currentTypeName = movements.isEmpty() ? "None" : movements.get(selectedMovementTypeIndex).getName();
        this.addRenderableWidget(Button.builder(Component.literal("Type: " + currentTypeName),
                        button -> cycleMovementType())
                .bounds(centerX + addButtonWidth + clipboardButtonWidth + spacing * 2, centerY + CONTENT_START_Y,
                        typeButtonWidth, BUTTON_HEIGHT)
                .build());

        // Wrap checkbox
        this.addRenderableWidget(Checkbox.builder(Component.literal("Wrap"), Minecraft.getInstance().font)
                .pos(centerX + addButtonWidth + clipboardButtonWidth + typeButtonWidth + spacing * 3, centerY + CONTENT_START_Y)
                .selected(SlotMenuSettings.getWrapState(slotIndex))
                .onValueChange((checkbox, checked) -> SlotMenuSettings.setWrapState(slotIndex, checked))
                .build());

        // Toggle checkbox - add right after Wrap checkbox
        this.addRenderableWidget(Checkbox.builder(Component.literal("Toggle"), Minecraft.getInstance().font)
                .pos(centerX + addButtonWidth + clipboardButtonWidth + typeButtonWidth + spacing * 3 + 100, centerY + CONTENT_START_Y)
                .selected(SlotMenuSettings.getToggleState(slotIndex))
                .onValueChange((checkbox, checked) -> SlotMenuSettings.setToggleState(slotIndex, checked))
                .build());
    }
    private void createMovementControls(int slotIndex, int index, ICameraMovement movement, int rowY, int BUTTON_HEIGHT) {
        int controlX = centerX + 10;

        // Movement control buttons
        if (index > 0) {
            addRenderableWidget(Button.builder(Component.literal("↑"),
                            button -> moveMovement(slotIndex, index, index - 1))
                    .bounds(controlX, rowY, 20, BUTTON_HEIGHT).build());
        }
        controlX += 25;

        if (index < CraneshotClient.MOVEMENT_MANAGER.getAvailableMovementsForSlot(slotIndex).size() - 1) {
            addRenderableWidget(Button.builder(Component.literal("↓"),
                            button -> moveMovement(slotIndex, index, index + 1))
                    .bounds(controlX, rowY, 20, BUTTON_HEIGHT).build());
        }
        controlX += 25;

        if (CraneshotClient.MOVEMENT_MANAGER.getAvailableMovementsForSlot(slotIndex).size() > 1) {
            addRenderableWidget(Button.builder(Component.literal("×"),
                            button -> deleteMovement(slotIndex, index))
                    .bounds(controlX, rowY, 20, BUTTON_HEIGHT).build());
        }
        controlX += 25;

        // Rename button
        if (movement instanceof AbstractMovementSettings settings) {
            addRenderableWidget(Button.builder(Component.literal("r"), button -> {
                        if (minecraft != null) {
                            minecraft.setScreen(new RenameModal(this, settings, this::reinitialize));
                        }
                    })
                    .bounds(controlX, rowY, 20, BUTTON_HEIGHT)
                    .build());
            controlX += 25;
        }

        // Movement name/expand button
        int remainingWidth = Math.min(200, guiWidth / 3);
        String displayName = movement instanceof AbstractMovementSettings ?
                ((AbstractMovementSettings)movement).getDisplayName() :
                movement.getName();
        addRenderableWidget(Button.builder(
                        Component.literal((isMovementExpanded(slotIndex, index) ? "▼ " : "▶ ") + displayName),
                        button -> {
                            toggleMovementExpanded(slotIndex, index);
                            reinitialize();
                        })
                .bounds(controlX, rowY, remainingWidth, BUTTON_HEIGHT)
                .build());

        // Copy button after the name
        controlX += remainingWidth + 5;
        addRenderableWidget(Button.builder(Component.literal("Copy"), button -> copyMovement(movement))
                .bounds(controlX, rowY, 30, BUTTON_HEIGHT)
                .build());
    }

    private void addGeneralSettings() {
        int yOffset = CONTENT_START_Y + 20;
        int buttonWidth = 200;
        int buttonX = centerX +20;
        int spacing = 25;

        // Define the necessary dimensions
        int BUTTON_HEIGHT = 20;
        int totalWidth = guiWidth - 40;
        int labelWidth = Math.min(200, totalWidth / 3);
        int controlWidth = Math.min(200, totalWidth / 2);
        int baseY = centerY - scrollOffset;

        // Auto Advance Checkbox
        this.addRenderableWidget(Checkbox.builder(Component.literal("Auto Advance"), Minecraft.getInstance().font)
                .pos(buttonX, baseY + yOffset)
                .selected(GeneralMenuSettings.isAutoAdvance())
                .onValueChange((checkbox, checked) -> GeneralMenuSettings.setAutoAdvance(checked))
                .build());

        yOffset += spacing;

        // Show/Hide Vanilla Crosshair
        this.addRenderableWidget(Checkbox.builder(Component.literal("Show Vanilla Crosshair"), Minecraft.getInstance().font)
                .pos(buttonX, baseY + yOffset)
                .selected(GeneralMenuSettings.isShowVanillaCrosshair())
                .onValueChange((checkbox, checked) -> {
                    GeneralMenuSettings.setShowVanillaCrosshair(checked);
                    GeneralSettingsIO.saveSettings();
                })
                .build());

        yOffset += spacing;

        // Show/Hide Camera Crosshair
        this.addRenderableWidget(Checkbox.builder(Component.literal("Show Camera Crosshair"), Minecraft.getInstance().font)
                .pos(buttonX, baseY + yOffset)
                .selected(GeneralMenuSettings.isShowCameraCrosshair())
                .onValueChange((checkbox, checked) -> {
                    GeneralMenuSettings.setShowCameraCrosshair(checked);
                    GeneralSettingsIO.saveSettings();
                })
                .build());

        yOffset += spacing;

        // Camera Crosshair Shape
        this.addRenderableWidget(Checkbox.builder(Component.literal("Camera Crosshair: Square"), Minecraft.getInstance().font)
                .pos(buttonX, baseY + yOffset)
                .selected(GeneralMenuSettings.isCameraCrosshairSquare())
                .onValueChange((checkbox, checked) -> {
                    GeneralMenuSettings.setCameraCrosshairSquare(checked);
                    GeneralSettingsIO.saveSettings();
                })
                .build());

        yOffset += spacing;

        // Camera Crosshair Size slider
        this.addRenderableWidget(Button.builder(Component.literal("Camera Crosshair Size"), button -> {})
                .bounds(buttonX, baseY + yOffset, labelWidth, BUTTON_HEIGHT)
                .build());
        this.addRenderableWidget(SettingWidget.createSlider(
                buttonX + labelWidth + 10,
                baseY + yOffset,
                controlWidth,
                BUTTON_HEIGHT,
                Component.literal("Camera Crosshair Size"),
                1f,
                20f,
                GeneralMenuSettings.getCameraCrosshairSize(),
                "cameraCrosshairSize",
                new AbstractMovementSettings() {
                    @Override
                    public void updateSetting(String key, Object value) {
                        if (key.equals("cameraCrosshairSize") && value instanceof Number) {
                            GeneralMenuSettings.setCameraCrosshairSize(((Number) value).intValue());
                            GeneralSettingsIO.saveSettings();
                        }
                    }
                }
        ));

        yOffset += spacing;

        // Show/Hide Node Overlays outside edit mode
        this.addRenderableWidget(Checkbox.builder(Component.literal("Show Nodes Outside Edit"), Minecraft.getInstance().font)
                .pos(buttonX, baseY + yOffset)
                .selected(GeneralMenuSettings.isShowNodesOutsideEdit())
                .onValueChange((checkbox, checked) -> {
                    GeneralMenuSettings.setShowNodesOutsideEdit(checked);
                    GeneralSettingsIO.saveSettings();
                })
                .build());

        yOffset += spacing;

        // Enable/Disable Zones
        this.addRenderableWidget(Checkbox.builder(Component.literal("Zones Enabled"), Minecraft.getInstance().font)
                .pos(buttonX, baseY + yOffset)
                .selected(GeneralMenuSettings.isZonesEnabled())
                .onValueChange((checkbox, checked) -> {
                    GeneralMenuSettings.setZonesEnabled(checked);
                    GeneralSettingsIO.saveSettings();
                })
                .build());

        yOffset += spacing;

        // Enforce Minimum Speed Checkbox
        this.addRenderableWidget(Checkbox.builder(Component.literal("Enforce Minimum Speed During Return"), Minecraft.getInstance().font)
                .pos(buttonX, baseY + yOffset)
                .selected(GeneralMenuSettings.isEnforceMinimumSpeed())
                .onValueChange((checkbox, checked) -> {
                    GeneralMenuSettings.setEnforceMinimumSpeed(checked);
                    GeneralSettingsIO.saveSettings();
                })
                .build());

        yOffset += spacing;

        // Minimum Speed Multiplier slider
        this.addRenderableWidget(Button.builder(Component.literal("Minimum Speed Multiplier"), button -> {})
                .bounds(buttonX, baseY + yOffset, labelWidth, BUTTON_HEIGHT)
                .build());
        this.addRenderableWidget(SettingWidget.createSlider(
                buttonX + labelWidth + 10,
                baseY + yOffset,
                controlWidth,
                BUTTON_HEIGHT,
                Component.literal("Minimum Speed Multiplier"),
                1.0f,
                3.0f,
                GeneralMenuSettings.getMinimumSpeedMultiplier(),
                "minimumSpeedMultiplier",
                new AbstractMovementSettings() {
                    @Override
                    public void updateSetting(String key, Object value) {
                        if (key.equals("minimumSpeedMultiplier") && value instanceof Number) {
                            GeneralMenuSettings.setMinimumSpeedMultiplier(((Number) value).doubleValue());
                            GeneralSettingsIO.saveSettings();
                        }
                    }
                }
        ));

        yOffset += spacing;

        // Use Default Movement When Idle Checkbox
        this.addRenderableWidget(Checkbox.builder(Component.literal("Use Default Movement When Idle"), Minecraft.getInstance().font)
                .pos(buttonX, baseY + yOffset)
                .selected(GeneralMenuSettings.isUseDefaultIdleMovement())
                .onValueChange((checkbox, checked) -> {
                    GeneralMenuSettings.setUseDefaultIdleMovement(checked);
                    GeneralSettingsIO.saveSettings();
                })
                .build());

        yOffset += spacing;

        // Node Editor sensitivity slider
        this.addRenderableWidget(Button.builder(Component.literal("Node Edit Sensitivity"), button -> {})
                .bounds(buttonX, baseY + yOffset, labelWidth, BUTTON_HEIGHT)
                .build());
        this.addRenderableWidget(SettingWidget.createSlider(
                buttonX + labelWidth + 10,
                baseY + yOffset,
                controlWidth,
                BUTTON_HEIGHT,
                Component.literal("Node Edit Sensitivity"),
                0.5f,
                20.0f,
                GeneralMenuSettings.getNodeEditSensitivityMultiplier(),
                "nodeEditSensitivity",
                new ninja.trek.cameramovements.AbstractMovementSettings() {
                    @Override
                    public void updateSetting(String key, Object value) {
                        if (key.equals("nodeEditSensitivity") && value instanceof Number) {
                            GeneralMenuSettings.setNodeEditSensitivityMultiplier(((Number) value).doubleValue());
                            // Persist
                            GeneralSettingsIO.saveSettings();
                        }
                    }
                }
        ));

        yOffset += spacing;

        // Add collapsible Spectator Follow section header
        String spectatorFollowKey = "spectatorFollowSection";
        boolean spectatorFollowExpanded = isSettingsExpanded(spectatorFollowKey);
        this.addRenderableWidget(Button.builder(
                Component.literal((spectatorFollowExpanded ? "▼ " : "▶ ") + "Spectator Follow Settings").withStyle(ChatFormatting.YELLOW),
                button -> {
                    toggleSettingsExpanded(spectatorFollowKey);
                    reinitialize();
                })
                .bounds(buttonX, baseY + yOffset, buttonWidth, BUTTON_HEIGHT)
                .build());
        yOffset += spacing;

        if (spectatorFollowExpanded) {
            // Enable/Disable Spectator Follow
            this.addRenderableWidget(Checkbox.builder(Component.literal("Enable Spectator Follow"), Minecraft.getInstance().font)
                    .pos(buttonX + 10, baseY + yOffset)
                    .selected(GeneralMenuSettings.isSpectatorFollowEnabled())
                    .onValueChange((checkbox, checked) -> {
                        GeneralMenuSettings.setSpectatorFollowEnabled(checked);
                        GeneralSettingsIO.saveSettings();
                    })
                    .build());

            yOffset += spacing;

            // Target Player Name Label
            this.addRenderableWidget(Button.builder(Component.literal("Target Player Name:"), button -> {})
                    .bounds(buttonX + 10, baseY + yOffset, labelWidth, BUTTON_HEIGHT)
                    .build());

            // Target Player Name Text Field
            targetPlayerNameField = new EditBox(
                    Minecraft.getInstance().font,
                    buttonX + labelWidth + 20,
                    baseY + yOffset,
                    controlWidth,
                    BUTTON_HEIGHT,
                    Component.literal("Player name")
            );
            targetPlayerNameField.setMaxLength(16); // Minecraft username max length
            targetPlayerNameField.setValue(GeneralMenuSettings.getTargetPlayerName());
            targetPlayerNameField.setResponder(text -> {
                GeneralMenuSettings.setTargetPlayerName(text);
                GeneralSettingsIO.saveSettings();
            });
            this.addRenderableWidget(targetPlayerNameField);

            yOffset += spacing;

            // Status Indicator
            Minecraft client = Minecraft.getInstance();
            String statusText = getSpectatorFollowStatus(client);
            ChatFormatting statusColor = getSpectatorFollowStatusColor(client);

            this.addRenderableWidget(Button.builder(
                    Component.literal("Status: " + statusText).withStyle(statusColor),
                    button -> {})
                    .bounds(buttonX + 10, baseY + yOffset, buttonWidth, BUTTON_HEIGHT)
                    .build());

            yOffset += spacing;
        }

        // Add collapsible Free Camera section header
        String freeCamKey = "freeCamSection";
        boolean freeCamExpanded = isSettingsExpanded(freeCamKey);
        this.addRenderableWidget(Button.builder(
                Component.literal((freeCamExpanded ? "▼ " : "▶ ") + "Free Camera Settings").withStyle(ChatFormatting.YELLOW),
                button -> {
                    toggleSettingsExpanded(freeCamKey);
                    reinitialize();
                })
                .bounds(buttonX, baseY + yOffset, buttonWidth, BUTTON_HEIGHT)
                .build());
        yOffset += spacing;
        
        if (freeCamExpanded) {
            // Move Speed Slider
            float currentSpeed = GeneralMenuSettings.getFreeCamSettings().getMoveSpeed();
            this.addRenderableWidget(Button.builder(Component.literal("Free Camera Speed"), button -> {})
                    .bounds(buttonX, baseY + yOffset, labelWidth, BUTTON_HEIGHT)
                    .build());
            this.addRenderableWidget(SettingWidget.createSlider(
                    buttonX + labelWidth + 10,
                    baseY + yOffset,
                    controlWidth,
                    BUTTON_HEIGHT,
                    Component.literal("Free Camera Speed"),
                    0.1f,
                    2.0f,
                    currentSpeed,
                    "moveSpeed",
                    new AbstractMovementSettings() {
                        @Override
                        public void updateSetting(String key, Object value) {
                            if (key.equals("moveSpeed") && value instanceof Number) {
                                GeneralMenuSettings.getFreeCamSettings().setMoveSpeed(((Number)value).floatValue());
                            }
                        }
                    }
            ));

            yOffset += spacing;

            // Acceleration Slider
            float currentAcceleration = GeneralMenuSettings.getFreeCamSettings().getAcceleration();
            this.addRenderableWidget(Button.builder(Component.literal("Acceleration"), button -> {})
                    .bounds(buttonX, baseY + yOffset, labelWidth, BUTTON_HEIGHT)
                    .build());
            this.addRenderableWidget(SettingWidget.createSlider(
                    buttonX + labelWidth + 10,
                    baseY + yOffset,
                    controlWidth,
                    BUTTON_HEIGHT,
                    Component.literal("Acceleration"),
                    0.01f,
                    0.1f,
                    currentAcceleration,
                    "acceleration",
                    new AbstractMovementSettings() {
                        @Override
                        public void updateSetting(String key, Object value) {
                            if (key.equals("acceleration") && value instanceof Number) {
                                GeneralMenuSettings.getFreeCamSettings().setAcceleration(((Number)value).floatValue());
                            }
                        }
                    }
            ));

            yOffset += spacing;

            // Deceleration Slider
            float currentDeceleration = GeneralMenuSettings.getFreeCamSettings().getDeceleration();
            this.addRenderableWidget(Button.builder(Component.literal("Deceleration"), button -> {})
                    .bounds(buttonX, baseY + yOffset, labelWidth, BUTTON_HEIGHT)
                    .build());
            this.addRenderableWidget(SettingWidget.createSlider(
                    buttonX + labelWidth + 10,
                    baseY + yOffset,
                    controlWidth,
                    BUTTON_HEIGHT,
                    Component.literal("Deceleration"),
                    0.01f,
                    0.5f,
                    currentDeceleration,
                    "deceleration",
                    new AbstractMovementSettings() {
                        @Override
                        public void updateSetting(String key, Object value) {
                            if (key.equals("deceleration") && value instanceof Number) {
                                GeneralMenuSettings.getFreeCamSettings().setDeceleration(((Number)value).floatValue());
                            }
                        }
                    }
            ));

            yOffset += spacing;

            // Rotation Easing Slider
            float currentRotationEasing = GeneralMenuSettings.getFreeCamSettings().getRotationEasing();
            this.addRenderableWidget(Button.builder(Component.literal("Rotation Easing"), button -> {})
                    .bounds(buttonX, baseY + yOffset, labelWidth, BUTTON_HEIGHT)
                    .build());
            this.addRenderableWidget(SettingWidget.createSlider(
                    buttonX + labelWidth + 10,
                    baseY + yOffset,
                    controlWidth,
                    BUTTON_HEIGHT,
                    Component.literal("Rotation Easing"),
                    0.01f,
                    1.0f,
                    currentRotationEasing,
                    "rotationEasing",
                    new AbstractMovementSettings() {
                        @Override
                        public void updateSetting(String key, Object value) {
                            if (key.equals("rotationEasing") && value instanceof Number) {
                                GeneralMenuSettings.getFreeCamSettings().setRotationEasing(((Number)value).floatValue());
                            }
                        }
                    }
            ));

            yOffset += spacing;

            // Rotation Speed Limit Slider
            float currentRotationSpeedLimit = GeneralMenuSettings.getFreeCamSettings().getRotationSpeedLimit();
            this.addRenderableWidget(Button.builder(Component.literal("Rotation Speed Limit"), button -> {})
                    .bounds(buttonX, baseY + yOffset, labelWidth, BUTTON_HEIGHT)
                    .build());
            this.addRenderableWidget(SettingWidget.createSlider(
                    buttonX + labelWidth + 10,
                    baseY + yOffset,
                    controlWidth,
                    BUTTON_HEIGHT,
                    Component.literal("Rotation Speed Limit"),
                    0.1f,
                    1000.0f,
                    currentRotationSpeedLimit,
                    "rotationSpeedLimit",
                    new AbstractMovementSettings() {
                        @Override
                        public void updateSetting(String key, Object value) {
                            if (key.equals("rotationSpeedLimit") && value instanceof Number) {
                                GeneralMenuSettings.getFreeCamSettings().setRotationSpeedLimit(((Number)value).floatValue());
                            }
                        }
                    }
            ));
        }
        
        // Add collapsible Default Idle Movement Settings section
        yOffset += spacing;
        String defaultIdleKey = "defaultIdleSection";
        boolean defaultIdleExpanded = isSettingsExpanded(defaultIdleKey);
        this.addRenderableWidget(Button.builder(
                Component.literal((defaultIdleExpanded ? "â–¼ " : "â–¶ ") + "Default Idle Movement (Linear)").withStyle(ChatFormatting.YELLOW),
                button -> {
                    toggleSettingsExpanded(defaultIdleKey);
                    reinitialize();
                })
                .bounds(buttonX, baseY + yOffset, buttonWidth, BUTTON_HEIGHT)
                .build());

        if (defaultIdleExpanded) {
            yOffset += spacing;
            ninja.trek.cameramovements.movements.LinearMovement defaultIdle = GeneralMenuSettings.getDefaultIdleMovement();

            // Render settings using the same layout helper as movement lists
            java.util.List<java.lang.reflect.Field> settingFields = new java.util.ArrayList<>();
            collectSettingFields(defaultIdle, settingFields);

            // Reuse totalWidth/labelWidth/controlWidth already defined earlier in this method
            int settingWidth = labelWidth + controlWidth + 10;
            int columnsCount = Math.max(1, Math.min(3, (totalWidth + 20) / (settingWidth + 20)));
            int settingsPerColumn = (int) Math.ceil(settingFields.size() / (double) columnsCount);

            for (int fieldIndex = 0; fieldIndex < settingFields.size(); fieldIndex++) {
                java.lang.reflect.Field field = settingFields.get(fieldIndex);
                ninja.trek.config.MovementSetting annotation = field.getAnnotation(ninja.trek.config.MovementSetting.class);
                field.setAccessible(true);
                try {
                    int column = fieldIndex / settingsPerColumn;
                    int row = fieldIndex % settingsPerColumn;
                    int settingX = centerX + 20 + column * (settingWidth + 20);
                    int settingY = baseY + yOffset + (row * BUTTON_HEIGHT);

                    createSettingControl(defaultIdle, field, annotation, settingX, settingY,
                            labelWidth, controlWidth, BUTTON_HEIGHT);
                } catch (IllegalAccessException ignored) {}
            }

            // Advance yOffset by the number of rows we used
            yOffset += settingsPerColumn * BUTTON_HEIGHT;
        }

        // Add collapsible Follow Movement Settings section (keybind-only)
        yOffset += spacing;
        String followKey = "followMovementSection";
        boolean followExpanded = isSettingsExpanded(followKey);
        this.addRenderableWidget(Button.builder(
                Component.literal((followExpanded ? "v " : "> ") + "Follow Movement").withStyle(ChatFormatting.YELLOW),
                button -> {
                    toggleSettingsExpanded(followKey);
                    reinitialize();
                })
                .bounds(buttonX, baseY + yOffset, buttonWidth, BUTTON_HEIGHT)
                .build());

        if (followExpanded) {
            yOffset += spacing;
            ninja.trek.cameramovements.movements.FollowMovement follow = GeneralMenuSettings.getFollowMovement();

            java.util.List<java.lang.reflect.Field> settingFields = new java.util.ArrayList<>();
            collectSettingFields(follow, settingFields);

            int settingWidth = labelWidth + controlWidth + 10;
            int columnsCount = Math.max(1, Math.min(3, (totalWidth + 20) / (settingWidth + 20)));
            int settingsPerColumn = (int) Math.ceil(settingFields.size() / (double) columnsCount);

            for (int fieldIndex = 0; fieldIndex < settingFields.size(); fieldIndex++) {
                java.lang.reflect.Field field = settingFields.get(fieldIndex);
                ninja.trek.config.MovementSetting annotation = field.getAnnotation(ninja.trek.config.MovementSetting.class);
                field.setAccessible(true);
                try {
                    int column = fieldIndex / settingsPerColumn;
                    int row = fieldIndex % settingsPerColumn;
                    int settingX = centerX + 20 + column * (settingWidth + 20);
                    int settingY = baseY + yOffset + (row * BUTTON_HEIGHT);

                    createSettingControl(follow, field, annotation, settingX, settingY,
                            labelWidth, controlWidth, BUTTON_HEIGHT);
                } catch (IllegalAccessException ignored) {}
            }

            yOffset += settingsPerColumn * BUTTON_HEIGHT;
        }

        // Add collapsible Zoom Settings section
        yOffset += spacing;
        String zoomKey = "zoomSection";
        boolean zoomExpanded = isSettingsExpanded(zoomKey);
        this.addRenderableWidget(Button.builder(
                Component.literal((zoomExpanded ? "▼ " : "▶ ") + "Zoom Settings").withStyle(ChatFormatting.YELLOW),
                button -> {
                    toggleSettingsExpanded(zoomKey);
                    reinitialize();
                })
                .bounds(buttonX, baseY + yOffset, buttonWidth, BUTTON_HEIGHT)
                .build());

        if (zoomExpanded) {
            yOffset += spacing;
            ninja.trek.cameramovements.movements.ZoomMovement zoom = GeneralMenuSettings.getZoomMovement();

            java.util.List<java.lang.reflect.Field> settingFields = new java.util.ArrayList<>();
            collectSettingFields(zoom, settingFields);

            int settingWidth = labelWidth + controlWidth + 10;
            int columnsCount = Math.max(1, Math.min(3, (totalWidth + 20) / (settingWidth + 20)));
            int settingsPerColumn = (int) Math.ceil(settingFields.size() / (double) columnsCount);

            for (int fieldIndex = 0; fieldIndex < settingFields.size(); fieldIndex++) {
                java.lang.reflect.Field field = settingFields.get(fieldIndex);
                ninja.trek.config.MovementSetting annotation = field.getAnnotation(ninja.trek.config.MovementSetting.class);
                field.setAccessible(true);
                try {
                    int column = fieldIndex / settingsPerColumn;
                    int row = fieldIndex % settingsPerColumn;
                    int settingX = centerX + 20 + column * (settingWidth + 20);
                    int settingY = baseY + yOffset + (row * BUTTON_HEIGHT);

                    createSettingControl(zoom, field, annotation, settingX, settingY,
                            labelWidth, controlWidth, BUTTON_HEIGHT);
                } catch (IllegalAccessException ignored) {}
            }

            yOffset += settingsPerColumn * BUTTON_HEIGHT;
        }

        // Add collapsible Free Camera Return section header
        yOffset += spacing;
        String freeCamReturnKey = "freeCamReturnSection";
        boolean freeCamReturnExpanded = isSettingsExpanded(freeCamReturnKey);
        this.addRenderableWidget(Button.builder(
                Component.literal((freeCamReturnExpanded ? "▼ " : "▶ ") + "Free Camera Return Settings").withStyle(ChatFormatting.YELLOW),
                button -> {
                    toggleSettingsExpanded(freeCamReturnKey);
                    reinitialize();
                })
                .bounds(buttonX, baseY + yOffset, buttonWidth, BUTTON_HEIGHT)
                .build());
                
        if (freeCamReturnExpanded) {
            // Add settings for FreeCamReturnMovement
            yOffset += spacing;
            ninja.trek.cameramovements.movements.FreeCamReturnMovement freeCamReturn = GeneralMenuSettings.getFreeCamReturnMovement();
            
            // Position Easing slider
            this.addRenderableWidget(Button.builder(Component.literal("Position Easing"), button -> {})
                    .bounds(buttonX, baseY + yOffset, labelWidth, BUTTON_HEIGHT)
                    .build());
                    
            // Use reflection to get the current value
            double positionEasing = 0.2; // Default value
            try {
                java.lang.reflect.Field field = freeCamReturn.getClass().getDeclaredField("positionEasing");
                field.setAccessible(true);
                positionEasing = (double)field.get(freeCamReturn);
        } catch (Exception e) {
            // logging removed
        }
            
            this.addRenderableWidget(SettingWidget.createSlider(
                    buttonX + labelWidth + 10,
                    baseY + yOffset,
                    controlWidth,
                    BUTTON_HEIGHT,
                    Component.literal("Position Easing"),
                    0.01f,
                    1.0f,
                    positionEasing,
                    "positionEasing",
                    (AbstractMovementSettings)freeCamReturn
            ));
            
            // Position Speed Limit slider
            yOffset += spacing;
            this.addRenderableWidget(Button.builder(Component.literal("Position Speed Limit"), button -> {})
                    .bounds(buttonX, baseY + yOffset, labelWidth, BUTTON_HEIGHT)
                    .build());
                    
            // Use reflection to get the current value
            double positionSpeedLimit = 5.0; // Default value
            try {
                java.lang.reflect.Field field = freeCamReturn.getClass().getDeclaredField("positionSpeedLimit");
                field.setAccessible(true);
                positionSpeedLimit = (double)field.get(freeCamReturn);
        } catch (Exception e) {
            // logging removed
        }
            
            this.addRenderableWidget(SettingWidget.createSlider(
                    buttonX + labelWidth + 10,
                    baseY + yOffset,
                    controlWidth,
                    BUTTON_HEIGHT,
                    Component.literal("Position Speed Limit"),
                    0.1f,
                    200.0f,
                    positionSpeedLimit,
                    "positionSpeedLimit",
                    (AbstractMovementSettings)freeCamReturn
            ));
            
            // Rotation Easing slider
            yOffset += spacing;
            this.addRenderableWidget(Button.builder(Component.literal("Rotation Easing"), button -> {})
                    .bounds(buttonX, baseY + yOffset, labelWidth, BUTTON_HEIGHT)
                    .build());
                    
            // Use reflection to get the current value
            double rotationEasing = 0.2; // Default value
            try {
                java.lang.reflect.Field field = freeCamReturn.getClass().getDeclaredField("rotationEasing");
                field.setAccessible(true);
                rotationEasing = (double)field.get(freeCamReturn);
        } catch (Exception e) {
            // logging removed
        }
            
            this.addRenderableWidget(SettingWidget.createSlider(
                    buttonX + labelWidth + 10,
                    baseY + yOffset,
                    controlWidth,
                    BUTTON_HEIGHT,
                    Component.literal("Rotation Easing"),
                    0.01f,
                    1.0f,
                    rotationEasing,
                    "rotationEasing",
                    (AbstractMovementSettings)freeCamReturn
            ));
            
            // Rotation Speed Limit slider
            yOffset += spacing;
            this.addRenderableWidget(Button.builder(Component.literal("Rotation Speed Limit"), button -> {})
                    .bounds(buttonX, baseY + yOffset, labelWidth, BUTTON_HEIGHT)
                    .build());
                    
            // Use reflection to get the current value
            double rotationSpeedLimit = 90.0; // Default value
            try {
                java.lang.reflect.Field field = freeCamReturn.getClass().getDeclaredField("rotationSpeedLimit");
                field.setAccessible(true);
                rotationSpeedLimit = (double)field.get(freeCamReturn);
        } catch (Exception e) {
            // logging removed
        }
            
            this.addRenderableWidget(SettingWidget.createSlider(
                    buttonX + labelWidth + 10,
                    baseY + yOffset,
                    controlWidth,
                    BUTTON_HEIGHT,
                    Component.literal("Rotation Speed Limit"),
                    0.1f,
                    360.0f,
                    rotationSpeedLimit,
                    "rotationSpeedLimit",
                    (AbstractMovementSettings)freeCamReturn
            ));
        }
        
        // Update max scroll to handle the expanded/collapsed sections
        updateScrollBounds(yOffset + spacing);
    }
    private void addFollowerSettings() {
        int yOffset = CONTENT_START_Y + 20;
        int buttonWidth = 200;
        int buttonX = centerX + 20;
        int spacing = 25;
        int BUTTON_HEIGHT = 20;
        int totalWidth = guiWidth - 40;
        int labelWidth = Math.min(200, totalWidth / 3);
        int controlWidth = Math.min(200, totalWidth / 2);
        int baseY = centerY - scrollOffset;

        // Target Player Name
        this.addRenderableWidget(Button.builder(Component.literal("Target Player Name:"), button -> {})
                .bounds(buttonX, baseY + yOffset, labelWidth, BUTTON_HEIGHT)
                .build());

        followerTargetPlayerNameField = new EditBox(
                Minecraft.getInstance().font,
                buttonX + labelWidth + 20,
                baseY + yOffset,
                controlWidth,
                BUTTON_HEIGHT,
                Component.literal("Player name")
        );
        followerTargetPlayerNameField.setMaxLength(16);
        followerTargetPlayerNameField.setValue(followerConfig.getTargetPlayerName());
        followerTargetPlayerNameField.setResponder(text -> {
            followerConfig.setTargetPlayerName(text);
            FollowerSettingsIO.saveFollowers(followerConfig);
        });
        this.addRenderableWidget(followerTargetPlayerNameField);

        yOffset += spacing + 10;

        // Follower entries header
        this.addRenderableWidget(Button.builder(
                Component.literal("Follower Entries").withStyle(ChatFormatting.YELLOW), button -> {})
                .bounds(buttonX, baseY + yOffset, buttonWidth, BUTTON_HEIGHT)
                .build());

        // Add follower button
        int addBtnX = buttonX + buttonWidth + 10;
        this.addRenderableWidget(Button.builder(Component.literal("+ Add Follower"), button -> {
            followerConfig.addFollower(new FollowerEntry());
            FollowerSettingsIO.saveFollowers(followerConfig);
            reinitialize();
        }).bounds(addBtnX, baseY + yOffset, 100, BUTTON_HEIGHT).build());

        yOffset += spacing + 5;

        // Render each follower entry
        List<FollowerEntry> followers = followerConfig.getFollowers();
        for (int i = 0; i < followers.size(); i++) {
            final int followerIndex = i;
            FollowerEntry entry = followers.get(i);

            // Follower index label
            int controlX = buttonX;
            this.addRenderableWidget(Button.builder(
                    Component.literal("Follower " + i).withStyle(ChatFormatting.WHITE), button -> {})
                    .bounds(controlX, baseY + yOffset, 80, BUTTON_HEIGHT)
                    .build());
            controlX += 85;

            // Zones checkbox
            this.addRenderableWidget(Checkbox.builder(Component.literal("Zones"), Minecraft.getInstance().font)
                    .pos(controlX, baseY + yOffset)
                    .selected(entry.isUseZones())
                    .onValueChange((checkbox, checked) -> {
                        entry.setUseZones(checked);
                        FollowerSettingsIO.saveFollowers(followerConfig);
                    })
                    .build());
            controlX += 80;

            // Remove button
            this.addRenderableWidget(Button.builder(Component.literal("×"), button -> {
                followerConfig.removeFollower(followerIndex);
                expandedFollowers.remove(followerIndex);
                FollowerSettingsIO.saveFollowers(followerConfig);
                reinitialize();
            }).bounds(controlX, baseY + yOffset, 20, BUTTON_HEIGHT).build());
            controlX += 25;

            // Copy button (only if movement assigned)
            if (entry.getMovement() != null) {
                final ICameraMovement movementToCopy = entry.getMovement();
                this.addRenderableWidget(Button.builder(Component.literal("Copy"), button -> {
                    SlotSettingsIO.copyMovementToClipboard(movementToCopy);
                }).bounds(controlX, baseY + yOffset, 40, BUTTON_HEIGHT).build());
                controlX += 45;
            }

            // Paste button (always visible)
            this.addRenderableWidget(Button.builder(Component.literal("Paste"), button -> {
                try {
                    ICameraMovement newMovement = SlotSettingsIO.createMovementFromClipboard();
                    if (newMovement != null) {
                        entry.setMovement(newMovement);
                        FollowerSettingsIO.saveFollowers(followerConfig);
                        reinitialize();
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }).bounds(controlX, baseY + yOffset, 40, BUTTON_HEIGHT).build());
            controlX += 45;

            yOffset += spacing;

            // Movement settings
            {
                // Movement type selector
                String movementTypeName = "None";
                if (entry.getMovement() != null) {
                    movementTypeName = entry.getMovement() instanceof AbstractMovementSettings
                            ? ((AbstractMovementSettings) entry.getMovement()).getDisplayName()
                            : entry.getMovement().getName();
                }

                // Movement type cycle button
                List<CameraMovementRegistry.MovementInfo> allMovements = CameraMovementRegistry.getAllMovements();
                this.addRenderableWidget(Button.builder(
                        Component.literal("Type: " + movementTypeName), button -> {
                    if (allMovements.isEmpty()) return;
                    // Find current type index
                    int currentIdx = 0;
                    if (entry.getMovement() != null) {
                        for (int mi = 0; mi < allMovements.size(); mi++) {
                            if (allMovements.get(mi).getMovementClass().equals(entry.getMovement().getClass())) {
                                currentIdx = mi;
                                break;
                            }
                        }
                    }
                    int nextIdx = (currentIdx + 1) % allMovements.size();
                    try {
                        ICameraMovement newMovement = allMovements.get(nextIdx).getMovementClass()
                                .getDeclaredConstructor().newInstance();
                        entry.setMovement(newMovement);
                        FollowerSettingsIO.saveFollowers(followerConfig);
                        reinitialize();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }).bounds(buttonX + 20, baseY + yOffset, 200, BUTTON_HEIGHT).build());

                // Expand/collapse settings button
                if (entry.getMovement() != null) {
                    boolean expanded = expandedFollowers.getOrDefault(followerIndex, false);
                    this.addRenderableWidget(Button.builder(
                            Component.literal(expanded ? "▼ Settings" : "▶ Settings"), button -> {
                        expandedFollowers.put(followerIndex, !expandedFollowers.getOrDefault(followerIndex, false));
                        reinitialize();
                    }).bounds(buttonX + 225, baseY + yOffset, 80, BUTTON_HEIGHT).build());
                }

                yOffset += spacing;

                // Show movement settings if expanded
                if (entry.getMovement() instanceof AbstractMovementSettings settings
                        && expandedFollowers.getOrDefault(followerIndex, false)) {
                    List<Field> settingFields = new ArrayList<>();
                    collectSettingFields(settings, settingFields);

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
                            int settingX = centerX + 40 + column * (settingWidth + 20);
                            int settingY = baseY + yOffset + (row * BUTTON_HEIGHT);

                            createSettingControl(settings, field, annotation, settingX, settingY,
                                    labelWidth, controlWidth, BUTTON_HEIGHT);
                        } catch (IllegalAccessException ignored) {}
                    }

                    yOffset += settingsPerColumn * BUTTON_HEIGHT + 5;

                    // Save after any setting change - add a manual save button
                    this.addRenderableWidget(Button.builder(
                            Component.literal("Save Settings").withStyle(ChatFormatting.GREEN), button -> {
                        FollowerSettingsIO.saveFollowers(followerConfig);
                    }).bounds(buttonX + 40, baseY + yOffset, 100, BUTTON_HEIGHT).build());

                    yOffset += spacing;
                }
            }

            yOffset += 5; // Extra spacing between followers
        }

        if (followers.isEmpty()) {
            this.addRenderableWidget(Button.builder(
                    Component.literal("No followers configured").withStyle(ChatFormatting.GRAY), button -> {})
                    .bounds(buttonX, baseY + yOffset, buttonWidth, BUTTON_HEIGHT)
                    .build());
            yOffset += spacing;
        }

        updateScrollBounds(yOffset + spacing);
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
            Button enumButton = SettingWidget.createEnumButton(
                    settingX,
                    settingY,
                    labelWidth + controlWidth + 10,
                    BUTTON_HEIGHT,
                    field.getName(),
                    settings,
                    annotation
            );
            addRenderableWidget(enumButton);

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
                        Button warningButton = Button.builder(
                                        Component.literal("!").withStyle(ChatFormatting.GOLD),
                                        button -> {}
                                )
                                .bounds(settingX + labelWidth + controlWidth + 15, settingY, 20, BUTTON_HEIGHT)
                                .tooltip(Tooltip.create(Component.literal(
                                        "Warning: Camera rotation will be locked, Rotate Camera recommended")))
                                .build();

                        addRenderableWidget(warningButton);
                    }
                    } catch (Exception e) {
                        // logging removed
                    }
            }
        } else if (annotation.type() == MovementSettingType.BOOLEAN ||
                field.getType() == boolean.class || field.getType() == Boolean.class) {
            addRenderableWidget(Button.builder(Component.literal(annotation.label()), button -> {})
                    .bounds(settingX, settingY, labelWidth, BUTTON_HEIGHT)
                    .build());

            boolean checked = false;
            Object v = field.get(settings);
            if (v instanceof Boolean b) {
                checked = b;
            }

            addRenderableWidget(Checkbox.builder(Component.literal(""), Minecraft.getInstance().font)
                    .pos(settingX + labelWidth + 10, settingY)
                    .selected(checked)
                    .onValueChange((checkbox, isChecked) -> settings.updateSetting(field.getName(), isChecked))
                    .build());
        } else {
            // For non-enum settings, keep the original label + control layout
            addRenderableWidget(Button.builder(Component.literal(annotation.label()), button -> {})
                    .bounds(settingX, settingY, labelWidth, BUTTON_HEIGHT)
                    .build());
            addRenderableWidget(SettingWidget.createSlider(
                    settingX + labelWidth + 10,
                    settingY,
                    controlWidth,
                    BUTTON_HEIGHT,
                    Component.literal(annotation.label()),
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
        int contentHeight = yOffset - (CONTENT_START_Y + 20);
        int visibleHeight = guiHeight - CONTENT_START_Y - 30; // Additional padding
        maxScroll = Math.max(0, contentHeight - visibleHeight);
    }



    // In MenuOverlayScreen.java
    private void cycleMovementType() {
        List<CameraMovementRegistry.MovementInfo> movements = CameraMovementRegistry.getAllMovements();
        if (!movements.isEmpty()) {
            selectedMovementTypeIndex = (selectedMovementTypeIndex + 1) % movements.size();

            // Update the button text immediately
            for (GuiEventListener child : this.children()) {
                if (child instanceof Button button) {
                    String buttonText = button.getMessage().getString();
                    if (buttonText.startsWith("Type: ")) {
                        String currentTypeName = movements.get(selectedMovementTypeIndex).getName();
                        button.setMessage(Component.literal("Type: " + currentTypeName));
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
    public void resize(int width, int height) {
        super.resize(width, height);
        this.scrollOffset = 0;
        this.reinitialize();
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
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
                context.drawCenteredString(
                        Minecraft.getInstance().font,
                        Component.literal("▲"),
                        centerX + guiWidth - 15,
                        centerY + CONTENT_START_Y,
                        0xFFFFFF
                );
            }
            if (scrollOffset < maxScroll) {
                context.drawCenteredString(
                        Minecraft.getInstance().font,
                        Component.literal("▼"),
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
    
    // New methods for managing collapsible settings
    private boolean isSettingsExpanded(String settingsKey) {
        return expandedSettings.contains(settingsKey);
    }

    private void toggleSettingsExpanded(String settingsKey) {
        if (!expandedSettings.remove(settingsKey)) {
            expandedSettings.add(settingsKey);
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
        this.clearWidgets();
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
    public boolean keyPressed(net.minecraft.client.input.KeyEvent input) {
        // ESC
        if (input.input() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }

        // Inventory key
        if (this.minecraft != null && this.minecraft.options.keyInventory.matches(input)) {
            onClose();
            return true;
        }

        return super.keyPressed(input);
    }

    private void scroll(int amount) {
        if (maxScroll > 0) {
            scrollOffset = Math.max(0, Math.min(scrollOffset + amount, maxScroll));
            clearWidgets();
            init();
        }
    }

    private void switchTab(int index) {
        selectedTab = index;
        scrollOffset = 0;
        clearWidgets();
        init();
    }

    public void toggleMenu() {
        Minecraft client = Minecraft.getInstance();
        if (isMenuOpen) {
            onClose();
        } else {
            client.setScreen(this);
            isMenuOpen = true;
        }
    }

    @Override
    public void onClose() {
        // Save the current slots configuration before closing
        List<List<ICameraMovement>> slots = new ArrayList<>();
        for (int i = 0; i < CraneshotClient.MOVEMENT_MANAGER.getMovementCount(); i++) {
            slots.add(CraneshotClient.MOVEMENT_MANAGER.getAvailableMovementsForSlot(i));
        }
        SlotSettingsIO.saveSlots(slots);
        GeneralSettingsIO.saveSettings();
        if (followerConfig != null) {
            FollowerSettingsIO.saveFollowers(followerConfig);
        }

        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
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
            // logging removed
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Gets the current status text for spectator follow feature.
     */
    private String getSpectatorFollowStatus(Minecraft client) {
        if (client == null || client.player == null) {
            return "Not in game";
        }

        if (!client.player.isSpectator()) {
            return "Not in spectator mode";
        }

        if (!GeneralMenuSettings.isSpectatorFollowEnabled()) {
            return "Disabled - using local player";
        }

        String targetName = GeneralMenuSettings.getTargetPlayerName();
        if (targetName == null || targetName.trim().isEmpty()) {
            return "No target - using local player";
        }

        // Check if target player can be found
        if (client.level != null) {
            for (net.minecraft.world.entity.player.Player player : client.level.players()) {
                if (player.getName().getString().equalsIgnoreCase(targetName)) {
                    return "Following: " + player.getName().getString();
                }
            }
        }

        return "Target not found - using local player";
    }

    /**
     * Gets the color for the status indicator based on current state.
     */
    private ChatFormatting getSpectatorFollowStatusColor(Minecraft client) {
        if (client == null || client.player == null) {
            return ChatFormatting.RED;
        }

        if (!client.player.isSpectator()) {
            return ChatFormatting.GRAY;
        }

        if (!GeneralMenuSettings.isSpectatorFollowEnabled()) {
            return ChatFormatting.GRAY;
        }

        String targetName = GeneralMenuSettings.getTargetPlayerName();
        if (targetName == null || targetName.trim().isEmpty()) {
            return ChatFormatting.GRAY;
        }

        // Check if target player can be found
        if (client.level != null) {
            for (net.minecraft.world.entity.player.Player player : client.level.players()) {
                if (player.getName().getString().equalsIgnoreCase(targetName)) {
                    return ChatFormatting.GREEN; // Successfully following
                }
            }
        }

        return ChatFormatting.YELLOW; // Target not found
    }

    // Static methods for managing expanded settings
    public static Set<String> getExpandedSettings() {
        return expandedSettings;
    }
    
    public static void clearExpandedSettings() {
        expandedSettings.clear();
    }
    
    public static void addExpandedSetting(String key) {
        expandedSettings.add(key);
    }
}
