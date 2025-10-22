package ninja.trek.nodes.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import ninja.trek.camera.CameraSystem;
import ninja.trek.nodes.NodeManager;
import ninja.trek.nodes.model.Area;
import ninja.trek.nodes.model.CameraNode;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class NodeEditorScreen extends Screen {
    private double lastMouseX, lastMouseY;
    private boolean dragging = false;
    private long mouseDownTime = 0;
    private double mouseDownX = 0;
    private double mouseDownY = 0;
    private static final long CLICK_TIME_THRESHOLD_MS = 200;
    private static final double CLICK_MOVEMENT_THRESHOLD_PX = 5.0;
    

    public NodeEditorScreen() {
        super(Text.literal("Node Edit"));
    }

    @Override protected void init() {
        this.clearChildren();
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
                n.lookAt = sel.lookAt;
                for (Area a : sel.areas) {
                    Area c = new Area();
                    c.shape = a.shape;
                    c.center = a.center;
                    c.minRadius = a.minRadius;
                    c.maxRadius = a.maxRadius;
                    c.advanced = a.advanced;
                    c.minRadii = a.minRadii;
                    c.maxRadii = a.maxRadii;
                    c.filterWalking = a.filterWalking;
                    c.filterElytra = a.filterElytra;
                    c.filterMinecart = a.filterMinecart;
                    c.filterRidingGhast = a.filterRidingGhast;
                    c.filterRidingOther = a.filterRidingOther;
                    c.filterBoat = a.filterBoat;
                    c.filterSwimming = a.filterSwimming;
                    c.filterSneaking = a.filterSneaking;
                    c.filterCrawling1Block = a.filterCrawling1Block;
                    c.easing = a.easing;
                    n.areas.add(c);
                }
                ninja.trek.nodes.NodeManager.get().getNodes();
                // add and select
                NodeManager.get().setSelected(NodeManager.get().addNode(n.position).id);
                // replace new node fields
                CameraNode added = NodeManager.get().getSelected();
                if (added != null) {
                    added.name = n.name;
                    added.type = n.type;
                    added.colorARGB = n.colorARGB;
                    added.lookAt = n.lookAt;
                    added.areas.addAll(n.areas);
                    NodeManager.get().save();
                }
                this.init(client, this.width, this.height);
            }
        }).dimensions(rightX,y,w,h).build()); y+=h+sp;

        

        // Export/Import node sets
        addDrawableChild(ButtonWidget.builder(Text.literal("Export"), b-> {
            boolean ok = ninja.trek.nodes.io.NodeStorage.exportNodes(new java.util.ArrayList<>(NodeManager.get().getNodes()));
            if (!ok) {
                // no toast; silent
            }
        }).dimensions(rightX,y,w,h).build()); y+=h+sp;
        addDrawableChild(ButtonWidget.builder(Text.literal("Import"), b-> {
            java.util.List<CameraNode> list = ninja.trek.nodes.io.NodeStorage.importNodes();
            if (!list.isEmpty()) {
                // Replace current set
                // simple: clear + add all
                // since NodeManager lacks clear API, we simulate by direct field replacement via reflection or rebuild
                // fallback: remove selected id and overwrite underlying storage
                try {
                    java.lang.reflect.Field f = ninja.trek.nodes.NodeManager.class.getDeclaredField("nodes");
                    f.setAccessible(true);
                    java.util.List<CameraNode> nodes = (java.util.List<CameraNode>) f.get(ninja.trek.nodes.NodeManager.get());
                    nodes.clear();
                    nodes.addAll(list);
                    ninja.trek.nodes.NodeManager.get().save();
                    this.init(client, this.width, this.height);
                } catch (Throwable ignored) {}
            }
        }).dimensions(rightX,y,w,h).build()); y+=h+sp;

        addDrawableChild(ButtonWidget.builder(Text.literal("Add Node"), b-> {
            Camera cam = MinecraftClient.getInstance().gameRenderer.getCamera();
            if (cam != null) {
                CameraNode n = NodeManager.get().addNode(cam.getPos());
                NodeManager.get().setSelected(n.id);
            }
        }).dimensions(rightX,y,w,h).build()); y+=h+sp;

        addDrawableChild(ButtonWidget.builder(Text.literal("Delete Node"), b-> NodeManager.get().removeSelected())
                .dimensions(rightX,y,w,h).build()); y+=h+sp;

        addDrawableChild(ButtonWidget.builder(Text.literal("Add Area"), b-> {
            CameraNode sel = NodeManager.get().getSelected();
            if (sel != null) {
                Camera cam = MinecraftClient.getInstance().gameRenderer.getCamera();
                if (cam != null) NodeManager.get().addAreaTo(sel, cam.getPos());
                this.init(client, this.width, this.height);
            }
        }).dimensions(rightX,y,w,h).build()); y+=h+sp;

        addDrawableChild(ButtonWidget.builder(Text.literal("Set LookAt"), b-> {
            CameraNode sel = NodeManager.get().getSelected();
            Camera cam = MinecraftClient.getInstance().gameRenderer.getCamera();
            if (sel != null && cam != null) NodeManager.get().setLookAt(sel, cam.getPos());
        }).dimensions(rightX,y,w,h).build()); y+=h+sp;

        addDrawableChild(ButtonWidget.builder(Text.literal("Unset LookAt"), b-> {
            CameraNode sel = NodeManager.get().getSelected();
            if (sel != null) NodeManager.get().unsetLookAt(sel);
        }).dimensions(rightX,y,w,h).build()); y+=h+sp;


        // Node type and DroneShot controls for selected node
        CameraNode selType = NodeManager.get().getSelected();
        if (selType != null) {
            addDrawableChild(ButtonWidget.builder(Text.literal("Type: "+selType.type), b-> {
                selType.type = (selType.type == ninja.trek.nodes.model.NodeType.CAMERA_CONTROL)
                        ? ninja.trek.nodes.model.NodeType.DRONE_SHOT
                        : ninja.trek.nodes.model.NodeType.CAMERA_CONTROL;
                NodeManager.get().save();
                this.init(client, this.width, this.height);
            }).dimensions(rightX, y, w, h).build());
            y += h + sp;

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

        // Build area list buttons for selected node
        CameraNode sel = NodeManager.get().getSelected();
        if (sel != null) {
            int listX = this.width - 240;
            int listY = 10;
            int idx = 0;
            for (Area a : sel.areas) {
                final int areaIndex = idx++;
                addDrawableChild(ButtonWidget.builder(Text.literal("Edit Area "+areaIndex), b-> {
                    client.setScreen(new AreaSettingsModal(a, updated -> {
                        NodeManager.get().save();
                        client.setScreen(this);
                    }));
                }).dimensions(listX, listY, 100, 18).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("-"), b-> {
                    NodeManager.get().removeArea(sel, a);
                    this.init(client, this.width, this.height);
                }).dimensions(listX+105, listY, 18, 18).build());
                listY += 22;
            }
        }
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
        CameraNode sel = NodeManager.get().getSelected();
        if (sel != null) {
            context.drawText(textRenderer, Text.literal("Type: "+sel.type), rightX, y, 0xFFFFFF, true); y+=12;
            if (sel.type == ninja.trek.nodes.model.NodeType.DRONE_SHOT) {
                context.drawText(textRenderer, Text.literal(String.format("Radius: %.1f", sel.droneRadius)), rightX, y, 0xFFFFFF, true); y+=12;
                context.drawText(textRenderer, Text.literal(String.format("Speed: %.0f deg/s", sel.droneSpeedDegPerSec)), rightX, y, 0xFFFFFF, true); y+=12;
                y+=4;
            }
            List<Area> areas = sel.areas;
            int i=0;
            for (Area a : areas) {
                context.drawText(textRenderer, Text.literal("Area "+i+" ("+a.shape+") min:"+(int)a.minRadius+" max:"+(int)a.maxRadius), rightX, y, 0xFFFFFF, true);
                y+=12;
            }
        } else {
            context.drawText(textRenderer, Text.literal("No node selected"), rightX, y, 0xAAAAAA, true);
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
                camEnt.updateCameraRotations((float)(deltaX * calc), (float)(-deltaY * calc));
            } else {
                // CameraSystem.updateRotation expects pre-multiplied values
                ninja.trek.camera.CameraSystem.getInstance().updateRotation(deltaX * calc * 0.55, deltaY * calc * 0.55, 1.0);
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
}

