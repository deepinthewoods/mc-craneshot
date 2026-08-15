package ninja.trek.nodes.ui;

import ninja.trek.camera.CameraSystem;
import ninja.trek.nodes.NodeManager;
import ninja.trek.nodes.model.CameraNode;
import ninja.trek.nodes.model.AreaInstance;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public class NodeEditorScreen extends Screen {
    private double lastMouseX, lastMouseY;
    private boolean dragging = false;
    private long mouseDownTime = 0;
    private double mouseDownX = 0;
    private double mouseDownY = 0;
    private static final long CLICK_TIME_THRESHOLD_MS = 200;
    private static final double CLICK_MOVEMENT_THRESHOLD_PX = 5.0;
    private static final double AREA_CENTER_STEP = 1.0;

    private Button areaXButton;
    private Button areaYButton;
    private Button areaZButton;

    private enum AreaAxis { X, Y, Z }

    public NodeEditorScreen() {
        super(Component.literal("Node Edit"));
    }

    @Override protected void init() {
        this.clearWidgets();
        areaXButton = null;
        areaYButton = null;
        areaZButton = null;
        int rightX = this.width - 120;
        int y = 10;
        int w = 110, h = 20, sp=4;

        // Initialize edit rotation to current camera orientation
        net.minecraft.client.Camera cam0 = Minecraft.getInstance().gameRenderer.mainCamera();
        if (cam0 != null) {
            ninja.trek.nodes.NodeManager.get().setEditRotation(cam0.yRot(), cam0.xRot());
        }

        addRenderableWidget(Button.builder(Component.literal("Color"), b-> {
            minecraft.gui.setScreen(new ColorPickerModal(col -> {
                CameraNode sel = NodeManager.get().getSelected();
                if (sel != null) { sel.colorARGB = col; NodeManager.get().save(); }
            }));
        }).bounds(rightX,y,w,h).build()); y+=h+sp;

        // Node edit utility buttons
        addRenderableWidget(Button.builder(Component.literal("Rename"), b-> {
            CameraNode sel = NodeManager.get().getSelected();
            if (sel != null) {
                minecraft.gui.setScreen(new ninja.trek.nodes.ui.NodeRenameModal(sel.name, newName -> {
                    sel.name = newName;
                    NodeManager.get().save();
                    minecraft.gui.setScreen(this);
                }));
            }
        }).bounds(rightX,y,w,h).build()); y+=h+sp;

        addRenderableWidget(Button.builder(Component.literal("Duplicate"), b-> {
            CameraNode sel = NodeManager.get().getSelected();
            if (sel != null) {
                CameraNode n = new CameraNode();
                n.name = sel.name + " Copy";
                n.type = sel.type;
                n.position = sel.position.add(0.25, 0, 0.25);
                n.colorARGB = sel.colorARGB;
                // add and select
                NodeManager.get().setSelected(NodeManager.get().addNode(n.position).id);
                // replace new node fields
                CameraNode added = NodeManager.get().getSelected();
                if (added != null) {
                    added.name = n.name;
                    added.type = n.type;
                    added.colorARGB = n.colorARGB;
                    added.droneRadius = sel.droneRadius;
                    added.droneSpeedDegPerSec = sel.droneSpeedDegPerSec;
                    added.droneStartAngleDeg = sel.droneStartAngleDeg;
                    added.timelapseYaw = sel.timelapseYaw;
                    added.timelapsePitch = sel.timelapsePitch;
                    added.timelapseFovMultiplier = sel.timelapseFovMultiplier;
                    added.timelapseIndex = sel.timelapseIndex;
                    added.timelapseEnabled = sel.timelapseEnabled;
                    NodeManager.get().save();
                }
                this.init(this.width, this.height);
            }
        }).bounds(rightX,y,w,h).build()); y+=h+sp;

        

        // Export/Import node sets
        addRenderableWidget(Button.builder(Component.literal("Export"), b-> {
            boolean ok = ninja.trek.nodes.io.NodeStorage.exportData(
                    new java.util.ArrayList<>(NodeManager.get().getNodes()),
                    new java.util.ArrayList<>(NodeManager.get().getAreas()));
            if (!ok) {
                // no toast; silent
            }
        }).bounds(rightX,y,w,h).build()); y+=h+sp;
        addRenderableWidget(Button.builder(Component.literal("Import"), b-> {
            ninja.trek.nodes.io.NodeStorage.Payload payload = ninja.trek.nodes.io.NodeStorage.importData();
            if (!payload.nodes.isEmpty() || !payload.areas.isEmpty()) {
                NodeManager.get().replaceAll(payload.nodes, payload.areas);
                this.init(this.width, this.height);
            }
        }).bounds(rightX,y,w,h).build()); y+=h+sp;

        addRenderableWidget(Button.builder(Component.literal("Add Node"), b-> {
            Camera cam = Minecraft.getInstance().gameRenderer.mainCamera();
            if (cam != null) {
                CameraNode n = NodeManager.get().addNode(cam.position());
                NodeManager.get().setSelected(n.id);
            }
        }).bounds(rightX,y,w,h).build()); y+=h+sp;

        addRenderableWidget(Button.builder(Component.literal("Add Timelapse"), b-> {
            Camera cam = Minecraft.getInstance().gameRenderer.mainCamera();
            if (cam != null) {
                float fovMul = 1.0f;
                fovMul = ninja.trek.camera.CameraSystem.getInstance().getFovMultiplier();
                CameraNode n = NodeManager.get().addTimelapseNode(cam.position(), cam.yRot(), cam.xRot(), fovMul);
                NodeManager.get().setSelected(n.id);
                this.init(this.width, this.height);
            }
        }).bounds(rightX,y,w,h).build()); y+=h+sp;

        addRenderableWidget(Button.builder(Component.literal("Delete Node"), b-> NodeManager.get().removeSelected())
                .bounds(rightX,y,w,h).build()); y+=h+sp;

        addRenderableWidget(Button.builder(Component.literal("Add Area"), b-> {
            Camera cam = Minecraft.getInstance().gameRenderer.mainCamera();
            Vec3 pos = cam != null ? cam.position() : Vec3.ZERO;
            var area = NodeManager.get().addArea(pos);
            NodeManager.get().setSelectedArea(area.id);
            this.init(this.width, this.height);
        }).bounds(rightX,y,w,h).build()); y+=h+sp;


        // Node type and DroneShot controls for selected node
        CameraNode selType = NodeManager.get().getSelected();
        if (selType != null) {
            addRenderableWidget(Button.builder(Component.literal("Type: "+selType.type), b-> {
                ninja.trek.nodes.model.NodeType[] types = ninja.trek.nodes.model.NodeType.values();
                int idx = selType.type.ordinal();
                selType.type = types[(idx + 1) % types.length];
                NodeManager.get().save();
                this.init(this.width, this.height);
            }).bounds(rightX, y, w, h).build());
            y += h + sp;

            if (selType.type == ninja.trek.nodes.model.NodeType.TIMELAPSE) {
                // Update Camera button: re-capture current camera state into this timelapse node
                addRenderableWidget(Button.builder(Component.literal("Update Camera"), b-> {
                    Camera cam2 = Minecraft.getInstance().gameRenderer.mainCamera();
                    if (cam2 != null) {
                        selType.position = cam2.position();
                        selType.timelapseYaw = cam2.yRot();
                        selType.timelapsePitch = cam2.xRot();
                        selType.timelapseFovMultiplier = ninja.trek.camera.CameraSystem.getInstance().getFovMultiplier();
                        NodeManager.get().save();
                        this.init(this.width, this.height);
                    }
                }).bounds(rightX, y, w, h).build());
                y += h + sp;

                // Yaw +/- controls
                addRenderableWidget(Button.builder(Component.literal("Yaw -"), b-> {
                    selType.timelapseYaw -= 5f;
                    NodeManager.get().save();
                    this.init(this.width, this.height);
                }).bounds(rightX, y, (w/2)-2, h).build());
                addRenderableWidget(Button.builder(Component.literal("+"), b-> {
                    selType.timelapseYaw += 5f;
                    NodeManager.get().save();
                    this.init(this.width, this.height);
                }).bounds(rightX + (w/2) + 2, y, (w/2)-2, h).build());
                y += h + sp;

                // Pitch +/- controls
                addRenderableWidget(Button.builder(Component.literal("Pitch -"), b-> {
                    selType.timelapsePitch = Math.max(-90f, selType.timelapsePitch - 5f);
                    NodeManager.get().save();
                    this.init(this.width, this.height);
                }).bounds(rightX, y, (w/2)-2, h).build());
                addRenderableWidget(Button.builder(Component.literal("+"), b-> {
                    selType.timelapsePitch = Math.min(90f, selType.timelapsePitch + 5f);
                    NodeManager.get().save();
                    this.init(this.width, this.height);
                }).bounds(rightX + (w/2) + 2, y, (w/2)-2, h).build());
                y += h + sp;

                // FOV +/- controls
                addRenderableWidget(Button.builder(Component.literal("FOV -"), b-> {
                    selType.timelapseFovMultiplier = Math.max(0.1f, selType.timelapseFovMultiplier - 0.1f);
                    NodeManager.get().save();
                    this.init(this.width, this.height);
                }).bounds(rightX, y, (w/2)-2, h).build());
                addRenderableWidget(Button.builder(Component.literal("+"), b-> {
                    selType.timelapseFovMultiplier += 0.1f;
                    NodeManager.get().save();
                    this.init(this.width, this.height);
                }).bounds(rightX + (w/2) + 2, y, (w/2)-2, h).build());
                y += h + sp;

                // Timelapse Index cycling button
                addRenderableWidget(Button.builder(Component.literal("Index: " + selType.timelapseIndex), b-> {
                    // Collect available indices from follower configs
                    java.util.Set<Integer> indices = new java.util.TreeSet<>();
                    indices.add(0);
                    try {
                        ninja.trek.config.FollowerConfig fc = ninja.trek.config.FollowerSettingsIO.loadFollowers();
                        for (ninja.trek.config.FollowerConfig.FollowerEntry entry : fc.getFollowers()) {
                            if (entry.getMovement() instanceof ninja.trek.cameramovements.movements.TimelapseMovement tm) {
                                indices.add(tm.getTimelapseIndex());
                            }
                        }
                    } catch (Throwable ignored) {}
                    // Cycle to next index
                    java.util.List<Integer> indexList = new java.util.ArrayList<>(indices);
                    int cur = indexList.indexOf(selType.timelapseIndex);
                    int next = (cur + 1) % indexList.size();
                    int newIndex = indexList.get(next);

                    // Check for conflict: if the target index's TimelapseMovement has followPlayer==false
                    boolean isNonFollow = false;
                    try {
                        ninja.trek.config.FollowerConfig fc2 = ninja.trek.config.FollowerSettingsIO.loadFollowers();
                        for (ninja.trek.config.FollowerConfig.FollowerEntry entry : fc2.getFollowers()) {
                            if (entry.getMovement() instanceof ninja.trek.cameramovements.movements.TimelapseMovement tm) {
                                if (tm.getTimelapseIndex() == newIndex && !tm.isFollowPlayer()) {
                                    isNonFollow = true;
                                    break;
                                }
                            }
                        }
                    } catch (Throwable ignored) {}

                    if (isNonFollow) {
                        CameraNode conflict = NodeManager.get().findEnabledTimelapseNodeWithIndex(newIndex, selType.id);
                        if (conflict != null) {
                            final int idx = newIndex;
                            minecraft.gui.setScreen(new TimelapseIndexConflictModal(idx, conflict, selType, () -> {
                                minecraft.gui.setScreen(this);
                                this.init(this.width, this.height);
                            }));
                            return;
                        }
                    }
                    selType.timelapseIndex = newIndex;
                    NodeManager.get().save();
                    this.init(this.width, this.height);
                }).bounds(rightX, y, w, h).build());
                y += h + sp;

                // Timelapse Enabled toggle
                addRenderableWidget(Button.builder(Component.literal(selType.timelapseEnabled ? "Enabled" : "Disabled"), b-> {
                    selType.timelapseEnabled = !selType.timelapseEnabled;
                    NodeManager.get().save();
                    this.init(this.width, this.height);
                }).bounds(rightX, y, w, h).build());
                y += h + sp;
            }

            if (selType.type == ninja.trek.nodes.model.NodeType.DRONE_SHOT) {
                // Radius controls
                addRenderableWidget(Button.builder(Component.literal("Radius -"), b-> {
                    selType.droneRadius = Math.max(0.5, selType.droneRadius - 0.5);
                    NodeManager.get().save();
                    this.init(this.width, this.height);
                }).bounds(rightX, y, (w/2)-2, h).build());
                addRenderableWidget(Button.builder(Component.literal("+"), b-> {
                    selType.droneRadius = selType.droneRadius + 0.5;
                    NodeManager.get().save();
                    this.init(this.width, this.height);
                }).bounds(rightX + (w/2) + 2, y, (w/2)-2, h).build());
                y += h + sp;

                // Speed controls
                addRenderableWidget(Button.builder(Component.literal("Speed -"), b-> {
                    selType.droneSpeedDegPerSec = Math.max(0.0, selType.droneSpeedDegPerSec - 5.0);
                    NodeManager.get().save();
                    this.init(this.width, this.height);
                }).bounds(rightX, y, (w/2)-2, h).build());
                addRenderableWidget(Button.builder(Component.literal("+"), b-> {
                    selType.droneSpeedDegPerSec = selType.droneSpeedDegPerSec + 5.0;
                    NodeManager.get().save();
                    this.init(this.width, this.height);
                }).bounds(rightX + (w/2) + 2, y, (w/2)-2, h).build());
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

            addRenderableWidget(Button.builder(Component.literal(displayName), b -> {
                NodeManager.get().setSelectedArea(area.id);
                minecraft.gui.setScreen(new AreaSettingsModal(area, updated -> {
                    NodeManager.get().save();
                    minecraft.gui.setScreen(this);
                }));
            }).bounds(areaListX, areaListY, 140, 18).build());

            addRenderableWidget(Button.builder(Component.literal("Sel"), b -> {
                NodeManager.get().setSelectedArea(area.id);
                this.init(this.width, this.height);
            }).bounds(areaListX + 145, areaListY, 32, 18).build());

            addRenderableWidget(Button.builder(Component.literal("-"), b -> {
                NodeManager.get().removeArea(area.id);
                this.init(this.width, this.height);
            }).bounds(areaListX + 182, areaListY, 18, 18).build());

            areaListY += 22;
            areaIdx++;
        }

        selectedArea = NodeManager.get().getSelectedArea();
        if (selectedArea != null) {
            int coordWidth = 80;
            int coordHeight = 18;
            int coordSpacing = 4;
            int coordY = areaListY + 8;
            Vec3 center = selectedArea.center != null ? selectedArea.center : Vec3.ZERO;
            areaXButton = Button.builder(areaAxisText("X", center.x), b -> {
                adjustSelectedAreaCenter(AreaAxis.X, AREA_CENTER_STEP);
            }).bounds(areaListX, coordY, coordWidth, coordHeight).build();
            areaXButton.setTooltip(Tooltip.create(Component.literal("Left click +1, Right click -1")));
            addRenderableWidget(areaXButton);

            areaYButton = Button.builder(areaAxisText("Y", center.y), b -> {
                adjustSelectedAreaCenter(AreaAxis.Y, AREA_CENTER_STEP);
            }).bounds(areaListX + coordWidth + coordSpacing, coordY, coordWidth, coordHeight).build();
            areaYButton.setTooltip(Tooltip.create(Component.literal("Left click +1, Right click -1")));
            addRenderableWidget(areaYButton);

            areaZButton = Button.builder(areaAxisText("Z", center.z), b -> {
                adjustSelectedAreaCenter(AreaAxis.Z, AREA_CENTER_STEP);
            }).bounds(areaListX + (coordWidth + coordSpacing) * 2, coordY, coordWidth, coordHeight).build();
            areaZButton.setTooltip(Tooltip.create(Component.literal("Left click +1, Right click -1")));
            addRenderableWidget(areaZButton);
        }
        updateAreaCoordButtons();
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void tick() {
        super.tick();
        // Key forwarding is handled in keyPressed/keyReleased; no per-tick polling here.
    }

    @Override public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // Render only our widgets; skip Screen default background/separators entirely
        for (net.minecraft.client.gui.components.events.GuiEventListener e : this.children()) {
            if (e instanceof net.minecraft.client.gui.components.Renderable d) {
                d.extractRenderState(context, mouseX, mouseY, delta);
            }
        }

        // Side info text (selected node + areas)
        int rightX = this.width - 240;
        int y = 10;
        CameraNode selNode = NodeManager.get().getSelected();
        if (selNode != null) {
            context.text(font, Component.literal("Node: "+selNode.name), rightX, y, 0xFFFFFF, true); y+=12;
            context.text(font, Component.literal("Type: "+selNode.type), rightX, y, 0xFFFFFF, true); y+=12;
            if (selNode.type == ninja.trek.nodes.model.NodeType.DRONE_SHOT) {
                context.text(font, Component.literal(String.format("Radius: %.1f", selNode.droneRadius)), rightX, y, 0xFFFFFF, true); y+=12;
                context.text(font, Component.literal(String.format("Speed: %.0f deg/s", selNode.droneSpeedDegPerSec)), rightX, y, 0xFFFFFF, true); y+=12;
            }
            if (selNode.type == ninja.trek.nodes.model.NodeType.TIMELAPSE) {
                context.text(font, Component.literal(String.format("Yaw: %.1f", selNode.timelapseYaw)), rightX, y, 0xFFFFFF, true); y+=12;
                context.text(font, Component.literal(String.format("Pitch: %.1f", selNode.timelapsePitch)), rightX, y, 0xFFFFFF, true); y+=12;
                context.text(font, Component.literal(String.format("FOV: %.2f", selNode.timelapseFovMultiplier)), rightX, y, 0xFFFFFF, true); y+=12;
                context.text(font, Component.literal("Index: " + selNode.timelapseIndex), rightX, y, 0xFFFFFF, true); y+=12;
                context.text(font, Component.literal(selNode.timelapseEnabled ? "Enabled" : "Disabled"), rightX, y, selNode.timelapseEnabled ? 0x88FF88 : 0xFF8888, true); y+=12;
            }
        } else {
            context.text(font, Component.literal("No node selected"), rightX, y, 0xAAAAAA, true); y+=12;
        }

        y += 6;

        AreaInstance selArea = NodeManager.get().getSelectedArea();
        if (selArea != null) {
            context.text(font, Component.literal("Area: "+(selArea.name != null ? selArea.name : selArea.id.toString())), rightX, y, 0xFFFFFF, true); y+=12;
            context.text(font, Component.literal("Shape: "+selArea.shape), rightX, y, 0xFFFFFF, true); y+=12;
            if (selArea.advanced && selArea.insideRadii != null && selArea.outsideRadii != null) {
                context.text(font, Component.literal(String.format("Inside: %.1f/%.1f/%.1f", selArea.insideRadii.x, selArea.insideRadii.y, selArea.insideRadii.z)), rightX, y, 0xFFFFFF, true); y+=12;
                context.text(font, Component.literal(String.format("Outside: %.1f/%.1f/%.1f", selArea.outsideRadii.x, selArea.outsideRadii.y, selArea.outsideRadii.z)), rightX, y, 0xFFFFFF, true); y+=12;
            } else {
                context.text(font, Component.literal(String.format("Inside: %.1f", selArea.insideRadius)), rightX, y, 0xFFFFFF, true); y+=12;
                context.text(font, Component.literal(String.format("Outside: %.1f", selArea.outsideRadius)), rightX, y, 0xFFFFFF, true); y+=12;
            }
            context.text(font, Component.literal("Curve: "+selArea.easing), rightX, y, 0xFFFFFF, true); y+=12;
        } else {
            context.text(font, Component.literal("No area selected"), rightX, y, 0xAAAAAA, true);
        }
    }

    // Disable the default translucent in-game gradient background to avoid flicker
    @Override
    public void extractTransparentBackground(GuiGraphicsExtractor context) {
        // no-op: keep full game view without gradient/blur
    }

    // Belt/world blur guard: override to prevent any blur application
    @Override
    protected void extractBlurredBackground(GuiGraphicsExtractor context) {
        // no-op
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        // Close and start return on ESC
        if (input.input() == GLFW.GLFW_KEY_ESCAPE) {
            closeAndStartReturn();
            return true;
        }

        // If user hits the active slot hotkey again while editing, close and start return
        if (this.minecraft != null) {
            Integer activeSlot = ninja.trek.CraneshotClient.MOVEMENT_MANAGER.getActiveMovementSlot();
            if (activeSlot != null && activeSlot >= 0 && activeSlot < ninja.trek.CraneshotClient.cameraKeyBinds.length) {
                net.minecraft.client.KeyMapping kb = ninja.trek.CraneshotClient.cameraKeyBinds[activeSlot];
                if (kb != null && kb.matches(input)) {
                    closeAndStartReturn();
                    return true;
                }
            }
        }

        // Forward movement keys by bound mapping so freecam can move while screen is open
        if (this.minecraft != null) {
            var o = this.minecraft.options;
            if (o.keyUp.matches(input)) { o.keyUp.setDown(true); return true; }
            if (o.keyDown.matches(input)) { o.keyDown.setDown(true); return true; }
            if (o.keyLeft.matches(input)) { o.keyLeft.setDown(true); return true; }
            if (o.keyRight.matches(input)) { o.keyRight.setDown(true); return true; }
            if (o.keyJump.matches(input)) { o.keyJump.setDown(true); return true; }
            if (o.keyShift.matches(input)) { o.keyShift.setDown(true); return true; }
            if (o.keySprint.matches(input)) { o.keySprint.setDown(true); return true; }
        }

        // Let super handle other UI keys
        return super.keyPressed(input);
    }

    private void closeAndStartReturn() {
        // Mark node editing off and start camera return
        ninja.trek.nodes.NodeManager.get().setEditing(false);
        // Clear any forwarded key states to avoid stuck keys
        clearMovementKeys();
        if (this.minecraft != null) {
            ninja.trek.CraneshotClient.MOVEMENT_MANAGER.finishTransition(this.minecraft, this.minecraft.gameRenderer.mainCamera());
            this.minecraft.gui.setScreen(null);
        }
    }

    @Override
    public void removed() {
        // Ensure keys are cleared if the screen is closed by any means
        clearMovementKeys();
        // Ensure cursor is released when screen is closed
        if (minecraft != null && minecraft.getWindow() != null) {
            GLFW.glfwSetInputMode(minecraft.getWindow().handle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        }
        super.removed();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean fromInside) {
        if (click != null) {
            if (handleAreaCoordinateRightClick(click)) {
                return true;
            }
        }
        // Let buttons/widgets handle the click first
        if (super.mouseClicked(click, fromInside)) {
            return true;
        }
        // No widget consumed the click — start dragging
        if (click != null) {
            dragging = true;
            mouseDownTime = System.currentTimeMillis();
            mouseDownX = click.x();
            mouseDownY = click.y();

            // Capture mouse cursor when dragging starts
            if (minecraft != null && minecraft.getWindow() != null) {
                GLFW.glfwSetInputMode(minecraft.getWindow().handle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        // Release mouse cursor when dragging ends
        if (minecraft != null && minecraft.getWindow() != null) {
            GLFW.glfwSetInputMode(minecraft.getWindow().handle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
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
                    Camera cam = Minecraft.getInstance().gameRenderer.mainCamera();
                    if (cam != null) {
                        NodeManager.get().selectNearestToScreen(click.x(), click.y(), this.width, this.height, cam);
                        this.init(this.width, this.height);
                    }
                } catch (Throwable t) {
                    // Fallback: select center
                    Camera cam = Minecraft.getInstance().gameRenderer.mainCamera();
                    if (cam != null) {
                        NodeManager.get().selectNearestToScreen(this.width/2.0, this.height/2.0, this.width, this.height, cam);
                        this.init(this.width, this.height);
                    }
                }
            }
        }

        mouseDownTime = 0;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (dragging) {
            // 3x sensitivity for node editing
            double sens = Minecraft.getInstance().options.sensitivity().get();
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
        if (this.minecraft == null) return;
        var opts = this.minecraft.options;
        opts.keyUp.setDown(false);
        opts.keyDown.setDown(false);
        opts.keyLeft.setDown(false);
        opts.keyRight.setDown(false);
        opts.keyJump.setDown(false);
        opts.keyShift.setDown(false);
        opts.keySprint.setDown(false);
    }

    // (keyPressed override lives earlier in the file)

    @Override
    public boolean keyReleased(KeyEvent input) {
        if (this.minecraft != null) {
            var o = this.minecraft.options;
            if (o.keyUp.matches(input)) { o.keyUp.setDown(false); return true; }
            if (o.keyDown.matches(input)) { o.keyDown.setDown(false); return true; }
            if (o.keyLeft.matches(input)) { o.keyLeft.setDown(false); return true; }
            if (o.keyRight.matches(input)) { o.keyRight.setDown(false); return true; }
            if (o.keyJump.matches(input)) { o.keyJump.setDown(false); return true; }
            if (o.keyShift.matches(input)) { o.keyShift.setDown(false); return true; }
            if (o.keySprint.matches(input)) { o.keySprint.setDown(false); return true; }
        }
        return super.keyReleased(input);
    }

    private boolean handleAreaCoordinateRightClick(MouseButtonEvent click) {
        if (click == null || click.button() != 1) {
            return false;
        }
        double mx = click.x();
        double my = click.y();
        var soundManager = Minecraft.getInstance().getSoundManager();
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
        Vec3 center = area.center != null ? area.center : Vec3.ZERO;
        double x = center.x;
        double y = center.y;
        double z = center.z;
        switch (axis) {
            case X -> x += delta;
            case Y -> y += delta;
            case Z -> z += delta;
        }
        area.center = new Vec3(x, y, z);
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

    private static Component areaAxisText(String axis, double value) {
        return Component.literal(String.format(Locale.US, "%s: %.1f", axis, value));
    }

    private static Component areaAxisPlaceholder(String axis) {
        return Component.literal(axis + ": --");
    }
}
