package ninja.trek;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
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
