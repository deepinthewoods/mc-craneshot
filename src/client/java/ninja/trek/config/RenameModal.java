package ninja.trek.config;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.client.gui.DrawContext;
import ninja.trek.cameramovements.AbstractMovementSettings;

public class RenameModal extends Screen {
    private final Screen parent;
    private final AbstractMovementSettings movement;
    private TextFieldWidget nameField;
    private final Runnable onComplete;
    private static final int MODAL_WIDTH = 200;
    private static final int MODAL_HEIGHT = 100;

    public RenameModal(Screen parent, AbstractMovementSettings movement, Runnable onComplete) {
        super(Text.literal("Rename Movement"));
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
        nameField = new TextFieldWidget(
                textRenderer,
                modalLeft + 10,
                modalTop + 30,
                MODAL_WIDTH - 20,
                20,
                Text.literal("Name")
        );
        nameField.setText(movement.getDisplayName());
        nameField.setMaxLength(32);
        addSelectableChild(nameField);
        setInitialFocus(nameField);

        // Create buttons
        addDrawableChild(ButtonWidget.builder(Text.literal("Save"), button -> {
                    movement.setCustomName(nameField.getText());
                    if (onComplete != null) onComplete.run();
                    close();
                })
                .dimensions(modalLeft + 10, modalTop + MODAL_HEIGHT - 30, 80, 20)
                .build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> close())
                .dimensions(modalLeft + MODAL_WIDTH - 90, modalTop + MODAL_HEIGHT - 30, 80, 20)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (parent != null) {
           // parent.render(context, mouseX, mouseY, delta);
        }
        super.render(context, mouseX, mouseY, delta);

        int centerX = width / 2;
        int centerY = height / 2;
        int modalLeft = centerX - MODAL_WIDTH / 2;
        int modalTop = centerY - MODAL_HEIGHT / 2;

        // Draw modal background
        context.fill(modalLeft, modalTop, modalLeft + MODAL_WIDTH, modalTop + MODAL_HEIGHT, 0xF0000000);
        context.fill(modalLeft + 1, modalTop + 1, modalLeft + MODAL_WIDTH - 1, modalTop + MODAL_HEIGHT - 1, 0xFF444444);

        // Draw title
        context.drawCenteredTextWithShadow(textRenderer, "Rename Movement", centerX, modalTop + 10, 0xFFFFFF);

        nameField.render(context, mouseX, mouseY, delta);

    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}