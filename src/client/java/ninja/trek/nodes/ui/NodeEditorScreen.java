package ninja.trek.nodes.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.Camera;
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

                // Start angle controls
                addDrawableChild(ButtonWidget.builder(Text.literal("Angle -"), b-> {
                    selType.droneStartAngleDeg = (selType.droneStartAngleDeg - 5.0 + 360.0) % 360.0;
                    NodeManager.get().save();
                    this.init(client, this.width, this.height);
                }).dimensions(rightX, y, (w/2)-2, h).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("+"), b-> {
                    selType.droneStartAngleDeg = (selType.droneStartAngleDeg + 5.0) % 360.0;
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
        // Logging only; no key forwarding hacks
        if (client != null && client.getWindow() != null) {
            ninja.trek.Craneshot.LOGGER.debug("NodeEditorScreen.tick (window={}, editing=true)", client.getWindow());
        }
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
                context.drawText(textRenderer, Text.literal(String.format("Start Angle: %.0f deg", sel.droneStartAngleDeg)), rightX, y, 0xFFFFFF, true); y+=12;
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
    public boolean mouseClicked(Click click, boolean fromInside) {
        if (click != null) {
            dragging = true;
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
        return super.mouseClicked(click, fromInside);
    }

    @Override
    public boolean mouseReleased(Click click) {
        dragging = false;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (dragging) {
            double sens = MinecraftClient.getInstance().options.getMouseSensitivity().getValue();
            double calc = 0.6 * sens * sens * sens + 0.2;
            // Rotate using regular freecam pipeline: entity if present, otherwise CameraSystem
            ninja.trek.util.CameraEntity camEnt = ninja.trek.util.CameraEntity.getCamera();
            if (camEnt != null) {
                camEnt.updateCameraRotations((float)(deltaX * calc), (float)(-deltaY * calc));
            } else {
                ninja.trek.camera.CameraSystem.getInstance().updateRotation(deltaX * calc * 0.55D, -deltaY * calc * 0.55D, 1.0);
            }
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }
}

