package ninja.trek.nodes.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import ninja.trek.nodes.model.Area;
import ninja.trek.nodes.model.AreaShape;
import ninja.trek.nodes.model.EasingCurve;

import java.util.function.Consumer;

public class AreaSettingsModal extends Screen {
    private final Area area;
    private final Consumer<Area> onDone;
    private boolean showAdvanced;

    public AreaSettingsModal(Area area, Consumer<Area> onDone) {
        super(Text.literal("Area Settings"));
        this.area = area;
        this.onDone = onDone;
        this.showAdvanced = area.advanced;
    }

    @Override protected void init() {
        int x=20,y=20,w=100,h=20,sp=5;
        addDrawableChild(ButtonWidget.builder(Text.literal("Type: "+area.shape), b-> {
            area.shape = (area.shape== AreaShape.CUBE? AreaShape.SPHERE: AreaShape.CUBE);
            b.setMessage(Text.literal("Type: "+area.shape));
        }).dimensions(x,y,w*2,h).build());
        y+=h+sp;
        addDrawableChild(ButtonWidget.builder(Text.literal("Inside-"), b-> area.insideRadius=Math.max(0,area.insideRadius-1)).dimensions(x,y,w,h).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Inside+"), b-> area.insideRadius=area.insideRadius+1).dimensions(x+w+sp,y,w,h).build());
        y+=h+sp;
        addDrawableChild(ButtonWidget.builder(Text.literal("Outside-"), b-> area.outsideRadius=Math.max(area.insideRadius,area.outsideRadius-1)).dimensions(x,y,w,h).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Outside+"), b-> area.outsideRadius=area.outsideRadius+1).dimensions(x+w+sp,y,w,h).build());
        y+=h+sp;

        // Easing curve selector
        addDrawableChild(ButtonWidget.builder(Text.literal("Curve: "+area.easing), b-> {
            EasingCurve[] curves = EasingCurve.values();
            int idx = area.easing.ordinal();
            area.easing = curves[(idx+1)%curves.length];
            b.setMessage(Text.literal("Curve: "+area.easing));
        }).dimensions(x,y,w*2,h).build());
        y+=h+sp;

        // Movement filters toggle buttons
        int fx = x, fy = y;
        fy = addFilterRow(fx, fy, "Walk", ()-> area.filterWalking, v-> area.filterWalking=v);
        fy = addFilterRow(fx, fy, "Sneak", ()-> area.filterSneaking, v-> area.filterSneaking=v);
        fy = addFilterRow(fx, fy, "Crawl1b", ()-> area.filterCrawling1Block, v-> area.filterCrawling1Block=v);
        fy = addFilterRow(fx, fy, "Swim", ()-> area.filterSwimming, v-> area.filterSwimming=v);
        fy = addFilterRow(fx, fy, "Elytra", ()-> area.filterElytra, v-> area.filterElytra=v);
        fy = addFilterRow(fx, fy, "Boat", ()-> area.filterBoat, v-> area.filterBoat=v);
        fy = addFilterRow(fx, fy, "Minecart", ()-> area.filterMinecart, v-> area.filterMinecart=v);
        fy = addFilterRow(fx, fy, "RideOther", ()-> area.filterRidingOther, v-> area.filterRidingOther=v);
        fy = addFilterRow(fx, fy, "RideGhast", ()-> area.filterRidingGhast, v-> area.filterRidingGhast=v);
        y = fy + sp;

        // Advanced toggle
        addDrawableChild(ButtonWidget.builder(Text.literal(showAdvanced? "... (advanced on)" : "... (advanced off)"), b-> {
            showAdvanced = !showAdvanced;
            area.advanced = showAdvanced;
            b.setMessage(Text.literal(showAdvanced? "... (advanced on)" : "... (advanced off)"));
        }).dimensions(x,y,w*2,h).build());
        y+=h+sp;

        // Done
        addDrawableChild(ButtonWidget.builder(Text.literal("OK"), b-> { if (onDone!=null) onDone.accept(area); if (client!=null) client.setScreen(null);} ).dimensions(x,y,w*2,h).build());
    }

    @Override public boolean shouldPause() { return false; }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int left = 20, top = 20 + 20*6; // below some buttons
        int y = top;
        // Draw center position labels with click-to-increment
        context.drawText(textRenderer, Text.literal("Position:"), left, y, 0xFFFFFF, true); y+=12;
        y = drawLabeledVec3(context, left, y, "X", ()-> area.center.x, d-> area.center = new net.minecraft.util.math.Vec3d(d, area.center.y, area.center.z));
        y = drawLabeledVec3(context, left, y, "Y", ()-> area.center.y, d-> area.center = new net.minecraft.util.math.Vec3d(area.center.x, d, area.center.z));
        y = drawLabeledVec3(context, left, y, "Z", ()-> area.center.z, d-> area.center = new net.minecraft.util.math.Vec3d(area.center.x, area.center.y, d));
        y += 8;
        if (showAdvanced) {
            // per-axis radii for inside/outside
            if (area.insideRadii == null) area.insideRadii = new net.minecraft.util.math.Vec3d(area.insideRadius, area.insideRadius, area.insideRadius);
            if (area.outsideRadii == null) area.outsideRadii = new net.minecraft.util.math.Vec3d(area.outsideRadius, area.outsideRadius, area.outsideRadius);
            context.drawText(textRenderer, Text.literal("Inside Radii:"), left, y, 0xFFFFFF, true); y+=12;
            y = drawLabeledVec3(context, left, y, "X", ()-> area.insideRadii.x, d-> area.insideRadii = new net.minecraft.util.math.Vec3d(Math.max(0,d), area.insideRadii.y, area.insideRadii.z));
            y = drawLabeledVec3(context, left, y, "Y", ()-> area.insideRadii.y, d-> area.insideRadii = new net.minecraft.util.math.Vec3d(area.insideRadii.x, Math.max(0,d), area.insideRadii.z));
            y = drawLabeledVec3(context, left, y, "Z", ()-> area.insideRadii.z, d-> area.insideRadii = new net.minecraft.util.math.Vec3d(area.insideRadii.x, area.insideRadii.y, Math.max(0,d)));
            y += 6;
            context.drawText(textRenderer, Text.literal("Outside Radii:"), left, y, 0xFFFFFF, true); y+=12;
            y = drawLabeledVec3(context, left, y, "X", ()-> area.outsideRadii.x, d-> area.outsideRadii = new net.minecraft.util.math.Vec3d(Math.max(area.insideRadii!=null?area.insideRadii.x:0,d), area.outsideRadii.y, area.outsideRadii.z));
            y = drawLabeledVec3(context, left, y, "Y", ()-> area.outsideRadii.y, d-> area.outsideRadii = new net.minecraft.util.math.Vec3d(area.outsideRadii.x, Math.max(area.insideRadii!=null?area.insideRadii.y:0,d), area.outsideRadii.z));
            y = drawLabeledVec3(context, left, y, "Z", ()-> area.outsideRadii.z, d-> area.outsideRadii = new net.minecraft.util.math.Vec3d(area.outsideRadii.x, area.outsideRadii.y, Math.max(area.insideRadii!=null?area.insideRadii.z:0,d)));
        }
    }

    @Override
    public void renderInGameBackground(DrawContext context) {
        // Disable translucent gradient/blur
    }

    @Override
    protected void applyBlur(DrawContext context) {
        // no-op
    }

    private int addFilterRow(int x, int y, String label, java.util.function.Supplier<Boolean> getter, java.util.function.Consumer<Boolean> setter) {
        Text t = Text.literal(label+": "+ (getter.get()?"ON":"OFF"));
        this.addDrawableChild(ButtonWidget.builder(t, b-> {
            boolean v = !getter.get();
            setter.accept(v);
            b.setMessage(Text.literal(label+": "+(v?"ON":"OFF")));
        }).dimensions(x, y, 120, 18).build());
        return y + 18 + 4;
    }

    private interface DoubleSetter { void set(double v); }
    private int drawLabeledVec3(DrawContext ctx, int left, int y, String axis, java.util.function.Supplier<Double> getter, DoubleSetter setter) {
        String text = axis+": "+String.format("%.2f", getter.get());
        ctx.drawText(textRenderer, Text.literal(text+"   (+/- 1,10,100 with Ctrl/Shift & RMB)"), left+10, y, 0xDDDDDD, true);
        // Note: actual increment handling is in mouseClicked
        return y + 12;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean fromInside) {
        // Handle position/radii label clicks for increments
        if (click != null) {
            boolean ctrl = false, shift = false;
            try {
                long win = net.minecraft.client.MinecraftClient.getInstance().getWindow().getHandle();
                ctrl = org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS
                        || org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
                shift = org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS
                        || org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
            } catch (Throwable ignored) {}
            int inc = shift ? 100 : (ctrl ? 10 : 1);
            boolean right = click.button() == 1;
            int sign = right ? -1 : 1;
            int left = 20;
            int yStart = 20 + 20*6 + 12; // start around first X label
            int mouseX = (int)click.x();
            int mouseY = (int)click.y();

            // helper to check if y within row
            java.util.function.BiPredicate<Integer,Integer> within = (yt, h)-> mouseY >= yt && mouseY < yt + h;

            // Position X/Y/Z rows (3 rows)
            int rowH = 12;
            if (within.test(yStart, rowH)) {
                double v = area.center.x + sign * inc;
                area.center = new net.minecraft.util.math.Vec3d(v, area.center.y, area.center.z);
            } else if (within.test(yStart+rowH, rowH)) {
                double v = area.center.y + sign * inc;
                area.center = new net.minecraft.util.math.Vec3d(area.center.x, v, area.center.z);
            } else if (within.test(yStart+rowH*2, rowH)) {
                double v = area.center.z + sign * inc;
                area.center = new net.minecraft.util.math.Vec3d(area.center.x, area.center.y, v);
            } else if (showAdvanced) {
                int y = yStart + rowH*3 + 8 + 12; // skip header and go to inside radii
                if (area.insideRadii == null) area.insideRadii = new net.minecraft.util.math.Vec3d(area.insideRadius, area.insideRadius, area.insideRadius);
                if (area.outsideRadii == null) area.outsideRadii = new net.minecraft.util.math.Vec3d(area.outsideRadius, area.outsideRadius, area.outsideRadius);
                if (within.test(y, rowH)) {
                    area.insideRadii = new net.minecraft.util.math.Vec3d(Math.max(0, area.insideRadii.x + sign*inc), area.insideRadii.y, area.insideRadii.z);
                } else if (within.test(y+rowH, rowH)) {
                    area.insideRadii = new net.minecraft.util.math.Vec3d(area.insideRadii.x, Math.max(0, area.insideRadii.y + sign*inc), area.insideRadii.z);
                } else if (within.test(y+rowH*2, rowH)) {
                    area.insideRadii = new net.minecraft.util.math.Vec3d(area.insideRadii.x, area.insideRadii.y, Math.max(0, area.insideRadii.z + sign*inc));
                } else {
                    y += rowH*2 + rowH + 6 + 12; // skip to outside radii header then first row
                    if (within.test(y, rowH)) {
                        area.outsideRadii = new net.minecraft.util.math.Vec3d(Math.max(area.insideRadii.x, area.outsideRadii.x + sign*inc), area.outsideRadii.y, area.outsideRadii.z);
                    } else if (within.test(y+rowH, rowH)) {
                        area.outsideRadii = new net.minecraft.util.math.Vec3d(area.outsideRadii.x, Math.max(area.insideRadii.y, area.outsideRadii.y + sign*inc), area.outsideRadii.z);
                    } else if (within.test(y+rowH*2, rowH)) {
                        area.outsideRadii = new net.minecraft.util.math.Vec3d(area.outsideRadii.x, area.outsideRadii.y, Math.max(area.insideRadii.z, area.outsideRadii.z + sign*inc));
                    }
                }
            }
        }
        return super.mouseClicked(click, fromInside);
    }
}
