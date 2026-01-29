package ninja.trek.nodes.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import ninja.trek.camera.CameraSystem;
import ninja.trek.nodes.NodeManager;
import ninja.trek.nodes.model.CameraNode;
import ninja.trek.nodes.model.AreaInstance;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public class NodeEditorScreen extends Screen {
    private double lastMouseX, lastMouseY;
    private boolean dragging = false;
    private long mouseDownTime = 0;
    private double mouseDownX = 0;
    private double mouseDownY = 0;
    private static final long CLICK_TIME_THRESHOLD_MS = 200;
    private static final double CLICK_MOVEMENT_THRESHOLD_PX = 5.0;
    private static final double AREA_CENTER_STEP = 1.0;

    private ButtonWidget areaXButton;
    private ButtonWidget areaYButton;
    private ButtonWidget areaZButton;

    private enum AreaAxis { X, Y, Z }

    public NodeEditorScreen() {
        super(Text.literal("Node Edit"));
    }

    @Override protected void init() {
        this.clearChildren();
        areaXButton = null;
        areaYButton = null;
        areaZButton = null;
        int rightX = this.width - 120;
        int y = 10;
        int w = 110, h = 20, sp=4;

        // Initialize edit rotation to current camera orientation
        net.minecraft.client.render.Camera cam0 = MinecraftClient.getInstance().gameRenderer.getCamera();
        if (cam0 != null) {
            ninja.trek.nodes.NodeManager.get().setEditRotation(cam0.getYaw(), cam0.getPitch());
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Color"), b-> {
            client.setScreen(new ColorPickerModal(col -> {
                CameraNode sel = NodeManager.get().getSelected();
                if (sel != null) { sel.colorARGB = col; NodeManager.get().save(); }
            }));
        }).dimensions(rightX,y,w,h).build()); y+=h+sp;

        // Node edit utility buttons
        addDrawableChild(ButtonWidget.builder(Text.literal("Rename"), b-> {
            CameraNode sel = NodeManager.get().getSelected();
            if (sel != null) {
                client.setScreen(new ninja.trek.nodes.ui.NodeRenameModal(sel.name, newName -> {
                    sel.name = newName;
                    NodeManager.get().save();
                    client.setScreen(this);
                }));
            }
        }).dimensions(rightX,y,w,h).build()); y+=h+sp;

        addDrawableChild(ButtonWidget.builder(Text.literal("Duplicate"), b-> {
            CameraNode sel = NodeManager.get().getSelected();
            if (sel != null) {
                CameraNode n = new CameraNode();
                n.name = sel.name + " Copy";
                n.type = sel.type;
                n.position = sel.position.add(0.25, 0, 0.25);
                n.colorARGB = sel.colorARGB;
                ninja.trek.nodes.NodeManager.get().getNodes();
                // add and select
                NodeManager.get().setSelected(NodeManager.get().addNode(n.position).id);
                // replace new node fields
                CameraNode added = NodeManager.get().getSelected();
                if (added != null) {
                    added.name = n.name;
                    added.type = n.type;
                    added.colorARGB = n.colorARGB;
                    NodeManager.get().save();
                }
                this.init(client, this.width, this.height);
            }
        }).dimensions(rightX,y,w,h).build()); y+=h+sp;

        

        // Export/Import node sets
        addDrawableChild(ButtonWidget.builder(Text.literal("Export"), b-> {
            boolean ok = ninja.trek.nodes.io.NodeStorage.exportData(
                    new java.util.ArrayList<>(NodeManager.get().getNodes()),
                    new java.util.ArrayList<>(NodeManager.get().getAreas()));
            if (!ok) {
                // no toast; silent
            }
        }).dimensions(rightX,y,w,h).build()); y+=h+sp;
        addDrawableChild(ButtonWidget.builder(Text.literal("Import"), b-> {
            ninja.trek.nodes.io.NodeStorage.Payload payload = ninja.trek.nodes.io.NodeStorage.importData();
            if (!payload.nodes.isEmpty() || !payload.areas.isEmpty()) {
                NodeManager.get().replaceAll(payload.nodes, payload.areas);
                this.init(client, this.width, this.height);
            }
        }).dimensions(rightX,y,w,h).build()); y+=h+sp;

        addDrawableChild(ButtonWidget.builder(Text.literal("Add Node"), b-> {
            Camera cam = MinecraftClient.getInstance().gameRenderer.getCamera();
            if (cam != null) {
                CameraNode n = NodeManager.get().addNode(cam.getPos());
                NodeManager.get().setSelected(n.id);
            }
        }).dimensions(rightX,y,w,h).build()); y+=h+sp;

        addDrawableChild(ButtonWidget.builder(Text.literal("Add Timelapse"), b-> {
            Camera cam = MinecraftClient.getInstance().gameRenderer.getCamera();
            if (cam != null) {
                float fovMul = 1.0f;
                try {
                    fovMul = ((ninja.trek.mixin.client.FovAccessor) MinecraftClient.getInstance().gameRenderer).getFovModifier();
                    if (fovMul == 0) fovMul = 1.0f;
                } catch (Throwable ignored) {}
                CameraNode n = NodeManager.get().addTimelapseNode(cam.getPos(), cam.getYaw(), cam.getPitch(), fovMul);
                NodeManager.get().setSelected(n.id);
                this.init(client, this.width, this.height);
            }
        }).dimensions(rightX,y,w,h).build()); y+=h+sp;

        addDrawableChild(ButtonWidget.builder(Text.literal("Delete Node"), b-> NodeManager.get().removeSelected())
                .dimensions(rightX,y,w,h).build()); y+=h+sp;

        addDrawableChild(ButtonWidget.builder(Text.literal("Add Area"), b-> {
            Camera cam = MinecraftClient.getInstance().gameRenderer.getCamera();
            Vec3d pos = cam != null ? cam.getPos() : Vec3d.ZERO;
            var area = NodeManager.get().addArea(pos);
            NodeManager.get().setSelectedArea(area.id);
            this.init(client, this.width, this.height);
        }).dimensions(rightX,y,w,h).build()); y+=h+sp;


        // Node type and DroneShot controls for selected node
        CameraNode selType = NodeManager.get().getSelected();
        if (selType != null) {
            addDrawableChild(ButtonWidget.builder(Text.literal("Type: "+selType.type), b-> {
                ninja.trek.nodes.model.NodeType[] types = ninja.trek.nodes.model.NodeType.values();
                int idx = selType.type.ordinal();
                selType.type = types[(idx + 1) % types.length];
                NodeManager.get().save();
                this.init(client, this.width, this.height);
            }).dimensions(rightX, y, w, h).build());
            y += h + sp;

            if (selType.type == ninja.trek.nodes.model.NodeType.TIMELAPSE) {
                // Update Camera button: re-capture current camera state into this timelapse node
                addDrawableChild(ButtonWidget.builder(Text.literal("Update Camera"), b-> {
                    Camera cam2 = MinecraftClient.getInstance().gameRenderer.getCamera();
                    if (cam2 != null) {
                        selType.position = cam2.getPos();
                        selType.timelapseYaw = cam2.getYaw();
                        selType.timelapsePitch = cam2.getPitch();
                        try {
                            float fov = ((ninja.trek.mixin.client.FovAccessor) MinecraftClient.getInstance().gameRenderer).getFovModifier();
                            if (fov != 0) selType.timelapseFovMultiplier = fov;
                        } catch (Throwable ignored) {}
                        NodeManager.get().save();
                        this.init(client, this.width, this.height);
                    }
                }).dimensions(rightX, y, w, h).build());
                y += h + sp;

                // Yaw +/- controls
                addDrawableChild(ButtonWidget.builder(Text.literal("Yaw -"), b-> {
                    selType.timelapseYaw -= 5f;
                    NodeManager.get().save();
                    this.init(client, this.width, this.height);
                }).dimensions(rightX, y, (w/2)-2, h).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("+"), b-> {
                    selType.timelapseYaw += 5f;
                    NodeManager.get().save();
                    this.init(client, this.width, this.height);
                }).dimensions(rightX + (w/2) + 2, y, (w/2)-2, h).build());
                y += h + sp;

                // Pitch +/- controls
                addDrawableChild(ButtonWidget.builder(Text.literal("Pitch -"), b-> {
                    selType.timelapsePitch = Math.max(-90f, selType.timelapsePitch - 5f);
                    NodeManager.get().save();
                    this.init(client, this.width, this.height);
                }).dimensions(rightX, y, (w/2)-2, h).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("+"), b-> {
                    selType.timelapsePitch = Math.min(90f, selType.timelapsePitch + 5f);
                    NodeManager.get().save();
                    this.init(client, this.width, this.height);
                }).dimensions(rightX + (w/2) + 2, y, (w/2)-2, h).build());
                y += h + sp;

                // FOV +/- controls
                addDrawableChild(ButtonWidget.builder(Text.literal("FOV -"), b-> {
                    selType.timelapseFovMultiplier = Math.max(0.1f, selType.timelapseFovMultiplier - 0.1f);
                    NodeManager.get().save();
                    this.init(client, this.width, this.height);
                }).dimensions(rightX, y, (w/2)-2, h).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("+"), b-> {
                    selType.timelapseFovMultiplier += 0.1f;
                    NodeManager.get().save();
                    this.init(client, this.width, this.height);
                }).dimensions(rightX + (w/2) + 2, y, (w/2)-2, h).build());
                y += h + sp;
            }

            if (selType.type == ninja.trek.nodes.model.NodeType.DRONE_SHOT) {
                // Radius controls
                addDrawableChild(ButtonWidget.builder(Text.literal("Radius -"), b-> {
                    selType.droneRadius = Math.max(0.5, selType.droneRadius - 0.5);
                    NodeManager.get().save();
                    this.init(client, this.width, this.height);
                }).dimensions(rightX, y, (w/2)-2, h).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("+"), b-> {
                    selType.droneRadius = selType.droneRadius + 0.5;
                    NodeManager.get().save();
                    this.init(client, this.width, this.height);
                }).dimensions(rightX + (w/2) + 2, y, (w/2)-2, h).build());
                y += h + sp;

                // Speed controls
                addDrawableChild(ButtonWidget.builder(Text.literal("Speed -"), b-> {
                    selType.droneSpeedDegPerSec = Math.max(0.0, selType.droneSpeedDegPerSec - 5.0);
                    NodeManager.get().save();
                    this.init(client, this.width, this.height);
                }).dimensions(rightX, y, (w/2)-2, h).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("+"), b-> {
                    selType.droneSpeedDegPerSec = selType.droneSpeedDegPerSec + 5.0;
                    NodeManager.get().save();
                    this.init(client, this.width, this.height);
                }).dimensions(rightX + (w/2) + 2, y, (w/2)-2, h).build());
                y += h + sp;
            }
        }

        int areaListX = this.width - 260;
        int areaListY = 10;
        int areaIdx = 0;
        AreaInstance selectedArea = NodeManager.get().getSelectedArea();
        for (AreaInstance area : NodeManager.get().getAreas()) {
            boolean isSelected = selectedArea != null && selectedArea.id.equals(area.id);
            String displayName = (area.name != null && !area.name.isBlank()) ? area.name : "Area " + areaIdx;
            if (isSelected) displayName = "* " + displayName;

            addDrawableChild(ButtonWidget.builder(Text.literal(displayName), b -> {
                NodeManager.get().setSelectedArea(area.id);
                client.setScreen(new AreaSettingsModal(area, updated -> {
                    NodeManager.get().save();
                    client.setScreen(this);
                }));
            }).dimensions(areaListX, areaListY, 140, 18).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("Sel"), b -> {
                NodeManager.get().setSelectedArea(area.id);
                this.init(client, this.width, this.height);
            }).dimensions(areaListX + 145, areaListY, 32, 18).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("-"), b -> {
                NodeManager.get().removeArea(area.id);
                this.init(client, this.width, this.height);
            }).dimensions(areaListX + 182, areaListY, 18, 18).build());

            areaListY += 22;
            areaIdx++;
        }

        selectedArea = NodeManager.get().getSelectedArea();
        if (selectedArea != null) {
            int coordWidth = 80;
            int coordHeight = 18;
            int coordSpacing = 4;
            int coordY = areaListY + 8;
            Vec3d center = selectedArea.center != null ? selectedArea.center : Vec3d.ZERO;
            areaXButton = ButtonWidget.builder(areaAxisText("X", center.x), b -> {
                adjustSelectedAreaCenter(AreaAxis.X, AREA_CENTER_STEP);
            }).dimensions(areaListX, coordY, coordWidth, coordHeight).build();
            areaXButton.setTooltip(Tooltip.of(Text.literal("Left click +1, Right click -1")));
            addDrawableChild(areaXButton);

            areaYButton = ButtonWidget.builder(areaAxisText("Y", center.y), b -> {
                adjustSelectedAreaCenter(AreaAxis.Y, AREA_CENTER_STEP);
            }).dimensions(areaListX + coordWidth + coordSpacing, coordY, coordWidth, coordHeight).build();
            areaYButton.setTooltip(Tooltip.of(Text.literal("Left click +1, Right click -1")));
            addDrawableChild(areaYButton);

            areaZButton = ButtonWidget.builder(areaAxisText("Z", center.z), b -> {
                adjustSelectedAreaCenter(AreaAxis.Z, AREA_CENTER_STEP);
            }).dimensions(areaListX + (coordWidth + coordSpacing) * 2, coordY, coordWidth, coordHeight).build();
            areaZButton.setTooltip(Tooltip.of(Text.literal("Left click +1, Right click -1")));
            addDrawableChild(areaZButton);
        }
        updateAreaCoordButtons();
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void tick() {
        super.tick();
        // Key forwarding is handled in keyPressed/keyReleased; no per-tick polling here.
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Render only our widgets; skip Screen default background/separators entirely
        for (net.minecraft.client.gui.Element e : this.children()) {
            if (e instanceof net.minecraft.client.gui.Drawable d) {
                d.render(context, mouseX, mouseY, delta);
            }
        }

        // Side info text (selected node + areas)
        int rightX = this.width - 240;
        int y = 10;
        CameraNode selNode = NodeManager.get().getSelected();
        if (selNode != null) {
            context.drawText(textRenderer, Text.literal("Node: "+selNode.name), rightX, y, 0xFFFFFF, true); y+=12;
            context.drawText(textRenderer, Text.literal("Type: "+selNode.type), rightX, y, 0xFFFFFF, true); y+=12;
            if (selNode.type == ninja.trek.nodes.model.NodeType.DRONE_SHOT) {
                context.drawText(textRenderer, Text.literal(String.format("Radius: %.1f", selNode.droneRadius)), rightX, y, 0xFFFFFF, true); y+=12;
                context.drawText(textRenderer, Text.literal(String.format("Speed: %.0f deg/s", selNode.droneSpeedDegPerSec)), rightX, y, 0xFFFFFF, true); y+=12;
            }
            if (selNode.type == ninja.trek.nodes.model.NodeType.TIMELAPSE) {
                context.drawText(textRenderer, Text.literal(String.format("Yaw: %.1f", selNode.timelapseYaw)), rightX, y, 0xFFFFFF, true); y+=12;
                context.drawText(textRenderer, Text.literal(String.format("Pitch: %.1f", selNode.timelapsePitch)), rightX, y, 0xFFFFFF, true); y+=12;
                context.drawText(textRenderer, Text.literal(String.format("FOV: %.2f", selNode.timelapseFovMultiplier)), rightX, y, 0xFFFFFF, true); y+=12;
            }
        } else {
            context.drawText(textRenderer, Text.literal("No node selected"), rightX, y, 0xAAAAAA, true); y+=12;
        }

        y += 6;

        AreaInstance selArea = NodeManager.get().getSelectedArea();
        if (selArea != null) {
            context.drawText(textRenderer, Text.literal("Area: "+(selArea.name != null ? selArea.name : selArea.id.toString())), rightX, y, 0xFFFFFF, true); y+=12;
            context.drawText(textRenderer, Text.literal("Shape: "+selArea.shape), rightX, y, 0xFFFFFF, true); y+=12;
            if (selArea.advanced && selArea.insideRadii != null && selArea.outsideRadii != null) {
                context.drawText(textRenderer, Text.literal(String.format("Inside: %.1f/%.1f/%.1f", selArea.insideRadii.x, selArea.insideRadii.y, selArea.insideRadii.z)), rightX, y, 0xFFFFFF, true); y+=12;
                context.drawText(textRenderer, Text.literal(String.format("Outside: %.1f/%.1f/%.1f", selArea.outsideRadii.x, selArea.outsideRadii.y, selArea.outsideRadii.z)), rightX, y, 0xFFFFFF, true); y+=12;
            } else {
                context.drawText(textRenderer, Text.literal(String.format("Inside: %.1f", selArea.insideRadius)), rightX, y, 0xFFFFFF, true); y+=12;
                context.drawText(textRenderer, Text.literal(String.format("Outside: %.1f", selArea.outsideRadius)), rightX, y, 0xFFFFFF, true); y+=12;
            }
            context.drawText(textRenderer, Text.literal("Curve: "+selArea.easing), rightX, y, 0xFFFFFF, true); y+=12;
        } else {
            context.drawText(textRenderer, Text.literal("No area selected"), rightX, y, 0xAAAAAA, true);
        }
    }

    // Disable the default translucent in-game gradient background to avoid flicker
    @Override
    public void renderInGameBackground(DrawContext context) {
        // no-op: keep full game view without gradient/blur
    }

    // Belt/world blur guard: override to prevent any blur application
    @Override
    protected void applyBlur(DrawContext context) {
        // no-op
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        // Close and start return on ESC
        if (input.getKeycode() == GLFW.GLFW_KEY_ESCAPE) {
            closeAndStartReturn();
            return true;
        }

        // If user hits the active slot hotkey again while editing, close and start return
        if (this.client != null) {
            Integer activeSlot = ninja.trek.CraneshotClient.MOVEMENT_MANAGER.getActiveMovementSlot();
            if (activeSlot != null && activeSlot >= 0 && activeSlot < ninja.trek.CraneshotClient.cameraKeyBinds.length) {
                net.minecraft.client.option.KeyBinding kb = ninja.trek.CraneshotClient.cameraKeyBinds[activeSlot];
                if (kb != null && kb.matchesKey(input)) {
                    closeAndStartReturn();
                    return true;
                }
            }
        }

        // Forward movement keys by bound mapping so freecam can move while screen is open
        if (this.client != null) {
            var o = this.client.options;
            if (o.forwardKey.matchesKey(input)) { o.forwardKey.setPressed(true); return true; }
            if (o.backKey.matchesKey(input)) { o.backKey.setPressed(true); return true; }
            if (o.leftKey.matchesKey(input)) { o.leftKey.setPressed(true); return true; }
            if (o.rightKey.matchesKey(input)) { o.rightKey.setPressed(true); return true; }
            if (o.jumpKey.matchesKey(input)) { o.jumpKey.setPressed(true); return true; }
            if (o.sneakKey.matchesKey(input)) { o.sneakKey.setPressed(true); return true; }
            if (o.sprintKey.matchesKey(input)) { o.sprintKey.setPressed(true); return true; }
        }

        // Let super handle other UI keys
        return super.keyPressed(input);
    }

    private void closeAndStartReturn() {
        // Mark node editing off and start camera return
        ninja.trek.nodes.NodeManager.get().setEditing(false);
        // Clear any forwarded key states to avoid stuck keys
        clearMovementKeys();
        if (this.client != null) {
            ninja.trek.CraneshotClient.MOVEMENT_MANAGER.finishTransition(this.client, this.client.gameRenderer.getCamera());
            this.client.setScreen(null);
        }
    }

    @Override
    public void removed() {
        // Ensure keys are cleared if the screen is closed by any means
        clearMovementKeys();
        // Ensure cursor is released when screen is closed
        if (client != null && client.getWindow() != null) {
            GLFW.glfwSetInputMode(client.getWindow().getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        }
        super.removed();
    }

    @Override
    public boolean mouseClicked(Click click, boolean fromInside) {
        if (click != null) {
            if (handleAreaCoordinateRightClick(click)) {
                return true;
            }
            dragging = true;
            mouseDownTime = System.currentTimeMillis();
            mouseDownX = click.x();
            mouseDownY = click.y();

            // Capture mouse cursor when dragging starts
            if (client != null && client.getWindow() != null) {
                GLFW.glfwSetInputMode(client.getWindow().getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
            }
        }
        return super.mouseClicked(click, fromInside);
    }

    @Override
    public boolean mouseReleased(Click click) {
        // Release mouse cursor when dragging ends
        if (client != null && client.getWindow() != null) {
            GLFW.glfwSetInputMode(client.getWindow().getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        }

        dragging = false;

        // Only select a node if this was a quick click (< 200ms) without significant movement
        if (click != null && mouseDownTime > 0) {
            long clickDuration = System.currentTimeMillis() - mouseDownTime;
            double dx = click.x() - mouseDownX;
            double dy = click.y() - mouseDownY;
            double distance = Math.sqrt(dx * dx + dy * dy);

            if (clickDuration < CLICK_TIME_THRESHOLD_MS && distance < CLICK_MOVEMENT_THRESHOLD_PX) {
                // This was a click, not a drag - select the node
                try {
                    Camera cam = MinecraftClient.getInstance().gameRenderer.getCamera();
                    if (cam != null) {
                        NodeManager.get().selectNearestToScreen(click.x(), click.y(), this.width, this.height, cam);
                        this.init(client, this.width, this.height);
                    }
                } catch (Throwable t) {
                    // Fallback: select center
                    Camera cam = MinecraftClient.getInstance().gameRenderer.getCamera();
                    if (cam != null) {
                        NodeManager.get().selectNearestToScreen(this.width/2.0, this.height/2.0, this.width, this.height, cam);
                        this.init(client, this.width, this.height);
                    }
                }
            }
        }

        mouseDownTime = 0;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (dragging) {
            // 3x sensitivity for node editing
            double sens = MinecraftClient.getInstance().options.getMouseSensitivity().getValue();
            double calc = 0.6 * sens * sens * sens + 0.2;
            calc *= 3.0;

            // Rotate using regular freecam pipeline: entity if present, otherwise CameraSystem
            ninja.trek.util.CameraEntity camEnt = ninja.trek.util.CameraEntity.getCamera();
            if (camEnt != null) {
                // CameraEntity.updateCameraRotations applies 0.15F internally, so just pass calc
                // Invert deltaY for intuitive up/down control
                camEnt.updateCameraRotations((float)(deltaX * calc), (float)(deltaY * calc));
            } else {
                // CameraSystem.updateRotation expects pre-multiplied values
                // Invert deltaY for intuitive up/down control
                ninja.trek.camera.CameraSystem.getInstance().updateRotation(deltaX * calc * 0.55, -deltaY * calc * 0.55, 1.0);
            }
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    // Reset movement key pressed states
    private void clearMovementKeys() {
        if (this.client == null) return;
        var opts = this.client.options;
        opts.forwardKey.setPressed(false);
        opts.backKey.setPressed(false);
        opts.leftKey.setPressed(false);
        opts.rightKey.setPressed(false);
        opts.jumpKey.setPressed(false);
        opts.sneakKey.setPressed(false);
        opts.sprintKey.setPressed(false);
    }

    // (keyPressed override lives earlier in the file)

    @Override
    public boolean keyReleased(KeyInput input) {
        if (this.client != null) {
            var o = this.client.options;
            if (o.forwardKey.matchesKey(input)) { o.forwardKey.setPressed(false); return true; }
            if (o.backKey.matchesKey(input)) { o.backKey.setPressed(false); return true; }
            if (o.leftKey.matchesKey(input)) { o.leftKey.setPressed(false); return true; }
            if (o.rightKey.matchesKey(input)) { o.rightKey.setPressed(false); return true; }
            if (o.jumpKey.matchesKey(input)) { o.jumpKey.setPressed(false); return true; }
            if (o.sneakKey.matchesKey(input)) { o.sneakKey.setPressed(false); return true; }
            if (o.sprintKey.matchesKey(input)) { o.sprintKey.setPressed(false); return true; }
        }
        return super.keyReleased(input);
    }

    private boolean handleAreaCoordinateRightClick(Click click) {
        if (click == null || click.button() != 1) {
            return false;
        }
        double mx = click.x();
        double my = click.y();
        var soundManager = MinecraftClient.getInstance().getSoundManager();
        if (areaXButton != null && areaXButton.active && areaXButton.isMouseOver(mx, my)) {
            areaXButton.playDownSound(soundManager);
            adjustSelectedAreaCenter(AreaAxis.X, -AREA_CENTER_STEP);
            return true;
        }
        if (areaYButton != null && areaYButton.active && areaYButton.isMouseOver(mx, my)) {
            areaYButton.playDownSound(soundManager);
            adjustSelectedAreaCenter(AreaAxis.Y, -AREA_CENTER_STEP);
            return true;
        }
        if (areaZButton != null && areaZButton.active && areaZButton.isMouseOver(mx, my)) {
            areaZButton.playDownSound(soundManager);
            adjustSelectedAreaCenter(AreaAxis.Z, -AREA_CENTER_STEP);
            return true;
        }
        return false;
    }

    private void adjustSelectedAreaCenter(AreaAxis axis, double delta) {
        AreaInstance area = NodeManager.get().getSelectedArea();
        if (area == null) {
            return;
        }
        Vec3d center = area.center != null ? area.center : Vec3d.ZERO;
        double x = center.x;
        double y = center.y;
        double z = center.z;
        switch (axis) {
            case X -> x += delta;
            case Y -> y += delta;
            case Z -> z += delta;
        }
        area.center = new Vec3d(x, y, z);
        NodeManager.get().markAreaDirty(area.id);
        NodeManager.get().save();
        updateAreaCoordButtons();
    }

    private void updateAreaCoordButtons() {
        AreaInstance area = NodeManager.get().getSelectedArea();
        if (area == null || area.center == null) {
            if (areaXButton != null) {
                areaXButton.active = false;
                areaXButton.setMessage(areaAxisPlaceholder("X"));
            }
            if (areaYButton != null) {
                areaYButton.active = false;
                areaYButton.setMessage(areaAxisPlaceholder("Y"));
            }
            if (areaZButton != null) {
                areaZButton.active = false;
                areaZButton.setMessage(areaAxisPlaceholder("Z"));
            }
            return;
        }
        if (areaXButton != null) {
            areaXButton.active = true;
            areaXButton.setMessage(areaAxisText("X", area.center.x));
        }
        if (areaYButton != null) {
            areaYButton.active = true;
            areaYButton.setMessage(areaAxisText("Y", area.center.y));
        }
        if (areaZButton != null) {
            areaZButton.active = true;
            areaZButton.setMessage(areaAxisText("Z", area.center.z));
        }
    }

    private static Text areaAxisText(String axis, double value) {
        return Text.literal(String.format(Locale.US, "%s: %.1f", axis, value));
    }

    private static Text areaAxisPlaceholder(String axis) {
        return Text.literal(axis + ": --");
    }
}

