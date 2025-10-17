package ninja.trek.nodes.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import ninja.trek.nodes.model.Area;
import ninja.trek.nodes.model.AreaShape;

import java.util.function.Consumer;

public class AreaSettingsModal extends Screen {
    private final Area area;
    private final Consumer<Area> onDone;

    public AreaSettingsModal(Area area, Consumer<Area> onDone) {
        super(Text.literal("Area Settings"));
        this.area = area;
        this.onDone = onDone;
    }

    @Override protected void init() {
        int x=20,y=20,w=80,h=20,sp=5;
        addDrawableChild(ButtonWidget.builder(Text.literal("Type: "+area.shape), b-> {
            area.shape = (area.shape== AreaShape.CUBE? AreaShape.SPHERE: AreaShape.CUBE);
            b.setMessage(Text.literal("Type: "+area.shape));
        }).dimensions(x,y,w*2,h).build());
        y+=h+sp;
        addDrawableChild(ButtonWidget.builder(Text.literal("Min-"), b-> area.minRadius=Math.max(0,area.minRadius-1)).dimensions(x,y,w,h).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Min+"), b-> area.minRadius=area.minRadius+1).dimensions(x+w+sp,y,w,h).build());
        y+=h+sp;
        addDrawableChild(ButtonWidget.builder(Text.literal("Max-"), b-> area.maxRadius=Math.max(area.minRadius,area.maxRadius-1)).dimensions(x,y,w,h).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Max+"), b-> area.maxRadius=area.maxRadius+1).dimensions(x+w+sp,y,w,h).build());
        y+=h+sp;
        addDrawableChild(ButtonWidget.builder(Text.literal("OK"), b-> { if (onDone!=null) onDone.accept(area); if (client!=null) client.setScreen(null);} ).dimensions(x,y,w*2,h).build());
    }

    @Override public boolean shouldPause() { return false; }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // No background/blur
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void renderInGameBackground(DrawContext context) {
        // Disable translucent gradient/blur
    }

    @Override
    protected void applyBlur(DrawContext context) {
        // no-op
    }
}
