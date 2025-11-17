package ninja.trek.nodes.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.text.Text;
import ninja.trek.cameramovements.movements.StaticMovement;
import ninja.trek.nodes.NodeManager;
import ninja.trek.nodes.model.AreaInstance;
import ninja.trek.nodes.model.AreaMovementConfig;
import ninja.trek.nodes.model.AreaShape;
import ninja.trek.nodes.model.CameraNode;
import ninja.trek.nodes.model.EasingCurve;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class AreaSettingsModal extends Screen {
    private final AreaInstance area;
    private final Consumer<AreaInstance> onDone;
    private boolean showAdvanced;

    public AreaSettingsModal(AreaInstance area, Consumer<AreaInstance> onDone) {
        super(Text.literal("Area Settings"));
        this.area = area;
        this.onDone = onDone;
        this.showAdvanced = area.advanced;
    }

    @Override protected void init() {
        if (ensureStateFilterExclusivity()) {
            NodeManager.get().save();
        }
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

        // Advanced toggle
        addDrawableChild(ButtonWidget.builder(Text.literal(showAdvanced? "... (advanced on)" : "... (advanced off)"), b-> {
            showAdvanced = !showAdvanced;
            area.advanced = showAdvanced;
            b.setMessage(Text.literal(showAdvanced? "... (advanced on)" : "... (advanced off)"));
        }).dimensions(x,y,w*2,h).build());
        y+=h+sp;

        addDrawableChild(ButtonWidget.builder(Text.literal("Add Static Movement"), b -> {
            AreaMovementConfig cfg = new AreaMovementConfig();
            cfg.movementType = StaticMovement.MOVEMENT_ID;
            cfg.weight = 1.0f;
            CameraNode selected = NodeManager.get().getSelected();
            if (selected != null) {
                cfg.settings.put("positionNodeId", selected.id.toString());
            }
            area.movements.add(cfg);
            NodeManager.get().markAreaDirty(area.id);
            NodeManager.get().save();
            if (client != null) this.init(client, this.width, this.height);
        }).dimensions(x, y, w*2, h).build());
        y += h + sp;

        var stateKeys = NodeManager.getCanonicalStateKeys();

        for (AreaMovementConfig config : area.movements) {
            final AreaMovementConfig current = config;
            int rowY = y;

            addDrawableChild(ButtonWidget.builder(Text.literal("Use Sel Pos"), btn -> {
                CameraNode selected = NodeManager.get().getSelected();
                if (selected != null) {
                    current.settings.put("positionNodeId", selected.id.toString());
                    NodeManager.get().markAreaDirty(area.id);
                    NodeManager.get().save();
                    if (client != null) this.init(client, this.width, this.height);
                }
            }).dimensions(x, rowY, w, h).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("Use Sel Look"), btn -> {
                CameraNode selected = NodeManager.get().getSelected();
                if (selected != null) {
                    current.settings.put("lookNodeId", selected.id.toString());
                    NodeManager.get().markAreaDirty(area.id);
                    NodeManager.get().save();
                    if (client != null) this.init(client, this.width, this.height);
                }
            }).dimensions(x + w + sp, rowY, w, h).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("Clear Look"), btn -> {
                current.settings.remove("lookNodeId");
                NodeManager.get().markAreaDirty(area.id);
                NodeManager.get().save();
                if (client != null) this.init(client, this.width, this.height);
            }).dimensions(x + (w + sp) * 2, rowY, w, h).build());

            y = rowY + h + sp;

            addDrawableChild(ButtonWidget.builder(Text.literal("Weight -"), btn -> {
                current.weight = Math.max(0.0f, current.weight - 0.1f);
                NodeManager.get().markAreaDirty(area.id);
                NodeManager.get().save();
            }).dimensions(x, y, (w/2)-2, h).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("+"), btn -> {
                current.weight = Math.min(1.0f, current.weight + 0.1f);
                NodeManager.get().markAreaDirty(area.id);
                NodeManager.get().save();
            }).dimensions(x + (w/2) + 2, y, (w/2)-2, h).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("Remove"), btn -> {
                area.movements.remove(current);
                NodeManager.get().markAreaDirty(area.id);
                NodeManager.get().save();
                if (client != null) this.init(client, this.width, this.height);
            }).dimensions(x + w + sp, y, w, h).build());

            y += h + sp;

            int checkboxY = y;
            final int checkboxSpacing = 14;
            for (var key : stateKeys) {
                var stateKey = key;
                boolean selected = current.stateFilters.contains(stateKey.id());
                CheckboxWidget checkbox = CheckboxWidget.builder(Text.literal(stateKey.label()), this.textRenderer)
                        .pos(x, checkboxY)
                        .checked(selected)
                        .callback((cb, checkedValue) -> handleStateFilterToggle(current, stateKey, checkedValue))
                        .build();
                checkbox.setWidth(w * 2);
                addDrawableChild(checkbox);
                checkboxY += checkboxSpacing;
            }

            y = checkboxY + sp;
        }

        // Done
        addDrawableChild(ButtonWidget.builder(Text.literal("OK"), b -> {
            NodeManager.get().markAreaDirty(area.id);
            if (onDone != null) onDone.accept(area);
            if (client != null) client.setScreen(null);
        }).dimensions(x, y, w*2, h).build());
    }

    private void handleStateFilterToggle(AreaMovementConfig target, NodeManager.PlayerStateKey key, boolean checked) {
        String stateId = key.id();
        boolean changed = false;

        if (checked) {
            if (!target.stateFilters.contains(stateId)) {
                target.stateFilters.add(stateId);
                changed = true;
            }
            for (AreaMovementConfig cfg : area.movements) {
                if (cfg == target) continue;
                if (cfg.stateFilters.removeIf(stateId::equals)) {
                    normalizeStateFilters(cfg);
                    changed = true;
                }
            }
        } else {
            if (target.stateFilters.removeIf(stateId::equals)) {
                changed = true;
            }
        }

        if (normalizeStateFilters(target)) {
            changed = true;
        }

        if (changed) {
            NodeManager.get().markAreaDirty(area.id);
            NodeManager.get().save();
            if (client != null) {
                this.init(client, this.width, this.height);
            }
        }
    }

    private boolean ensureStateFilterExclusivity() {
        boolean changed = false;
        for (AreaMovementConfig cfg : area.movements) {
            if (normalizeStateFilters(cfg)) {
                changed = true;
            }
        }
        for (var key : NodeManager.getCanonicalStateKeys()) {
            String stateId = key.id();
            AreaMovementConfig owner = null;
            for (AreaMovementConfig cfg : area.movements) {
                if (cfg.stateFilters.contains(stateId)) {
                    if (owner == null) {
                        owner = cfg;
                    } else if (cfg.stateFilters.removeIf(stateId::equals)) {
                        changed = true;
                    }
                }
            }
        }
        if (changed) {
            for (AreaMovementConfig cfg : area.movements) {
                normalizeStateFilters(cfg);
            }
            NodeManager.get().markAreaDirty(area.id);
        }
        return changed;
    }

    private boolean normalizeStateFilters(AreaMovementConfig config) {
        List<String> original = new ArrayList<>(config.stateFilters);
        List<String> normalized = new ArrayList<>();
        for (var key : NodeManager.getCanonicalStateKeys()) {
            String id = key.id();
            if (original.remove(id)) {
                normalized.add(id);
            }
        }
        for (String leftover : original) {
            if (!normalized.contains(leftover)) {
                normalized.add(leftover);
            }
        }
        if (!normalized.equals(config.stateFilters)) {
            config.stateFilters.clear();
            config.stateFilters.addAll(normalized);
            return true;
        }
        return false;
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

        y += 8;
        if (area.movements.isEmpty()) {
            context.drawText(textRenderer, Text.literal("Movements: none"), left, y, 0xAAAAAA, true);
        } else {
            context.drawText(textRenderer, Text.literal("Movements:"), left, y, 0xFFFFFF, true);
            y += 12;
            int idx = 1;
            for (AreaMovementConfig config : area.movements) {
                String typeLabel = config.movementType != null ? config.movementType : "unknown";
                context.drawText(textRenderer, Text.literal(idx + ") " + typeLabel), left + 10, y, 0xFFFFFF, true);
                y += 12;

                UUID posId = parseUuid(config.settings.get("positionNodeId"));
                CameraNode posNode = posId != null ? NodeManager.get().getNode(posId) : null;
                String posName = posNode != null ? posNode.name : "None";
                context.drawText(textRenderer, Text.literal("Pos: " + posName), left + 20, y, 0xDDDDDD, true);
                y += 12;

                UUID lookId = parseUuid(config.settings.get("lookNodeId"));
                CameraNode lookNode = lookId != null ? NodeManager.get().getNode(lookId) : null;
                String lookName = lookNode != null ? lookNode.name : "None";
                context.drawText(textRenderer, Text.literal("Look: " + lookName), left + 20, y, 0xDDDDDD, true);
                y += 12;

                context.drawText(textRenderer, Text.literal(String.format("Weight: %.2f", config.weight)), left + 20, y, 0xDDDDDD, true);
                y += 16;
                idx++;
            }
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

    private interface DoubleSetter { void set(double v); }
    private int drawLabeledVec3(DrawContext ctx, int left, int y, String axis, java.util.function.Supplier<Double> getter, DoubleSetter setter) {
        String text = axis+": "+String.format("%.2f", getter.get());
        ctx.drawText(textRenderer, Text.literal(text+"   (+/- 1,10,100 with Ctrl/Shift & RMB)"), left+10, y, 0xDDDDDD, true);
        // Note: actual increment handling is in mouseClicked
        return y + 12;
    }

    private UUID parseUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof String s) {
            try {
                return UUID.fromString(s);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
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
            boolean changed = false;

            // helper to check if y within row
            java.util.function.BiPredicate<Integer,Integer> within = (yt, h)-> mouseY >= yt && mouseY < yt + h;

            // Position X/Y/Z rows (3 rows)
            int rowH = 12;
            if (within.test(yStart, rowH)) {
                double v = area.center.x + sign * inc;
                area.center = new net.minecraft.util.math.Vec3d(v, area.center.y, area.center.z);
                changed = true;
            } else if (within.test(yStart+rowH, rowH)) {
                double v = area.center.y + sign * inc;
                area.center = new net.minecraft.util.math.Vec3d(area.center.x, v, area.center.z);
                changed = true;
            } else if (within.test(yStart+rowH*2, rowH)) {
                double v = area.center.z + sign * inc;
                area.center = new net.minecraft.util.math.Vec3d(area.center.x, area.center.y, v);
                changed = true;
            } else if (showAdvanced) {
                int y = yStart + rowH*3 + 8 + 12; // skip header and go to inside radii
                if (area.insideRadii == null) area.insideRadii = new net.minecraft.util.math.Vec3d(area.insideRadius, area.insideRadius, area.insideRadius);
                if (area.outsideRadii == null) area.outsideRadii = new net.minecraft.util.math.Vec3d(area.outsideRadius, area.outsideRadius, area.outsideRadius);
                if (within.test(y, rowH)) {
                    area.insideRadii = new net.minecraft.util.math.Vec3d(Math.max(0, area.insideRadii.x + sign*inc), area.insideRadii.y, area.insideRadii.z);
                    changed = true;
                } else if (within.test(y+rowH, rowH)) {
                    area.insideRadii = new net.minecraft.util.math.Vec3d(area.insideRadii.x, Math.max(0, area.insideRadii.y + sign*inc), area.insideRadii.z);
                    changed = true;
                } else if (within.test(y+rowH*2, rowH)) {
                    area.insideRadii = new net.minecraft.util.math.Vec3d(area.insideRadii.x, area.insideRadii.y, Math.max(0, area.insideRadii.z + sign*inc));
                    changed = true;
                } else {
                    y += rowH*2 + rowH + 6 + 12; // skip to outside radii header then first row
                    if (within.test(y, rowH)) {
                        area.outsideRadii = new net.minecraft.util.math.Vec3d(Math.max(area.insideRadii.x, area.outsideRadii.x + sign*inc), area.outsideRadii.y, area.outsideRadii.z);
                        changed = true;
                    } else if (within.test(y+rowH, rowH)) {
                        area.outsideRadii = new net.minecraft.util.math.Vec3d(area.outsideRadii.x, Math.max(area.insideRadii.y, area.outsideRadii.y + sign*inc), area.outsideRadii.z);
                        changed = true;
                    } else if (within.test(y+rowH*2, rowH)) {
                        area.outsideRadii = new net.minecraft.util.math.Vec3d(area.outsideRadii.x, area.outsideRadii.y, Math.max(area.insideRadii.z, area.outsideRadii.z + sign*inc));
                        changed = true;
                    }
                }
            }
            if (changed) {
                NodeManager.get().markAreaDirty(area.id);
            }
        }
        return super.mouseClicked(click, fromInside);
    }
}
