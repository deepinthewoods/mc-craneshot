package ninja.trek.nodes.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;

public class ColorPickerModal extends Screen {
    private float h = 0f, s = 1f, v = 1f;
    private Consumer<Integer> onPick;

    public ColorPickerModal(Consumer<Integer> onPick) {
        super(Text.literal("Color"));
        this.onPick = onPick;
    }

    @Override protected void init() {
        int x = 20, y = 20, w = 60, hgt = 20, sp=5;
        addDrawableChild(ButtonWidget.builder(Text.literal("Hue -"), b-> {h = (h+359)%360;}).dimensions(x,y,w,hgt).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Hue +"), b-> {h = (h+1)%360;}).dimensions(x+w+sp,y,w,hgt).build());
        y+=hgt+sp;
        addDrawableChild(ButtonWidget.builder(Text.literal("Sat -"), b-> {s = Math.max(0,s-0.05f);}).dimensions(x,y,w,hgt).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Sat +"), b-> {s = Math.min(1,s+0.05f);}).dimensions(x+w+sp,y,w,hgt).build());
        y+=hgt+sp;
        addDrawableChild(ButtonWidget.builder(Text.literal("Val -"), b-> {v = Math.max(0,v-0.05f);}).dimensions(x,y,w,hgt).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Val +"), b-> {v = Math.min(1,v+0.05f);}).dimensions(x+w+sp,y,w,hgt).build());
        y+=hgt+sp;
        addDrawableChild(ButtonWidget.builder(Text.literal("OK"), b-> {
            if (onPick != null) onPick.accept(hsvToArgb(h,s,v));
            if (client!=null) client.setScreen(null);
        }).dimensions(x,y,w*2+sp,hgt).build());
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // No background/blur – keep game view crisp while editing
        int col = hsvToArgb(h,s,v);
        context.fill(10, 10, 10+16, 10+16, col);
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

    private int hsvToArgb(float h, float s, float v) {
        float c = v * s;
        float x = c * (1 - Math.abs((h/60f) % 2 - 1));
        float m = v - c;
        float r=0,g=0,b=0;
        int hi = (int)(h/60f) % 6;
        switch (hi) {
            case 0 -> { r=c; g=x; b=0; }
            case 1 -> { r=x; g=c; b=0; }
            case 2 -> { r=0; g=c; b=x; }
            case 3 -> { r=0; g=x; b=c; }
            case 4 -> { r=x; g=0; b=c; }
            case 5 -> { r=c; g=0; b=x; }
        }
        int ri = (int)((r+m)*255), gi=(int)((g+m)*255), bi=(int)((b+m)*255);
        int a = 255;
        return (a<<24)|(ri<<16)|(gi<<8)|bi;
    }
}
