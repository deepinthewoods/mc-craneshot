package ninja.trek.nodes.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;

public class NodeRenameModal extends Screen {
    private final String initialName;
    private final Consumer<String> onComplete;
    private TextFieldWidget nameField;
    private static final int MODAL_WIDTH = 220;
    private static final int MODAL_HEIGHT = 110;

    public NodeRenameModal(String initialName, Consumer<String> onComplete) {
        super(Text.literal("Rename Node"));
        this.initialName = initialName != null ? initialName : "Node";
        this.onComplete = onComplete;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int centerY = height / 2;
        int modalLeft = centerX - MODAL_WIDTH / 2;
        int modalTop = centerY - MODAL_HEIGHT / 2;

        nameField = new TextFieldWidget(
                textRenderer,
                modalLeft + 10,
                modalTop + 30,
                MODAL_WIDTH - 20,
                20,
                Text.literal("Name")
        );
        nameField.setText(initialName);
        nameField.setMaxLength(64);
        addSelectableChild(nameField);
        setInitialFocus(nameField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Save"), btn -> {
                    if (onComplete != null) onComplete.accept(nameField.getText());
                    close();
                })
                .dimensions(modalLeft + 10, modalTop + MODAL_HEIGHT - 30, 90, 20)
                .build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), btn -> close())
                .dimensions(modalLeft + MODAL_WIDTH - 100, modalTop + MODAL_HEIGHT - 30, 90, 20)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int centerX = width / 2;
        int centerY = height / 2;
        int modalLeft = centerX - MODAL_WIDTH / 2;
        int modalTop = centerY - MODAL_HEIGHT / 2;
        context.fill(modalLeft, modalTop, modalLeft + MODAL_WIDTH, modalTop + MODAL_HEIGHT, 0xB0000000);
        context.fill(modalLeft + 1, modalTop + 1, modalLeft + MODAL_WIDTH - 1, modalTop + MODAL_HEIGHT - 1, 0xFF333333);
        context.drawCenteredTextWithShadow(textRenderer, "Rename Node", centerX, modalTop + 10, 0xFFFFFF);
        nameField.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(null);
    }

    @Override
    public boolean shouldPause() { return false; }
}

