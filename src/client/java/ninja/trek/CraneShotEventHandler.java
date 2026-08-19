package ninja.trek;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ninja.trek.cameramovements.AbstractMovementSettings;
import ninja.trek.cameramovements.ICameraMovement;
import ninja.trek.cameramovements.movements.FollowMovement;
import ninja.trek.config.FollowerConfig;
import ninja.trek.config.FollowerMode;
import ninja.trek.config.GeneralMenuSettings;
import ninja.trek.config.SlotMenuSettings;
import ninja.trek.follower.FollowerCameraDirector;

import java.util.HashMap;
import java.util.Map;

public class CraneShotEventHandler {
    private static final double SCROLL_COOLDOWN = 0.1;
    private static double lastScrollTime = 0;
    private static final Map<Integer, Boolean> keyStates = new HashMap<>();
    private static Integer lastActiveSlot = null;
    private static boolean followWasPressed = false;
    private static long lastFollowPressTimeMs = 0;
    private static final long DOUBLE_TAP_THRESHOLD_MS = 400;
    private static boolean zoomWasPressed = false;
    private static boolean lastAlive = true;
    private static boolean lastSleeping = false;
    private static java.util.UUID lastPlayerUuid = null;
    private static ResourceKey<Level> lastDimension = null;
    private static Vec3 lastPlayerPos = null;
    private static final double LARGE_POSITION_JUMP_THRESHOLD = 50.0; // blocks
    private static CameraType lastPerspective = null;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // If using our dedicated camera entity (freecam), tick it for smooth motion
            try {
                if (ninja.trek.util.CameraEntity.getCamera() != null) {
                    ninja.trek.util.CameraEntity.movementTick();
                }
            } catch (Throwable t) {
                // avoid hard crashes if something is null mid-load
            }
            handleScrollInput(client);
            CraneshotClient.checkKeybinds();

            Camera camera = client.gameRenderer.mainCamera();
            handleRespawnAndWakeReset(client, camera);
            handleDimensionChange(client, camera);
            handleLargePositionJumps(client, camera);

            // Follower mode: auto-activate assigned movement, skip manual keybinds
            if (FollowerMode.isFollower()) {
                handleFollowerMode(client, camera);
                // Skip all manual keybind processing for follower instances
                return;
            }

            boolean followPressed = CraneshotClient.followMovementKey != null && CraneshotClient.followMovementKey.isDown();
            if (followPressed != followWasPressed) {
                if (followPressed) {
                    // Detect double-tap for elytra takeoff
                    long now = System.currentTimeMillis();
                    FollowMovement followForTap = GeneralMenuSettings.getFollowMovement();
                    if (followForTap != null && followForTap.isAutoRunAndJump()
                            && (now - lastFollowPressTimeMs) <= DOUBLE_TAP_THRESHOLD_MS) {
                        followForTap.triggerElytraTakeoff(client);
                    }
                    lastFollowPressTimeMs = now;

                    CraneshotClient.MOVEMENT_MANAGER.startFollowMovement(client, camera);
                } else {
                    CraneshotClient.MOVEMENT_MANAGER.stopFollowMovement(client, camera);
                }
                followWasPressed = followPressed;
            }

            FollowMovement follow = GeneralMenuSettings.getFollowMovement();
            if (follow != null) {
                boolean followActive = followPressed && CraneshotClient.MOVEMENT_MANAGER.getActiveMovement() == follow;
                if (followActive) {
                    follow.tickAutoRunAndJump(client);
                } else {
                    follow.stopAutoRunAndJump(client);
                }
            }

            // Handle zoom key
            boolean zoomPressed = CraneshotClient.zoomKey != null && CraneshotClient.zoomKey.isDown();
            if (zoomPressed != zoomWasPressed) {
                if (zoomPressed) {
                    CraneshotClient.MOVEMENT_MANAGER.startZoomMovement(client, camera);
                } else {
                    CraneshotClient.MOVEMENT_MANAGER.stopZoomMovement(client, camera);
                }
                zoomWasPressed = zoomPressed;
            }

            for (int i = 0; i < CraneshotClient.cameraKeyBinds.length; i++) {
                boolean currentlyPressed = CraneshotClient.cameraKeyBinds[i].isDown();
                boolean wasPressed = keyStates.getOrDefault(i, false);

                if (!followPressed) {
                    if (currentlyPressed != wasPressed) {
                        boolean isToggle = SlotMenuSettings.getToggleState(i);
                        CraneshotClient.MOVEMENT_MANAGER.handleKeyStateChange(i, currentlyPressed, client, camera, isToggle);
                        if (currentlyPressed) {
                            lastActiveSlot = i;
                        }
                    }
                }
                keyStates.put(i, currentlyPressed);
            }
        });

        MovementToastRenderer.register();
    }

    private static void handleRespawnAndWakeReset(Minecraft client, Camera camera) {
        if (client == null || client.player == null) {
            lastAlive = false;
            lastSleeping = false;
            lastPlayerUuid = null;
            return;
        }

        boolean isAlive = client.player.isAlive();
        boolean isSleeping = client.player.isSleeping();
        java.util.UUID playerUuid = client.player.getUUID();

        boolean respawned = (!lastAlive && isAlive) ||
            (lastPlayerUuid != null && !lastPlayerUuid.equals(playerUuid) && isAlive);
        boolean wokeUp = lastSleeping && !isSleeping;

        if (respawned || wokeUp) {
            CraneshotClient.MOVEMENT_MANAGER.cancelAllMovements(client, camera);
        }

        lastAlive = isAlive;
        lastSleeping = isSleeping;
        lastPlayerUuid = playerUuid;
    }

    private static void handleDimensionChange(Minecraft client, Camera camera) {
        if (client == null || client.level == null) {
            lastDimension = null;
            return;
        }
        // If player is null (e.g. during portal entity recreation), don't clear lastDimension.
        // Keeping the old value ensures the dimension change is detected once the player reappears.
        if (client.player == null) {
            return;
        }

        ResourceKey<Level> currentDimension = client.level.dimension();
        CameraType currentPerspective = client.options.getCameraType();

        if (lastDimension != null && !lastDimension.equals(currentDimension)) {
            // Dimension changed - snap camera to player's new position.
            // Don't cancel movements - the LinearMovement snap check will handle
            // correcting its internal position on the next render frame. Cancelling
            // movements is too destructive (deactivates camera, resets perspective/modes).
            Vec3 playerEyePos = client.player.getEyePosition();
            float playerYaw = client.player.getYRot();
            float playerPitch = client.player.getXRot();

            // Snap CameraEntity if it exists
            ninja.trek.util.CameraEntity cameraEntity = ninja.trek.util.CameraEntity.getCamera();
            if (cameraEntity != null) {
                cameraEntity.setPosRaw(playerEyePos.x, playerEyePos.y, playerEyePos.z);
                cameraEntity.setYRot(playerYaw);
                cameraEntity.setXRot(playerPitch);
            }

            // Snap CameraSystem position if active
            ninja.trek.camera.CameraSystem cameraSystem = ninja.trek.camera.CameraSystem.getInstance();
            if (cameraSystem.isCameraActive()) {
                cameraSystem.setCameraPosition(playerEyePos);
                cameraSystem.setCameraRotation(playerYaw, playerPitch);
                cameraSystem.resetVelocity();
            }

            // Reset base target and lastPlayerPos to prevent stale cross-dimension data
            CraneshotClient.MOVEMENT_MANAGER.resetBaseTarget();
            lastPlayerPos = null;
        }

        lastDimension = currentDimension;
        lastPerspective = currentPerspective;
    }

    private static void handleLargePositionJumps(Minecraft client, Camera camera) {
        if (client == null) {
            lastPlayerPos = null;
            return;
        }
        // If player is null (e.g. during portal entity recreation), don't clear lastPlayerPos.
        // Keeping the old value ensures the large jump is detected once the player reappears.
        if (client.player == null) {
            return;
        }

        Vec3 currentPlayerPos = new Vec3(client.player.getX(), client.player.getY(), client.player.getZ());

        // Check for large position jumps (teleports, respawns, portals we missed, etc.)
        if (lastPlayerPos != null) {
            double distanceMoved = currentPlayerPos.distanceTo(lastPlayerPos);

            if (distanceMoved > LARGE_POSITION_JUMP_THRESHOLD) {
                // Player jumped a large distance - snap camera to prevent long travel.
                // Don't cancel movements - the LinearMovement snap check will handle
                // correcting its internal position on the next render frame.
                Vec3 playerEyePos = client.player.getEyePosition();
                float playerYaw = client.player.getYRot();
                float playerPitch = client.player.getXRot();

                // Snap CameraEntity if it exists
                ninja.trek.util.CameraEntity cameraEntity = ninja.trek.util.CameraEntity.getCamera();
                if (cameraEntity != null) {
                    cameraEntity.setPosRaw(playerEyePos.x, playerEyePos.y, playerEyePos.z);
                    cameraEntity.setYRot(playerYaw);
                    cameraEntity.setXRot(playerPitch);
                }

                // Snap CameraSystem position if active
                ninja.trek.camera.CameraSystem cameraSystem = ninja.trek.camera.CameraSystem.getInstance();
                if (cameraSystem.isCameraActive()) {
                    cameraSystem.setCameraPosition(playerEyePos);
                    cameraSystem.setCameraRotation(playerYaw, playerPitch);
                    cameraSystem.resetVelocity();
                }

                // Reset base target so movement recalculates from current state
                CraneshotClient.MOVEMENT_MANAGER.resetBaseTarget();
            }
        }

        lastPlayerPos = currentPlayerPos;
    }

    /**
     * Safely get the scroll value from the mouse mixin
     * @param client The Minecraft client instance
     * @return The scroll value, or 0 if it couldn't be accessed
     */
    private static double getScrollValue(Minecraft client) {
        try {
            if (client.mouseHandler instanceof IMouseMixin mouseMixin) {
                return mouseMixin.getLastScrollValue();
            }
        } catch (Exception e) {
            // logging removed
        }
        return 0;
    }
    
    /**
     * Safely reset the scroll value in the mouse mixin
     * @param client The Minecraft client instance
     */
    private static void resetScrollValue(Minecraft client) {
        try {
            if (client.mouseHandler instanceof IMouseMixin mouseMixin) {
                mouseMixin.setLastScrollValue(0);
            }
        } catch (Exception e) {
            // logging removed
        }
    }

    private static ICameraMovement followerMovementInstance = null;
    private static String lastFollowerMovementType = null;
    private static long lastSpectatorCheckTime = 0;
    private static final long SPECTATOR_CHECK_INTERVAL_MS = 3000; // Check every 3 seconds

    private static void handleFollowerMode(Minecraft client, Camera camera) {
        if (client.player == null) return;

        // Auto-respawn if dead (e.g. died while not in spectator mode)
        if (client.player.isDeadOrDying()) {
            client.player.respawn();
            client.gui.setScreen(null);
            return;
        }

        // Periodically ensure we're in spectator mode and near the target player
        long now = System.currentTimeMillis();
        if (now - lastSpectatorCheckTime > SPECTATOR_CHECK_INTERVAL_MS) {
            lastSpectatorCheckTime = now;
            if (!client.player.isSpectator() && client.player.connection != null) {
                client.player.connection.sendCommand("gamemode spectator");
                Craneshot.LOGGER.info("Follower mode: re-requesting spectator gamemode");
            }
            // Only teleport when necessary: target not loaded, different dimension, or very far away
            String tpTarget = GeneralMenuSettings.getTargetPlayerName();
            if (tpTarget != null && !tpTarget.isEmpty() && client.player.connection != null) {
                boolean shouldTeleport = true;
                Player targetPlayer = CameraController.getTrackedPlayer(client);
                if (targetPlayer != null && targetPlayer != client.player) {
                    // Target is loaded and in the same dimension - check distance
                    double distance = client.player.distanceTo(targetPlayer);
                    if (distance < 100.0) {
                        shouldTeleport = false;
                    }
                }
                if (shouldTeleport) {
                    client.player.connection.sendCommand("tp " + tpTarget);
                }
            }
        }

        // Periodically check if the config file was changed by the primary instance
        boolean configChanged = ninja.trek.config.FollowerMode.checkForConfigChange();

        FollowerConfig config = ninja.trek.config.FollowerMode.getConfig();
        if (config == null) return;

        // Set target player name from shared config
        String targetName = config.getTargetPlayerName();
        if (targetName != null && !targetName.isEmpty()) {
            GeneralMenuSettings.setTargetPlayerName(targetName);
            GeneralMenuSettings.setSpectatorFollowEnabled(true);
        }

        int index = ninja.trek.config.FollowerMode.getFollowerIndex();
        FollowerConfig.FollowerEntry entry = config.getFollower(index);

        // If our follower index no longer exists (entry removed), stop the movement
        if (entry == null) {
            if (followerMovementInstance != null) {
                CraneshotClient.MOVEMENT_MANAGER.cancelAllMovements(client, camera);
                followerMovementInstance = null;
                lastFollowerMovementType = null;
                ninja.trek.config.FollowerMode.setFollowerMovementStarted(false);
            }
            return;
        }

        // Propagate per-entry zone toggle to the global zone flag
        GeneralMenuSettings.setZonesEnabled(entry.isUseZones());

        boolean shouldStart = configChanged
                || followerMovementInstance == null
                || !ninja.trek.config.FollowerMode.isFollowerMovementStarted()
                || CraneshotClient.MOVEMENT_MANAGER.getActiveMovement() == null;
        if (shouldStart) {
            ICameraMovement newMovement = createFollowerMovement(index, entry);
            if (newMovement != null) {
                CraneshotClient.MOVEMENT_MANAGER.cancelAllMovements(client, camera);
                followerMovementInstance = newMovement;
                lastFollowerMovementType = newMovement.getClass().getName();
                CraneshotClient.MOVEMENT_MANAGER.startFollowerMovement(newMovement, client, camera);
                ninja.trek.config.FollowerMode.setFollowerMovementStarted(true);
                return;
            }
        }

        // Auto-restart if the movement completed
        if (followerMovementInstance != null) {
            CraneshotClient.MOVEMENT_MANAGER.restartFollowerMovementIfComplete(followerMovementInstance, client, camera);
        }
    }

    private static ICameraMovement createFollowerMovement(int index, FollowerConfig.FollowerEntry entry) {
        if (index == 0 && entry.isDirectorEnabled()) {
            return new FollowerCameraDirector(entry);
        }
        return entry.getMovement();
    }

    private static void handleScrollInput(Minecraft client) {
        double currentTime = System.currentTimeMillis() / 1000.0;
        if (currentTime - lastScrollTime < SCROLL_COOLDOWN) {
            return;
        }

        double scrollDelta = getScrollValue(client);
        if (scrollDelta == 0) {
            return;
        }

        boolean scrollUp = scrollDelta < 0;

        // Check for active movement with scroll modes
        AbstractMovementSettings.SCROLL_WHEEL activeScrollMode =
                CraneshotClient.MOVEMENT_MANAGER.getActiveMouseWheelMode();

        // Check for zoom overlay first (takes priority)
        ninja.trek.cameramovements.movements.ZoomMovement zoomOverlay =
                CraneshotClient.MOVEMENT_MANAGER.getActiveZoomOverlay();
        if (zoomOverlay != null && activeScrollMode == AbstractMovementSettings.SCROLL_WHEEL.FOV) {
            zoomOverlay.adjustFov(!scrollUp, client);
            lastScrollTime = currentTime;
            resetScrollValue(client);
            return;
        }

        ICameraMovement activeMovement = CraneshotClient.MOVEMENT_MANAGER.getActiveMovement();
        if (activeMovement != null) {
            if (activeScrollMode == AbstractMovementSettings.SCROLL_WHEEL.DISTANCE) {
                activeMovement.adjustDistance(!scrollUp, client);
                lastScrollTime = currentTime;
                resetScrollValue(client);
                return;
            } else if (activeScrollMode == AbstractMovementSettings.SCROLL_WHEEL.FOV) {
                if (activeMovement instanceof AbstractMovementSettings) {
                    ((AbstractMovementSettings) activeMovement).adjustFov(!scrollUp, client);
                    lastScrollTime = currentTime;
                    resetScrollValue(client);
                    return;
                }
            }
        }
        
        // Handle normal slot scrolling if no active scroll modes
        for (int i = 0; i < CraneshotClient.cameraKeyBinds.length; i++) {
            if (CraneshotClient.cameraKeyBinds[i].isDown()) {
                CraneshotClient.MOVEMENT_MANAGER.handleMouseScroll(i, scrollUp);
                lastScrollTime = currentTime;
                resetScrollValue(client);
                return;
            }
        }

        // Handle scroll with select movement key pressed
        if (CraneshotClient.selectMovementType.isDown() && lastActiveSlot != null) {
            CraneshotClient.MOVEMENT_MANAGER.handleMouseScroll(lastActiveSlot, scrollUp);
            lastScrollTime = currentTime;
            resetScrollValue(client);
        }
    }
}
