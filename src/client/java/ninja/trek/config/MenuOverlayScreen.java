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
    private static final Map<Integer, Set<Integer>> expandedMovements = new HashMap<>(); // Slot -> Set of expanded movement indices
    private static final int MARGIN = 20; // Margin from screen edges
    private static final int TAB_HEIGHT = 30;
    private static final int CONTENT_START_Y = TAB_HEIGHT - 10;
    private static final double SCROLL_SPEED = 10;
    private static boolean isMenuOpen = false;
    private int selectedTab = 0;
    private final List<SettingSlider> settingSliders = new ArrayList<>();
    private int scrollOffset = 0;
    private int maxScroll = 0;

    // Dynamic dimensions
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
        // Calculate dynamic dimensions
        this.guiWidth = this.width - (MARGIN * 2);
        this.guiHeight = this.height - (MARGIN * 2);
        this.centerX = MARGIN;
        this.centerY = MARGIN;

        int visibleStartY = centerY + CONTENT_START_Y;
        int visibleEndY = centerY + guiHeight;
        int height = 0;

        // Calculate tab width based on available space
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

        // Add slot-specific controls if not on general tab
        if (selectedTab > 0) {
            int slotIndex = selectedTab - 1;

            // Always show Add Movement button and Wrap toggle at the top
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

            // Movement list with settings
            List<ICameraMovement> movements = CraneshotClient.CAMERA_CONTROLLER.getAvailableMovementsForSlot(slotIndex);
            int yOffset = CONTENT_START_Y + BUTTON_HEIGHT + 10;

            for (int i = 0; i < movements.size(); i++) {
                int index = i;
                ICameraMovement movement = movements.get(i);
                int rowY = centerY + yOffset - scrollOffset;

                // Only add visible elements
                if (rowY >= visibleStartY - BUTTON_HEIGHT && rowY <= visibleEndY) {
                    // Movement header
                    int movementButtonWidth = Math.min(200, guiWidth / 3);
                    this.addDrawableChild(ButtonWidget.builder(
                                    Text.literal((isMovementExpanded(slotIndex, index) ? "▼ " : "▶ ") + movement.getName()),
                                    button -> {
                                        toggleMovementExpanded(slotIndex, index);
                                        reinitialize();
                                    })
                            .dimensions(centerX + 10, rowY, movementButtonWidth, BUTTON_HEIGHT)
                            .build());

                    // Control buttons positioned relative to GUI width
                    int controlsStartX = centerX + guiWidth - 75;

                    if (i > 0) {
                        this.addDrawableChild(ButtonWidget.builder(
                                        Text.literal("↑"),
                                        button -> moveMovement(slotIndex, index, index - 1)
                                )
                                .dimensions(controlsStartX, rowY, 20, BUTTON_HEIGHT)
                                .build());
                    }

                    if (i < movements.size() - 1) {
                        this.addDrawableChild(ButtonWidget.builder(
                                        Text.literal("↓"),
                                        button -> moveMovement(slotIndex, index, index + 1)
                                )
                                .dimensions(controlsStartX + 25, rowY, 20, BUTTON_HEIGHT)
                                .build());
                    }

                    if (movements.size() > 1) {
                        this.addDrawableChild(ButtonWidget.builder(
                                        Text.literal("×"),
                                        button -> deleteMovement(slotIndex, index)
                                )
                                .dimensions(controlsStartX + 50, rowY, 20, BUTTON_HEIGHT)
                                .build());
                    }
                }

                yOffset += MOVEMENT_ROW_HEIGHT;

                // Add settings if movement is expanded
                if (movement instanceof AbstractMovementSettings settings && isMovementExpanded(slotIndex, index)) {
                    for (Field field : settings.getClass().getDeclaredFields()) {
                        if (field.isAnnotationPresent(MovementSetting.class)) {
                            MovementSetting annotation = field.getAnnotation(MovementSetting.class);
                            field.setAccessible(true);

                            try {
                                double value = ((Number) field.get(settings)).doubleValue();
                                int settingY = centerY + yOffset - scrollOffset;

                                // Only add visible settings
                                if (settingY >= visibleStartY - BUTTON_HEIGHT && settingY <= visibleEndY) {
                                    // Calculate widths based on available space
                                    int labelWidth = Math.min(150, guiWidth / 4);
                                    int sliderWidth = Math.min(200, guiWidth / 2);

                                    // Add setting label
                                    this.addDrawableChild(ButtonWidget.builder(
                                                    Text.literal(annotation.label()),
                                                    button -> {}
                                            )
                                            .dimensions(centerX + 20, settingY, labelWidth, BUTTON_HEIGHT)
                                            .build());

                                    // Add setting slider
                                    SettingSlider slider = new SettingSlider(
                                            centerX + labelWidth + 30,
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
                                yOffset += SETTING_HEIGHT;
                            } catch (IllegalAccessException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }

                yOffset += MOVEMENT_SPACING;
            }

            // Calculate max scroll based on content height
            int contentHeight = yOffset - (CONTENT_START_Y + BUTTON_HEIGHT + 10);
            int visibleHeight = guiHeight - CONTENT_START_Y - 10;
            maxScroll = Math.max(0, contentHeight - visibleHeight);
        }
    }

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