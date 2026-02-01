package ninja.trek.nodes.ui;

import ninja.trek.nodes.NodeManager;
import ninja.trek.nodes.model.CameraNode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;


public class TimelapseIndexConflictModal extends Screen {
    private static final int MODAL_WIDTH = 280;
    private static final int MODAL_HEIGHT = 110;

    private final int index;
    private final CameraNode conflicting;
    private final CameraNode current;
    private final Runnable onResolved;

    public TimelapseIndexConflictModal(int index, CameraNode conflicting, CameraNode current, Runnable onResolved) {
        super(Component.literal("Index Conflict"));
        this.index = index;
        this.conflicting = conflicting;
        this.current = current;
        this.onResolved = onResolved;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int centerY = height / 2;
        int modalLeft = centerX - MODAL_WIDTH / 2;
        int modalTop = centerY - MODAL_HEIGHT / 2;

        addRenderableWidget(Button.builder(Component.literal("Disable other node"), btn -> {
            conflicting.timelapseEnabled = false;
            current.timelapseIndex = index;
            NodeManager.get().save();
            if (onResolved != null) onResolved.run();
            onClose();
        }).bounds(modalLeft + 10, modalTop + MODAL_HEIGHT - 30, 120, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> onClose())
                .bounds(modalLeft + MODAL_WIDTH - 80, modalTop + MODAL_HEIGHT - 30, 70, 20).build());
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
        context.drawCenteredString(font, "Index Conflict", centerX, modalTop + 10, 0xFFFFFF);
        String msg = "Index " + index + " is non-follow and already has";
        String msg2 = "node '" + conflicting.name + "' assigned.";
        context.drawCenteredString(font, msg, centerX, modalTop + 28, 0xFFAAAA);
        context.drawCenteredString(font, msg2, centerX, modalTop + 40, 0xFFAAAA);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
