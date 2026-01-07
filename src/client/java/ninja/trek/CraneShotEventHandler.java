package ninja.trek;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import ninja.trek.cameramovements.AbstractMovementSettings;
import ninja.trek.cameramovements.ICameraMovement;
import ninja.trek.cameramovements.movements.FollowMovement;
import ninja.trek.config.GeneralMenuSettings;
import ninja.trek.config.SlotMenuSettings;

import java.util.HashMap;
import java.util.Map;

public class CraneShotEventHandler {
    private static final double SCROLL_COOLDOWN = 0.1;
    private static double lastScrollTime = 0;
    private static final Map<Integer, Boolean> keyStates = new HashMap<>();
    private static Integer lastActiveSlot = null;
    private static boolean followWasPressed = false;
    private static boolean zoomWasPressed = false;
    private static boolean lastAlive = true;
    private static boolean lastSleeping = false;
    private static java.util.UUID lastPlayerUuid = null;
    private static RegistryKey<World> lastDimension = null;
    private static Vec3d lastPlayerPos = null;
    private static final double LARGE_POSITION_JUMP_THRESHOLD = 50.0; // blocks

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

            Camera camera = client.gameRenderer.getCamera();
            handleRespawnAndWakeReset(client, camera);
            handleDimensionChange(client, camera);
            handleLargePositionJumps(client, camera);

            boolean followPressed = CraneshotClient.followMovementKey != null && CraneshotClient.followMovementKey.isPressed();
            if (followPressed != followWasPressed) {
                if (followPressed) {
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
            boolean zoomPressed = CraneshotClient.zoomKey != null && CraneshotClient.zoomKey.isPressed();
            if (zoomPressed != zoomWasPressed) {
                if (zoomPressed) {
                    CraneshotClient.MOVEMENT_MANAGER.startZoomMovement(client, camera);
                } else {
                    CraneshotClient.MOVEMENT_MANAGER.stopZoomMovement(client, camera);
                }
                zoomWasPressed = zoomPressed;
            }

            for (int i = 0; i < CraneshotClient.cameraKeyBinds.length; i++) {
                boolean currentlyPressed = CraneshotClient.cameraKeyBinds[i].isPressed();
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

    private static void handleRespawnAndWakeReset(MinecraftClient client, Camera camera) {
        if (client == null || client.player == null) {
            lastAlive = false;
            lastSleeping = false;
            lastPlayerUuid = null;
            return;
        }

        boolean isAlive = client.player.isAlive();
        boolean isSleeping = client.player.isSleeping();
        java.util.UUID playerUuid = client.player.getUuid();

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

    private static void handleDimensionChange(MinecraftClient client, Camera camera) {
        if (client == null || client.world == null || client.player == null) {
            lastDimension = null;
            return;
        }

        RegistryKey<World> currentDimension = client.world.getRegistryKey();

        if (lastDimension != null && !lastDimension.equals(currentDimension)) {
            // Dimension changed - snap camera to player's new position
            // This prevents the camera from having to travel massive distances
            Vec3d playerEyePos = client.player.getEyePos();
            float playerYaw = client.player.getYaw();
            float playerPitch = client.player.getPitch();

            // Snap CameraEntity if it exists
            ninja.trek.util.CameraEntity cameraEntity = ninja.trek.util.CameraEntity.getCamera();
            if (cameraEntity != null) {
                cameraEntity.setPos(playerEyePos.x, playerEyePos.y, playerEyePos.z);
                cameraEntity.setYaw(playerYaw);
                cameraEntity.setPitch(playerPitch);
            }

            // Snap CameraSystem position if active (BEFORE canceling movements)
            ninja.trek.camera.CameraSystem cameraSystem = ninja.trek.camera.CameraSystem.getInstance();
            boolean wasCameraActive = cameraSystem.isCameraActive();
            Vec3d snappedCameraPos = null;
            float snappedYaw = 0;
            float snappedPitch = 0;

            if (wasCameraActive) {
                cameraSystem.setCameraPosition(playerEyePos);
                cameraSystem.setCameraRotation(playerYaw, playerPitch);
                cameraSystem.resetVelocity();
                snappedCameraPos = playerEyePos;
                snappedYaw = playerYaw;
                snappedPitch = playerPitch;
            }

            // Cancel movements to clear old world references
            CraneshotClient.MOVEMENT_MANAGER.cancelAllMovements(client, camera);

            // If camera was active, reactivate it at the snapped position
            if (wasCameraActive && snappedCameraPos != null) {
                cameraSystem.activateCamera(ninja.trek.camera.CameraSystem.CameraMode.THIRD_PERSON);
                cameraSystem.setCameraPosition(snappedCameraPos);
                cameraSystem.setCameraRotation(snappedYaw, snappedPitch);
                cameraSystem.resetVelocity();
            }
        }

        lastDimension = currentDimension;
    }

    private static void handleLargePositionJumps(MinecraftClient client, Camera camera) {
        if (client == null || client.player == null) {
            lastPlayerPos = null;
            return;
        }

        Vec3d currentPlayerPos = new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ());

        // Check for large position jumps (teleports, respawns, portals we missed, etc.)
        if (lastPlayerPos != null) {
            double distanceMoved = currentPlayerPos.distanceTo(lastPlayerPos);

            if (distanceMoved > LARGE_POSITION_JUMP_THRESHOLD) {
                // Player jumped a large distance - snap camera to prevent long travel
                Vec3d playerEyePos = client.player.getEyePos();
                float playerYaw = client.player.getYaw();
                float playerPitch = client.player.getPitch();

                // Snap CameraEntity if it exists
                ninja.trek.util.CameraEntity cameraEntity = ninja.trek.util.CameraEntity.getCamera();
                if (cameraEntity != null) {
                    cameraEntity.setPos(playerEyePos.x, playerEyePos.y, playerEyePos.z);
                    cameraEntity.setYaw(playerYaw);
                    cameraEntity.setPitch(playerPitch);
                }

                // Snap CameraSystem position if active
                ninja.trek.camera.CameraSystem cameraSystem = ninja.trek.camera.CameraSystem.getInstance();
                if (cameraSystem.isCameraActive()) {
                    cameraSystem.setCameraPosition(playerEyePos);
                    cameraSystem.setCameraRotation(playerYaw, playerPitch);
                    cameraSystem.resetVelocity();
                }

                // Reset base target
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
    private static double getScrollValue(MinecraftClient client) {
        try {
            if (client.mouse instanceof IMouseMixin mouseMixin) {
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
    private static void resetScrollValue(MinecraftClient client) {
        try {
            if (client.mouse instanceof IMouseMixin mouseMixin) {
                mouseMixin.setLastScrollValue(0);
            }
        } catch (Exception e) {
            // logging removed
        }
    }

    private static void handleScrollInput(MinecraftClient client) {
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
            if (CraneshotClient.cameraKeyBinds[i].isPressed()) {
                CraneshotClient.MOVEMENT_MANAGER.handleMouseScroll(i, scrollUp);
                lastScrollTime = currentTime;
                resetScrollValue(client);
                return;
            }
        }

        // Handle scroll with select movement key pressed
        if (CraneshotClient.selectMovementType.isPressed() && lastActiveSlot != null) {
            CraneshotClient.MOVEMENT_MANAGER.handleMouseScroll(lastActiveSlot, scrollUp);
            lastScrollTime = currentTime;
            resetScrollValue(client);
        }
    }
}
