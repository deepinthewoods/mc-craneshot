package ninja.trek.nodes.ui;

import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class NodeRenameModal extends Screen {
    private final String initialName;
    private final Consumer<String> onComplete;
    private EditBox nameField;
    private static final int MODAL_WIDTH = 220;
    private static final int MODAL_HEIGHT = 110;

    public NodeRenameModal(String initialName, Consumer<String> onComplete) {
        super(Component.literal("Rename Node"));
        this.initialName = initialName != null ? initialName : "Node";
        this.onComplete = onComplete;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int centerY = height / 2;
        int modalLeft = centerX - MODAL_WIDTH / 2;
        int modalTop = centerY - MODAL_HEIGHT / 2;

        nameField = new EditBox(
                font,
                modalLeft + 10,
                modalTop + 30,
                MODAL_WIDTH - 20,
                20,
                Component.literal("Name")
        );
        nameField.setValue(initialName);
        nameField.setMaxLength(64);
        addWidget(nameField);
        setInitialFocus(nameField);

        addRenderableWidget(Button.builder(Component.literal("Save"), btn -> {
                    if (onComplete != null) onComplete.accept(nameField.getValue());
                    onClose();
                })
                .bounds(modalLeft + 10, modalTop + MODAL_HEIGHT - 30, 90, 20)
                .build());

        addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> onClose())
                .bounds(modalLeft + MODAL_WIDTH - 100, modalTop + MODAL_HEIGHT - 30, 90, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int centerX = width / 2;
        int centerY = height / 2;
        int modalLeft = centerX - MODAL_WIDTH / 2;
        int modalTop = centerY - MODAL_HEIGHT / 2;
        context.fill(modalLeft, modalTop, modalLeft + MODAL_WIDTH, modalTop + MODAL_HEIGHT, 0xB0000000);
        context.fill(modalLeft + 1, modalTop + 1, modalLeft + MODAL_WIDTH - 1, modalTop + MODAL_HEIGHT - 1, 0xFF333333);
        context.drawCenteredString(font, "Rename Node", centerX, modalTop + 10, 0xFFFFFF);
        nameField.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}

