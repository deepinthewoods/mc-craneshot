package ninja.trek.config;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import ninja.trek.cameramovements.AbstractMovementSettings;

public class RenameModal extends Screen {
    private final Screen parent;
    private final AbstractMovementSettings movement;
    private EditBox nameField;
    private final Runnable onComplete;
    private static final int MODAL_WIDTH = 200;
    private static final int MODAL_HEIGHT = 100;

    public RenameModal(Screen parent, AbstractMovementSettings movement, Runnable onComplete) {
        super(Component.literal("Rename Movement"));
        this.parent = parent;
        this.movement = movement;
        this.onComplete = onComplete;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int centerY = height / 2;
        int modalLeft = centerX - MODAL_WIDTH / 2;
        int modalTop = centerY - MODAL_HEIGHT / 2;

        // Create text field
        nameField = new EditBox(
                font,
                modalLeft + 10,
                modalTop + 30,
                MODAL_WIDTH - 20,
                20,
                Component.literal("Name")
        );
        nameField.setValue(movement.getDisplayName());
        nameField.setMaxLength(32);
        addRenderableWidget(nameField);
        setInitialFocus(nameField);

        // Create buttons
        addRenderableWidget(Button.builder(Component.literal("Save"), button -> {
                    movement.setCustomName(nameField.getValue());
                    if (onComplete != null) onComplete.run();
                    onClose();
                })
                .bounds(modalLeft + 10, modalTop + MODAL_HEIGHT - 30, 80, 20)
                .build());

        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
                .bounds(modalLeft + MODAL_WIDTH - 90, modalTop + MODAL_HEIGHT - 30, 80, 20)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int centerX = width / 2;
        int centerY = height / 2;
        int modalLeft = centerX - MODAL_WIDTH / 2;
        int modalTop = centerY - MODAL_HEIGHT / 2;

        // Draw modal background
        context.fill(modalLeft, modalTop, modalLeft + MODAL_WIDTH, modalTop + MODAL_HEIGHT, 0xF0000000);
        context.fill(modalLeft + 1, modalTop + 1, modalLeft + MODAL_WIDTH - 1, modalTop + MODAL_HEIGHT - 1, 0xFF444444);

        // Draw title
        context.centeredText(font, "Rename Movement", centerX, modalTop + 10, 0xFFFFFF);

        // Extract widgets after the panel so the text field and buttons render on top.
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
