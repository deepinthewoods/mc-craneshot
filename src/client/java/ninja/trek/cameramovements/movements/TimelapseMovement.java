package ninja.trek.cameramovements.movements;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.math.Vec3d;
import ninja.trek.Craneshot;
import ninja.trek.cameramovements.*;
import ninja.trek.config.MovementSetting;
import ninja.trek.nodes.NodeManager;
import ninja.trek.nodes.model.CameraNode;
import ninja.trek.nodes.model.NodeType;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@CameraMovementType(
        name = "Timelapse",
        description = "Periodically captures screenshots from nearby timelapse nodes"
)
public class TimelapseMovement extends AbstractMovementSettings implements ICameraMovement {

    @MovementSetting(label = "Interval (seconds)", min = 1, max = 3600)
    private float intervalSeconds = 60f;

    @MovementSetting(label = "Distance (chunks)", min = 1, max = 32)
    private int distanceChunks = 5;

    private enum State {
        IDLE,
        SET_CAMERA,
        WAIT_RENDER,
        TAKE_SCREENSHOT
    }

    private State state = State.IDLE;
    private long lastCaptureTimeMs = 0;
    private final List<CameraNode> inRangeNodes = new ArrayList<>();
    private int currentNodeIndex = 0;
    private CameraTarget captureTarget = null;
    private CameraTarget lastTarget = null;

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    @Override
    public void start(MinecraftClient client, Camera camera) {
        state = State.IDLE;
        lastCaptureTimeMs = 0;
        inRangeNodes.clear();
        currentNodeIndex = 0;
        captureTarget = null;
        lastTarget = null;
    }

    @Override
    public MovementState calculateState(MinecraftClient client, Camera camera, float deltaSeconds) {
        if (client == null || client.player == null || client.world == null) {
            return new MovementState(buildTarget(client, camera), false);
        }

        switch (state) {
            case IDLE:
                tickIdle(client);
                break;
            case SET_CAMERA:
                tickSetCamera();
                break;
            case WAIT_RENDER:
                state = State.TAKE_SCREENSHOT;
                break;
            case TAKE_SCREENSHOT:
                tickTakeScreenshot(client);
                break;
        }

        return new MovementState(buildTarget(client, camera), false);
    }

    private CameraTarget buildTarget(MinecraftClient client, Camera camera) {
        if (captureTarget != null) {
            lastTarget = captureTarget;
            return captureTarget;
        }
        if (lastTarget != null) {
            return lastTarget;
        }
        // Fallback: player eye position
        if (client != null && client.player != null) {
            return CameraTarget.fromCamera(camera);
        }
        return new CameraTarget();
    }

    private void tickIdle(MinecraftClient client) {
        double intervalMs = intervalSeconds * 1000.0;
        long now = System.currentTimeMillis();
        if (now - lastCaptureTimeMs < intervalMs) {
            return;
        }

        Vec3d playerPos = client.player.getEyePos();
        int playerChunkX = (int) Math.floor(playerPos.x / 16.0);
        int playerChunkZ = (int) Math.floor(playerPos.z / 16.0);

        inRangeNodes.clear();
        for (CameraNode node : NodeManager.get().getNodes()) {
            if (node.type != NodeType.TIMELAPSE) continue;
            int nodeChunkX = (int) Math.floor(node.position.x / 16.0);
            int nodeChunkZ = (int) Math.floor(node.position.z / 16.0);
            int dx = Math.abs(playerChunkX - nodeChunkX);
            int dz = Math.abs(playerChunkZ - nodeChunkZ);
            if (dx <= distanceChunks && dz <= distanceChunks) {
                inRangeNodes.add(node);
            }
        }

        if (!inRangeNodes.isEmpty()) {
            currentNodeIndex = 0;
            state = State.SET_CAMERA;
            tickSetCamera();
        }
    }

    private void tickSetCamera() {
        if (currentNodeIndex >= inRangeNodes.size()) {
            finishCaptureCycle();
            return;
        }

        CameraNode node = inRangeNodes.get(currentNodeIndex);
        captureTarget = new CameraTarget(
                node.position,
                node.timelapseYaw,
                node.timelapsePitch,
                node.timelapseFovMultiplier
        );
        state = State.WAIT_RENDER;
    }

    private void tickTakeScreenshot(MinecraftClient client) {
        if (currentNodeIndex >= inRangeNodes.size()) {
            finishCaptureCycle();
            return;
        }

        CameraNode node = inRangeNodes.get(currentNodeIndex);

        try {
            var framebuffer = client.getFramebuffer();
            if (framebuffer != null) {
                String sanitizedName = sanitizeName(node.name);
                File timelapseDir = new File(client.runDirectory, "screenshots/timelapse/" + sanitizedName);
                if (!timelapseDir.exists()) {
                    timelapseDir.mkdirs();
                }

                String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
                String fileName = timestamp + ".png";

                File outputFile = new File(timelapseDir, fileName);

                ScreenshotRecorder.takeScreenshot(framebuffer, image -> {
                    net.minecraft.util.Util.getIoWorkerExecutor().execute(() -> {
                        try {
                            image.writeTo(outputFile);
                        } catch (Exception e) {
                            Craneshot.LOGGER.warn("Failed to save timelapse screenshot: {}", outputFile, e);
                        } finally {
                            image.close();
                        }
                    });
                });
            }
        } catch (Exception e) {
            Craneshot.LOGGER.warn("Failed to capture timelapse screenshot for node: {}", node.name, e);
        }

        currentNodeIndex++;
        if (currentNodeIndex < inRangeNodes.size()) {
            state = State.SET_CAMERA;
        } else {
            finishCaptureCycle();
        }
    }

    private void finishCaptureCycle() {
        state = State.IDLE;
        captureTarget = null;
        lastCaptureTimeMs = System.currentTimeMillis();
        inRangeNodes.clear();
        currentNodeIndex = 0;
    }

    @Override
    public boolean isComplete() {
        return false;
    }

    @Override
    public String getName() {
        return "Timelapse";
    }

    @Override
    public void queueReset(MinecraftClient client, Camera camera) {
        // no-op
    }

    @Override
    public void adjustDistance(boolean increase, MinecraftClient client) {
        // no-op
    }

    @Override
    public float getWeight() {
        return 1.0f;
    }

    @Override
    public RaycastType getRaycastType() {
        return RaycastType.NONE;
    }

    private static String sanitizeName(String name) {
        if (name == null || name.isBlank()) return "unnamed";
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }
}
