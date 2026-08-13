package ninja.trek.nodes.ui;

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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class AreaSettingsModal extends Screen {
    private final AreaInstance area;
    private final Consumer<AreaInstance> onDone;
    private boolean showAdvanced;

    // Tracked Y positions for click hit-testing (set during render)
    private int posXRowY, posYRowY, posZRowY;
    private int insideXRowY, insideYRowY, insideZRowY;
    private int outsideXRowY, outsideYRowY, outsideZRowY;

    public AreaSettingsModal(AreaInstance area, Consumer<AreaInstance> onDone) {
        super(Component.literal("Area Settings"));
        this.area = area;
        this.onDone = onDone;
        this.showAdvanced = area.advanced;
    }

    @Override protected void init() {
        if (ensureStateFilterExclusivity()) {
            NodeManager.get().save();
        }
        int x=20,y=20,w=100,h=20,sp=5;
        addRenderableWidget(Button.builder(Component.literal("Type: "+area.shape), b-> {
            area.shape = (area.shape== AreaShape.CUBE? AreaShape.SPHERE: AreaShape.CUBE);
            b.setMessage(Component.literal("Type: "+area.shape));
        }).bounds(x,y,w*2,h).build());
        y+=h+sp;
        addRenderableWidget(Button.builder(Component.literal("Inside-"), b-> area.insideRadius=Math.max(0,area.insideRadius-1)).bounds(x,y,w,h).build());
        addRenderableWidget(Button.builder(Component.literal("Inside+"), b-> area.insideRadius=area.insideRadius+1).bounds(x+w+sp,y,w,h).build());
        y+=h+sp;
        addRenderableWidget(Button.builder(Component.literal("Outside-"), b-> area.outsideRadius=Math.max(area.insideRadius,area.outsideRadius-1)).bounds(x,y,w,h).build());
        addRenderableWidget(Button.builder(Component.literal("Outside+"), b-> area.outsideRadius=area.outsideRadius+1).bounds(x+w+sp,y,w,h).build());
        y+=h+sp;

        // Easing curve selector
        addRenderableWidget(Button.builder(Component.literal("Curve: "+area.easing), b-> {
            EasingCurve[] curves = EasingCurve.values();
            int idx = area.easing.ordinal();
            area.easing = curves[(idx+1)%curves.length];
            b.setMessage(Component.literal("Curve: "+area.easing));
        }).bounds(x,y,w*2,h).build());
        y+=h+sp;

        // Advanced toggle
        addRenderableWidget(Button.builder(Component.literal(showAdvanced? "... (advanced on)" : "... (advanced off)"), b-> {
            showAdvanced = !showAdvanced;
            area.advanced = showAdvanced;
            b.setMessage(Component.literal(showAdvanced? "... (advanced on)" : "... (advanced off)"));
        }).bounds(x,y,w*2,h).build());
        y+=h+sp;

        addRenderableWidget(Button.builder(Component.literal("Add Static Movement"), b -> {
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
            if (minecraft != null) this.init(this.width, this.height);
        }).bounds(x, y, w*2, h).build());
        y += h + sp;

        var stateKeys = NodeManager.getCanonicalStateKeys();

        for (AreaMovementConfig config : area.movements) {
            final AreaMovementConfig current = config;
            int rowY = y;

            addRenderableWidget(Button.builder(Component.literal("Use Sel Pos"), btn -> {
                CameraNode selected = NodeManager.get().getSelected();
                if (selected != null) {
                    current.settings.put("positionNodeId", selected.id.toString());
                    NodeManager.get().markAreaDirty(area.id);
                    NodeManager.get().save();
                    if (minecraft != null) this.init(this.width, this.height);
                }
            }).bounds(x, rowY, w, h).build());

            addRenderableWidget(Button.builder(Component.literal("Use Sel Look"), btn -> {
                CameraNode selected = NodeManager.get().getSelected();
                if (selected != null) {
                    current.settings.put("lookNodeId", selected.id.toString());
                    NodeManager.get().markAreaDirty(area.id);
                    NodeManager.get().save();
                    if (minecraft != null) this.init(this.width, this.height);
                }
            }).bounds(x + w + sp, rowY, w, h).build());

            addRenderableWidget(Button.builder(Component.literal("Clear Look"), btn -> {
                current.settings.remove("lookNodeId");
                NodeManager.get().markAreaDirty(area.id);
                NodeManager.get().save();
                if (minecraft != null) this.init(this.width, this.height);
            }).bounds(x + (w + sp) * 2, rowY, w, h).build());

            y = rowY + h + sp;

            addRenderableWidget(Button.builder(Component.literal("Weight -"), btn -> {
                current.weight = Math.max(0.0f, current.weight - 0.1f);
                NodeManager.get().markAreaDirty(area.id);
                NodeManager.get().save();
            }).bounds(x, y, (w/2)-2, h).build());
            addRenderableWidget(Button.builder(Component.literal("+"), btn -> {
                current.weight = Math.min(1.0f, current.weight + 0.1f);
                NodeManager.get().markAreaDirty(area.id);
                NodeManager.get().save();
            }).bounds(x + (w/2) + 2, y, (w/2)-2, h).build());

            addRenderableWidget(Button.builder(Component.literal("Remove"), btn -> {
                area.movements.remove(current);
                NodeManager.get().markAreaDirty(area.id);
                NodeManager.get().save();
                if (minecraft != null) this.init(this.width, this.height);
            }).bounds(x + w + sp, y, w, h).build());

            y += h + sp;

            int checkboxY = y;
            final int checkboxSpacing = 14;
            for (var key : stateKeys) {
                var stateKey = key;
                boolean selected = current.stateFilters.contains(stateKey.id());
                Checkbox checkbox = Checkbox.builder(Component.literal(stateKey.label()), this.font)
                        .pos(x, checkboxY)
                        .selected(selected)
                        .onValueChange((cb, checkedValue) -> handleStateFilterToggle(current, stateKey, checkedValue))
                        .build();
                checkbox.setWidth(w * 2);
                addRenderableWidget(checkbox);
                checkboxY += checkboxSpacing;
            }

            y = checkboxY + sp;
        }

        // Done
        addRenderableWidget(Button.builder(Component.literal("OK"), b -> {
            NodeManager.get().markAreaDirty(area.id);
            if (onDone != null) onDone.accept(area);
            if (minecraft != null) minecraft.gui.setScreen(null);
        }).bounds(x, y, w*2, h).build());
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
            if (minecraft != null) {
                this.init(this.width, this.height);
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

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void removed() {
        NodeManager.get().markAreaDirty(area.id);
        NodeManager.get().save();
        if (onDone != null) onDone.accept(area);
        super.removed();
    }

    @Override public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        int left = 20, top = 20 + 20*6; // below some buttons
        int y = top;
        // Draw center position labels with click-to-increment
        context.text(font, Component.literal("Position:"), left, y, 0xFFFFFF, true); y+=12;
        posXRowY = y;
        y = drawLabeledVec3(context, left, y, "X", ()-> area.center.x, d-> area.center = new net.minecraft.world.phys.Vec3(d, area.center.y, area.center.z));
        posYRowY = y;
        y = drawLabeledVec3(context, left, y, "Y", ()-> area.center.y, d-> area.center = new net.minecraft.world.phys.Vec3(area.center.x, d, area.center.z));
        posZRowY = y;
        y = drawLabeledVec3(context, left, y, "Z", ()-> area.center.z, d-> area.center = new net.minecraft.world.phys.Vec3(area.center.x, area.center.y, d));
        y += 8;
        if (showAdvanced) {
            // per-axis radii for inside/outside
            if (area.insideRadii == null) area.insideRadii = new net.minecraft.world.phys.Vec3(area.insideRadius, area.insideRadius, area.insideRadius);
            if (area.outsideRadii == null) area.outsideRadii = new net.minecraft.world.phys.Vec3(area.outsideRadius, area.outsideRadius, area.outsideRadius);
            context.text(font, Component.literal("Inside Radii:"), left, y, 0xFFFFFF, true); y+=12;
            insideXRowY = y;
            y = drawLabeledVec3(context, left, y, "X", ()-> area.insideRadii.x, d-> area.insideRadii = new net.minecraft.world.phys.Vec3(Math.max(0,d), area.insideRadii.y, area.insideRadii.z));
            insideYRowY = y;
            y = drawLabeledVec3(context, left, y, "Y", ()-> area.insideRadii.y, d-> area.insideRadii = new net.minecraft.world.phys.Vec3(area.insideRadii.x, Math.max(0,d), area.insideRadii.z));
            insideZRowY = y;
            y = drawLabeledVec3(context, left, y, "Z", ()-> area.insideRadii.z, d-> area.insideRadii = new net.minecraft.world.phys.Vec3(area.insideRadii.x, area.insideRadii.y, Math.max(0,d)));
            y += 6;
            context.text(font, Component.literal("Outside Radii:"), left, y, 0xFFFFFF, true); y+=12;
            outsideXRowY = y;
            y = drawLabeledVec3(context, left, y, "X", ()-> area.outsideRadii.x, d-> area.outsideRadii = new net.minecraft.world.phys.Vec3(Math.max(area.insideRadii!=null?area.insideRadii.x:0,d), area.outsideRadii.y, area.outsideRadii.z));
            outsideYRowY = y;
            y = drawLabeledVec3(context, left, y, "Y", ()-> area.outsideRadii.y, d-> area.outsideRadii = new net.minecraft.world.phys.Vec3(area.outsideRadii.x, Math.max(area.insideRadii!=null?area.insideRadii.y:0,d), area.outsideRadii.z));
            outsideZRowY = y;
            y = drawLabeledVec3(context, left, y, "Z", ()-> area.outsideRadii.z, d-> area.outsideRadii = new net.minecraft.world.phys.Vec3(area.outsideRadii.x, area.outsideRadii.y, Math.max(area.insideRadii!=null?area.insideRadii.z:0,d)));
        }

        y += 8;
        if (area.movements.isEmpty()) {
            context.text(font, Component.literal("Movements: none"), left, y, 0xAAAAAA, true);
        } else {
            context.text(font, Component.literal("Movements:"), left, y, 0xFFFFFF, true);
            y += 12;
            int idx = 1;
            for (AreaMovementConfig config : area.movements) {
                String typeLabel = config.movementType != null ? config.movementType : "unknown";
                context.text(font, Component.literal(idx + ") " + typeLabel), left + 10, y, 0xFFFFFF, true);
                y += 12;

                UUID posId = parseUuid(config.settings.get("positionNodeId"));
                CameraNode posNode = posId != null ? NodeManager.get().getNode(posId) : null;
                String posName = posNode != null ? posNode.name : "None";
                context.text(font, Component.literal("Pos: " + posName), left + 20, y, 0xDDDDDD, true);
                y += 12;

                UUID lookId = parseUuid(config.settings.get("lookNodeId"));
                CameraNode lookNode = lookId != null ? NodeManager.get().getNode(lookId) : null;
                String lookName = lookNode != null ? lookNode.name : "None";
                context.text(font, Component.literal("Look: " + lookName), left + 20, y, 0xDDDDDD, true);
                y += 12;

                context.text(font, Component.literal(String.format("Weight: %.2f", config.weight)), left + 20, y, 0xDDDDDD, true);
                y += 16;
                idx++;
            }
        }
    }

    @Override
    public void extractTransparentBackground(GuiGraphicsExtractor context) {
        // Disable translucent gradient/blur
    }

    @Override
    protected void extractBlurredBackground(GuiGraphicsExtractor context) {
        // no-op
    }

    private interface DoubleSetter { void set(double v); }
    private int drawLabeledVec3(GuiGraphicsExtractor ctx, int left, int y, String axis, java.util.function.Supplier<Double> getter, DoubleSetter setter) {
        String text = axis+": "+String.format("%.2f", getter.get());
        ctx.text(font, Component.literal(text+"   (+/- 1,10,100 with Ctrl/Shift & RMB)"), left+10, y, 0xDDDDDD, true);
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
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean fromInside) {
        // Handle position/radii label clicks for increments
        if (click != null) {
            boolean ctrl = false, shift = false;
            try {
                long win = net.minecraft.client.Minecraft.getInstance().getWindow().handle();
                ctrl = org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS
                        || org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
                shift = org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS
                        || org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
            } catch (Throwable ignored) {}
            int inc = shift ? 100 : (ctrl ? 10 : 1);
            boolean right = click.button() == 1;
            int sign = right ? -1 : 1;
            int mouseY = (int)click.y();
            boolean changed = false;
            int rowH = 12;

            // helper to check if y within row
            java.util.function.BiPredicate<Integer,Integer> within = (yt, h)-> mouseY >= yt && mouseY < yt + h;

            // Position X/Y/Z rows — use tracked Y positions from render()
            if (within.test(posXRowY, rowH)) {
                double v = area.center.x + sign * inc;
                area.center = new net.minecraft.world.phys.Vec3(v, area.center.y, area.center.z);
                changed = true;
            } else if (within.test(posYRowY, rowH)) {
                double v = area.center.y + sign * inc;
                area.center = new net.minecraft.world.phys.Vec3(area.center.x, v, area.center.z);
                changed = true;
            } else if (within.test(posZRowY, rowH)) {
                double v = area.center.z + sign * inc;
                area.center = new net.minecraft.world.phys.Vec3(area.center.x, area.center.y, v);
                changed = true;
            } else if (showAdvanced) {
                if (area.insideRadii == null) area.insideRadii = new net.minecraft.world.phys.Vec3(area.insideRadius, area.insideRadius, area.insideRadius);
                if (area.outsideRadii == null) area.outsideRadii = new net.minecraft.world.phys.Vec3(area.outsideRadius, area.outsideRadius, area.outsideRadius);
                if (within.test(insideXRowY, rowH)) {
                    area.insideRadii = new net.minecraft.world.phys.Vec3(Math.max(0, area.insideRadii.x + sign*inc), area.insideRadii.y, area.insideRadii.z);
                    changed = true;
                } else if (within.test(insideYRowY, rowH)) {
                    area.insideRadii = new net.minecraft.world.phys.Vec3(area.insideRadii.x, Math.max(0, area.insideRadii.y + sign*inc), area.insideRadii.z);
                    changed = true;
                } else if (within.test(insideZRowY, rowH)) {
                    area.insideRadii = new net.minecraft.world.phys.Vec3(area.insideRadii.x, area.insideRadii.y, Math.max(0, area.insideRadii.z + sign*inc));
                    changed = true;
                } else if (within.test(outsideXRowY, rowH)) {
                    area.outsideRadii = new net.minecraft.world.phys.Vec3(Math.max(area.insideRadii.x, area.outsideRadii.x + sign*inc), area.outsideRadii.y, area.outsideRadii.z);
                    changed = true;
                } else if (within.test(outsideYRowY, rowH)) {
                    area.outsideRadii = new net.minecraft.world.phys.Vec3(area.outsideRadii.x, Math.max(area.insideRadii.y, area.outsideRadii.y + sign*inc), area.outsideRadii.z);
                    changed = true;
                } else if (within.test(outsideZRowY, rowH)) {
                    area.outsideRadii = new net.minecraft.world.phys.Vec3(area.outsideRadii.x, area.outsideRadii.y, Math.max(area.insideRadii.z, area.outsideRadii.z + sign*inc));
                    changed = true;
                }
            }
            if (changed) {
                NodeManager.get().markAreaDirty(area.id);
            }
        }
        return super.mouseClicked(click, fromInside);
    }
}
