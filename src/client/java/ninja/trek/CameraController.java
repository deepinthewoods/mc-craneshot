package ninja.trek;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import ninja.trek.camera.CameraSystem;
import ninja.trek.cameramovements.AbstractMovementSettings;
import ninja.trek.cameramovements.AbstractMovementSettings.POST_MOVE_KEYS;
import ninja.trek.cameramovements.AbstractMovementSettings.POST_MOVE_MOUSE;
import ninja.trek.cameramovements.CameraTarget;
import ninja.trek.config.FreeCamSettings;
import ninja.trek.config.GeneralMenuSettings;
import ninja.trek.mixin.client.CameraAccessor;
import ninja.trek.mixin.client.FovAccessor;
import ninja.trek.mixin.client.KeyBindingAccessor;

public class CameraController {
    public static POST_MOVE_KEYS currentKeyMoveMode = POST_MOVE_KEYS.NONE;
    public static POST_MOVE_MOUSE currentMouseMoveMode = POST_MOVE_MOUSE.NONE;
    public static Vec3 freeCamPosition = Vec3.ZERO;
    public static float freeCamYaw = 0f;
    public static float freeCamPitch = 0f;
    public static CameraTarget controlStick = new CameraTarget();

    // Target player tracking for spectator mode
    private static Player cachedTargetPlayer = null;
    private static String cachedTargetPlayerName = "";
    private static long lastTargetCheckTime = 0;
    private static final long TARGET_CHECK_INTERVAL_MS = 1000; // Check every 1 second

    // Track whether the camera has been moved with keyboard input
    public static boolean hasMovedWithKeyboard = false;

    // Save sneak key state before entering free movement to restore on exit
    private static boolean savedSneakKeyState = false;

    /**
     * Check if a key is physically held down, ignoring toggle/hold settings.
     */
    public static boolean isKeyPhysicallyHeld(Minecraft client, KeyMapping keyBinding) {
        InputConstants.Key boundKey = ((KeyBindingAccessor) keyBinding).getBoundKey();
        return InputConstants.isKeyDown(client.getWindow(), boundKey.getValue());
    }

    // Track if camera was activated by node influence
    private static boolean cameraActivatedByNodes = false;
    private static double lastNodeInfluence = 0.0;

    private String currentMessage = "";
    private long messageTimer = 0;
    private static final long MESSAGE_DURATION = 2000;


    public static AbstractMovementSettings.END_TARGET currentEndTarget = AbstractMovementSettings.END_TARGET.HEAD_BACK;
    public static float currentYawOffset = 0f;
    private Vec3 lastPlayerPos = Vec3.ZERO;
    private Vec3 cumulativeMovement = Vec3.ZERO;
    private float targetYaw = 0f;
    private static final double FULL_ROTATE_DISTANCE = 2.0; // Blocks to move for full rotation

    private Vec3 currentVelocity = Vec3.ZERO;
    private boolean lockPlayerHead = false;
    private float lockedPlayerYaw = 0f;
    private float lockedPlayerPitch = 0f;
    private float lockedPlayerHeadYaw = 0f;
    private float lockedPlayerBodyYaw = 0f;

    /**
     * Attempts to resolve the target player entity from the configured name.
     * Uses caching to avoid searching every frame.
     *
     * @param client Minecraft client instance
     * @return The target PlayerEntity, or null if not found
     */
    private static Player resolveTargetPlayer(Minecraft client) {
        if (client == null || client.level == null) {
            cachedTargetPlayer = null;
            return null;
        }

        String targetName = GeneralMenuSettings.getTargetPlayerName();

        // If target name is empty, clear cache and return null (use local player)
        if (targetName == null || targetName.trim().isEmpty()) {
            cachedTargetPlayer = null;
            cachedTargetPlayerName = "";
            return null;
        }

        // Use cached player if name hasn't changed and player is still valid
        long now = System.currentTimeMillis();
        if (targetName.equals(cachedTargetPlayerName) &&
            cachedTargetPlayer != null &&
            !cachedTargetPlayer.isRemoved() &&
            now - lastTargetCheckTime < TARGET_CHECK_INTERVAL_MS) {
            return cachedTargetPlayer;
        }

        // Search for player by name
        lastTargetCheckTime = now;
        cachedTargetPlayerName = targetName;
        cachedTargetPlayer = null;

        for (Player player : client.level.players()) {
            if (player.getName().getString().equalsIgnoreCase(targetName)) {
                cachedTargetPlayer = player;
                break;
            }
        }

        return cachedTargetPlayer;
    }

    /**
     * Returns the player entity that camera movements should track.
     * In spectator mode with a configured target, returns the target player.
     * Otherwise returns the local player.
     */
    public static Player getTrackedPlayer(Minecraft client) {
        if (client == null || client.player == null) return null;
        if (shouldUseTargetPlayer(client)) {
            Player target = resolveTargetPlayer(client);
            if (target != null) {
                return target;
            }
        }
        return client.player;
    }

    /**
     * Checks if target player following is currently active.
     * Only active when: enabled, in spectator mode, and target player is found.
     */
    private static boolean shouldUseTargetPlayer(Minecraft client) {
        if (client == null || client.player == null) {
            return false;
        }

        // Only work in spectator mode
        if (!client.player.isSpectator()) {
            return false;
        }

        // Check if feature is enabled
        if (!GeneralMenuSettings.isSpectatorFollowEnabled()) {
            return false;
        }

        // Check if we have a valid target
        Player target = resolveTargetPlayer(client);
        return target != null;
    }

    private void updateControlStick(Minecraft client, float tickDelta) {
        if (currentKeyMoveMode != POST_MOVE_KEYS.MOVE_CAMERA_FLAT &&
                currentKeyMoveMode != POST_MOVE_KEYS.MOVE_CAMERA_FREE) {

            if (client.player == null) return;

            // Determine which player to track
            Player trackedPlayer = client.player;
            if (shouldUseTargetPlayer(client)) {
                Player target = resolveTargetPlayer(client);
                if (target != null) {
                    trackedPlayer = target;
                }
                // If target is null, falls back to client.player
            }

            Camera camera = client.gameRenderer.mainCamera();
            if (camera != null) {
                Vec3 eyePos = trackedPlayer.getEyePosition(tickDelta);
                float yaw = trackedPlayer.getViewYRot(tickDelta);
                float pitch = trackedPlayer.getViewXRot(tickDelta);

                // Update movement tracking for VELOCITY targets
                if (currentEndTarget == AbstractMovementSettings.END_TARGET.VELOCITY_BACK ||
                        currentEndTarget == AbstractMovementSettings.END_TARGET.VELOCITY_FRONT) {
                    // Track the target player's position (not local player)
                    updateMovementTracking(new Vec3(trackedPlayer.getX(), trackedPlayer.getY(), trackedPlayer.getZ()));
                }

                // Calculate final angles based on target type
                float finalYaw = calculateTargetYaw(yaw);
                float finalPitch = calculateTargetPitch(pitch);

                controlStick.set(eyePos, finalYaw, finalPitch);
            }
        }
    }

    private float calculateTargetYaw(float playerYaw) {
        float baseYaw;
        switch (currentEndTarget) {
            case HEAD_BACK:
                baseYaw = playerYaw;
                break;
            case HEAD_FRONT:
                baseYaw = (playerYaw + 180);
                break;
            case VELOCITY_BACK:
                baseYaw = 360-targetYaw;
                break;
            case VELOCITY_FRONT:
                baseYaw = (360-targetYaw + 180)%360;
                break;
            case FIXED_BACK:
                baseYaw = playerYaw;
                break;
            case FIXED_FRONT:
                baseYaw = (playerYaw + 180);
                break;
            default:
                baseYaw = playerYaw;
                break;
        }
        return baseYaw + currentYawOffset;
    }

    private float calculateTargetPitch(float playerPitch) {
        switch (currentEndTarget) {
            case HEAD_FRONT:
                return -playerPitch;
            case HEAD_BACK:
                return playerPitch;
            case VELOCITY_FRONT:
            case FIXED_FRONT:
                return 45f;  // Looking down at player
            case VELOCITY_BACK:
            case FIXED_BACK:
                return 45f; // Looking up from behind
            default:
                return playerPitch;
        }
    }

    private void updateMovementTracking(Vec3 currentPos) {
        if (lastPlayerPos.equals(Vec3.ZERO)) {
            lastPlayerPos = currentPos;
            return;
        }

        // Calculate movement in XZ plane
        Vec3 movement = new Vec3(
                currentPos.x - lastPlayerPos.x,
                0,
                currentPos.z - lastPlayerPos.z
        );

        if (movement.lengthSqr() > 0.001) { // Only update if there's significant movement
            cumulativeMovement = cumulativeMovement.add(movement);

            // Calculate movement direction (Minecraft coordinates)
            double movementYaw = Math.toDegrees(Math.atan2(movement.x, movement.z));
            while (movementYaw < 0) movementYaw += 360;

            // Linear interpolation based on cumulative movement distance
            double moveDistance = cumulativeMovement.length();
            double progress = Math.min(moveDistance / FULL_ROTATE_DISTANCE, 1.0);

            // Update target yaw
            targetYaw = (float)movementYaw;

            // Reset cumulative movement if we've reached full rotation
            if (moveDistance >= FULL_ROTATE_DISTANCE) {
                cumulativeMovement = Vec3.ZERO;
            }
        }

        lastPlayerPos = currentPos;
    }

    public void setPreMoveStates(AbstractMovementSettings m){
        currentEndTarget = m.getEndTarget();
        currentYawOffset = m.getYawOffset();
        // Reset any FOV modifications when starting a new movement
        Minecraft client = Minecraft.getInstance();
        if (client.gameRenderer.mainCamera() instanceof FovAccessor) {
            ((FovAccessor) client.gameRenderer.mainCamera()).setFovModifier(1.0f);
        }
    }

    public void setPostMoveStates(AbstractMovementSettings m) {
        // If node edit is active, ignore requests to clear post-move state so freecam persists
        if (m == null) {
            // Reset state when movement ends
            currentKeyMoveMode = POST_MOVE_KEYS.NONE;
            currentMouseMoveMode = POST_MOVE_MOUSE.NONE;
            currentYawOffset = 0f;
            MouseInterceptor.setIntercepting(false);
            clearPlayerHeadLock();

            // Reset tracking variables
            lastPlayerPos = Vec3.ZERO;
            cumulativeMovement = Vec3.ZERO;
            
            // Reset keyboard input handling
            Minecraft client = Minecraft.getInstance();
            if (client.player != null && client.player.input instanceof IKeyboardInputMixin) {
                ((IKeyboardInputMixin) client.player.input).setDisabled(false);
            }
            // Restore sneak key state to what it was before entering free movement.
            // During free movement, shift key presses for camera movement can toggle
            // the sneak key binding state, causing the player to sneak on return.
            client.options.keyShift.setDown(savedSneakKeyState);
            // Close node editor if open
            net.minecraft.client.Minecraft _mc = net.minecraft.client.Minecraft.getInstance();
            if (_mc != null && _mc.gui.screen() instanceof ninja.trek.nodes.ui.NodeEditorScreen) {
                _mc.gui.setScreen(null);
            }
            ninja.trek.nodes.NodeManager.get().setEditing(false);
            CameraSystem cs = CameraSystem.getInstance();
            boolean wasActive = cs.isCameraActive();
            
        } else {
            // Set new movement modes
            currentMouseMoveMode = m.getPostMoveMouse();
            currentKeyMoveMode = m.getPostMoveKeys();

            Minecraft client = Minecraft.getInstance();
            Camera camera = client.gameRenderer.mainCamera();
            
            // Reset the keyboard movement tracking flag when entering a new camera mode
            hasMovedWithKeyboard = false;
            
            // This is where the position must be preserved
            // freeCamPosition, freeCamYaw, and freeCamPitch should already be set by CameraMovementManager
            // before this method is called, so we don't need to capture them again here
            
            // Handle input disabling
            if (client.player != null && client.player.input instanceof IKeyboardInputMixin) {
                boolean shouldDisable = (currentKeyMoveMode == POST_MOVE_KEYS.MOVE_CAMERA_FLAT ||
                        currentKeyMoveMode == POST_MOVE_KEYS.MOVE_CAMERA_FREE);
                if (shouldDisable) {
                    // Save sneak key state before disabling input, so we can restore it
                    // when exiting free movement (prevents shift presses for camera-down
                    // from toggling sneak on return)
                    savedSneakKeyState = client.options.keyShift.isDown();
                }
                ((IKeyboardInputMixin) client.player.input).setDisabled(shouldDisable);
            }

            // Enable mouse interception for camera rotation
            if (currentMouseMoveMode == POST_MOVE_MOUSE.ROTATE_CAMERA) {
                MouseInterceptor.setIntercepting(true);
                capturePlayerHeadLock(client);
            }
            if (currentMouseMoveMode != POST_MOVE_MOUSE.ROTATE_CAMERA) {
                clearPlayerHeadLock();
            }
            // Handle node edit overlay activation
            if (currentMouseMoveMode == POST_MOVE_MOUSE.NODE_EDIT) {
                boolean keysOk = (currentKeyMoveMode == POST_MOVE_KEYS.MOVE_CAMERA_FLAT || currentKeyMoveMode == POST_MOVE_KEYS.MOVE_CAMERA_FREE);
                if (keysOk) {
                    ninja.trek.nodes.NodeManager.get().setEditing(true);
                    if (!(client.gui.screen() instanceof ninja.trek.nodes.ui.NodeEditorScreen)) {
                        client.gui.setScreen(new ninja.trek.nodes.ui.NodeEditorScreen());
                    }
                    // Do NOT intercept mouse globally while screen is open; screen handles drag
                    MouseInterceptor.setIntercepting(false);
                } else {
                    currentMouseMoveMode = POST_MOVE_MOUSE.NONE;
                    ninja.trek.nodes.NodeManager.get().setEditing(false);
                }
            } else {
                // Ensure editor closed if not in node edit mode
                if (client.gui.screen() instanceof ninja.trek.nodes.ui.NodeEditorScreen) {
                    client.gui.setScreen(null);
                }
                ninja.trek.nodes.NodeManager.get().setEditing(false);
            }
            // Determine if we should activate custom camera mode (include NODE_EDIT)
            boolean isFreeCamMode = (currentKeyMoveMode == POST_MOVE_KEYS.MOVE_CAMERA_FLAT ||
                                    currentKeyMoveMode == POST_MOVE_KEYS.MOVE_CAMERA_FREE ||
                                    currentMouseMoveMode == POST_MOVE_MOUSE.ROTATE_CAMERA ||
                                    currentMouseMoveMode == POST_MOVE_MOUSE.NODE_EDIT);
            
            boolean isOutPosition = (currentEndTarget == AbstractMovementSettings.END_TARGET.HEAD_BACK ||
                                    currentEndTarget == AbstractMovementSettings.END_TARGET.FIXED_BACK ||
                                    currentEndTarget == AbstractMovementSettings.END_TARGET.VELOCITY_BACK);
            
            // Capture existing camera position before activating any new camera mode
            Vec3 existingCameraPos = camera.position();
            float existingYaw = camera.yRot();
            float existingPitch = camera.xRot();
            
            // Activate the appropriate camera mode
            CameraSystem cameraSystem = CameraSystem.getInstance();
            if (isFreeCamMode) {
                // If this movement remembers its free-cam pose, try to restore it for the
                // current dimension before we hand the pose to the camera system.
                if (m.isSaveFreeCamPose() && client.level != null) {
                    String dimKey = client.level.dimension().identifier().toString();
                    AbstractMovementSettings.SavedPose saved = m.getSavedPose(dimKey);
                    if (saved != null && ninja.trek.util.CameraUtils.isPoseWithinRenderDistance(client, saved.position)) {
                        freeCamPosition = saved.position;
                        freeCamYaw = saved.yaw;
                        freeCamPitch = saved.pitch;
                    }
                }

                // Set the position first, so it's available during activation
                cameraSystem.setCameraPosition(freeCamPosition);
                cameraSystem.setCameraRotation(freeCamYaw, freeCamPitch);
                
                // Then activate the camera
                cameraSystem.activateCamera(CameraSystem.CameraMode.FREE_CAMERA);
                
                // Update the camera immediately to apply our position
                cameraSystem.updateCamera(camera);
                
            }
        }
    }

    private void handleKeyboardMovement(Minecraft client, Camera camera) {
        if (client.player == null) return;

        // Let the camera system handle movement
        CameraSystem cameraSystem = CameraSystem.getInstance();
        if (cameraSystem.isCameraActive()) {
            FreeCamSettings settings = GeneralMenuSettings.getFreeCamSettings();
            boolean moved = cameraSystem.handleMovementInput(
                settings.getMoveSpeed(),
                settings.getAcceleration(),
                settings.getDeceleration()
            );
            
            // Track if camera moved when using the camera system
            if (moved) {
                hasMovedWithKeyboard = true;
            }
            return;
        }

        // Legacy movement code
        // Base movement speed in blocks per tick
        float baseSpeed = GeneralMenuSettings.getFreeCamSettings().getMoveSpeed();

        // Sprint multiplier
        if (client.options.keySprint.isDown()) {
            baseSpeed *= 3.0f;
        }

        Vec3 targetVelocity = Vec3.ZERO;
        
        // Calculate movement direction
        if (currentKeyMoveMode == POST_MOVE_KEYS.MOVE_CAMERA_FREE) {
            // Free camera movement in all directions
            double x = 0, y = 0, z = 0;
            
            if (client.options.keyUp.isDown()) {
                z += 1.0;
            }
            if (client.options.keyDown.isDown()) {
                z -= 1.0;
            }
            if (client.options.keyLeft.isDown()) {
                x += 1.0;
            }
            if (client.options.keyRight.isDown()) {
                x -= 1.0;
            }
            if (client.options.keyJump.isDown()) {
                y += 1.0;
            }
            if (isKeyPhysicallyHeld(client, client.options.keyShift)) {
                y -= 1.0;
            }
            
            // Normalize if moving in multiple directions simultaneously
            if ((x != 0 && z != 0) || (x != 0 && y != 0) || (z != 0 && y != 0)) {
                double length = Math.sqrt(x * x + y * y + z * z);
                x /= length;
                y /= length;
                z /= length;
            }
            
            // Convert to camera-relative movement
            double yawRad = Math.toRadians(freeCamYaw);
            double pitchRad = Math.toRadians(freeCamPitch);
            Vec3 forward = new Vec3(
                -Math.sin(yawRad) * Math.cos(pitchRad),
                -Math.sin(pitchRad),
                Math.cos(yawRad) * Math.cos(pitchRad)
            );
            Vec3 right = forward.cross(new Vec3(0.0, 1.0, 0.0));
            if (right.lengthSqr() < 1.0E-6) {
                right = new Vec3(-Math.cos(yawRad), 0.0, -Math.sin(yawRad));
            } else {
                right = right.normalize();
            }
            Vec3 up = right.cross(forward).normalize();
            targetVelocity = forward.scale(z).add(right.scale(-x)).add(up.scale(y));
            
        } else if (currentKeyMoveMode == POST_MOVE_KEYS.MOVE_CAMERA_FLAT) {
            // Y-axis locked camera movement
            double x = 0, z = 0;
            
            if (client.options.keyUp.isDown()) {
                z += 1.0;
            }
            if (client.options.keyDown.isDown()) {
                z -= 1.0;
            }
            if (client.options.keyLeft.isDown()) {
                x += 1.0;
            }
            if (client.options.keyRight.isDown()) {
                x -= 1.0;
            }
            
            // Y movement from jump/sneak
            double y = 0;
            if (client.options.keyJump.isDown()) {
                y += 1.0;
            }
            if (isKeyPhysicallyHeld(client, client.options.keyShift)) {
                y -= 1.0;
            }
            
            // Normalize XZ movement
            if (x != 0 && z != 0) {
                double length = Math.sqrt(x * x + z * z);
                x /= length;
                z /= length;
            }
            
            // Convert to camera-relative XZ movement with free Y
            float yaw = freeCamYaw;
            double xFactor = Math.sin(yaw * Math.PI / 180.0);
            double zFactor = Math.cos(yaw * Math.PI / 180.0);
            
            targetVelocity = new Vec3(
                (x * zFactor - z * xFactor), 
                y, 
                (z * zFactor + x * xFactor)
            );
        }

        // Normalize and apply speed to target velocity if there's any movement
        if (targetVelocity.lengthSqr() > 0.0001) {
            targetVelocity = targetVelocity.normalize().scale(baseSpeed);
        }

        // Apply acceleration or deceleration
        float acceleration = GeneralMenuSettings.getFreeCamSettings().getAcceleration();
        float deceleration = GeneralMenuSettings.getFreeCamSettings().getDeceleration();

        if (targetVelocity.lengthSqr() > 0.0001) {
            // Accelerating
            currentVelocity = currentVelocity.add(
                    targetVelocity.subtract(currentVelocity).scale(acceleration)
            );
            
            // Mark as moved with keyboard if acceleration is happening
            if (!hasMovedWithKeyboard) {
                hasMovedWithKeyboard = true;
            }
        } else {
            // Decelerating
            currentVelocity = currentVelocity.scale(1.0 - deceleration);
            // Zero out very small velocities to prevent perpetual drift
            if (currentVelocity.lengthSqr() < 0.0001) {
                currentVelocity = Vec3.ZERO;
            }
        }

        // Apply movement
        freeCamPosition = freeCamPosition.add(currentVelocity);
        ((CameraAccessor) camera).invokesetPos(freeCamPosition);
    }

    public void updateCamera(Minecraft client, Camera camera, float tickDelta, float deltaSeconds) {
        updateControlStick(client, tickDelta);

        // Cache interpolated player position for consistent rendering decisions
        // This must be done BEFORE any rendering decisions (like shouldRenderPlayerModel)
        CameraSystem cameraSystem = CameraSystem.getInstance();
        if (client.player != null) {
            Vec3 interpolatedEyePos = client.player.getEyePosition(tickDelta);
            cameraSystem.updateInterpolatedPlayerPosition(interpolatedEyePos);
        }

        // Get the base camera state from movement manager - always update to track state
        CameraTarget baseTarget = CraneshotClient.MOVEMENT_MANAGER.update(client, camera, deltaSeconds);

        // Skip node influence when:
        // - Zones are disabled in settings
        // - In freecam/edit modes (manual camera control)
        boolean skipNodeInfluence = !GeneralMenuSettings.isZonesEnabled()
                || ninja.trek.nodes.NodeManager.get().isEditing()
                || currentKeyMoveMode != POST_MOVE_KEYS.NONE
                || currentMouseMoveMode != POST_MOVE_MOUSE.NONE
                ;

        baseTarget = ninja.trek.nodes.NodeManager.get().applyInfluence(baseTarget, skipNodeInfluence);

        // Handle node-based camera activation/deactivation
        double currentNodeInfluence = 0.0;
        if (!skipNodeInfluence && client.player != null) {
            currentNodeInfluence = ninja.trek.nodes.NodeManager.get().getTotalInfluence(client.player.getEyePosition());
        }

        // Activate camera when nodes start influencing
        if (currentNodeInfluence > 0.0 && !cameraActivatedByNodes && !skipNodeInfluence) {
            // Only activate if not already active from another source
            if (!cameraSystem.isCameraActive()) {
                cameraSystem.setCameraPosition(baseTarget.getPosition());
                cameraSystem.setCameraRotation(baseTarget.getYaw(), baseTarget.getPitch());
                cameraSystem.activateCamera(CameraSystem.CameraMode.NODE_DRIVEN);
                cameraActivatedByNodes = true;
            }
        }
        // Deactivate and trigger return when influence drops to 0
        // BUT: Don't deactivate if we're in freecam/edit mode (those modes own the camera now)
        else if (currentNodeInfluence <= 0.0 && cameraActivatedByNodes && lastNodeInfluence > 0.0 && !skipNodeInfluence) {
            // Camera was active due to nodes, but influence dropped to zero
            // Capture current camera position before deactivating
            freeCamPosition = cameraSystem.getCameraPosition();
            freeCamYaw = cameraSystem.getCameraYaw();
            freeCamPitch = cameraSystem.getCameraPitch();

            // Deactivate camera system
            cameraSystem.deactivateCamera();

            // Start FreeCamReturnMovement to smoothly return to player
            if (client != null) {
                ninja.trek.cameramovements.movements.FreeCamReturnMovement returnMovement =
                    ninja.trek.config.GeneralMenuSettings.getFreeCamReturnMovement();
                returnMovement.start(client, camera);

                // Manually set it as the active movement in the manager
                // (this mimics what finishTransition does)
                CraneshotClient.MOVEMENT_MANAGER.setActiveMovementForNodeReturn(returnMovement);
            }

            cameraActivatedByNodes = false;
        }

        lastNodeInfluence = currentNodeInfluence;

        // Check if we have an active camera system
        boolean cameraSystemActive = cameraSystem.isCameraActive();

        if (baseTarget != null) {
            // Update FOV in game renderer
            if (client.gameRenderer.mainCamera() instanceof FovAccessor) {
                float fovMultiplier = (float) baseTarget.getFovMultiplier();
                ((FovAccessor) client.gameRenderer.mainCamera()).setFovModifier(fovMultiplier);
            }
            
            if (cameraSystemActive) {
                // Let the camera system update its state
                boolean rotating = (currentMouseMoveMode == POST_MOVE_MOUSE.ROTATE_CAMERA) || ninja.trek.nodes.NodeManager.get().isEditing();
                boolean entityFreecam = (ninja.trek.util.CameraEntity.getCamera() != null);

                // If camera is being controlled by nodes, force position/rotation from baseTarget
                if (cameraActivatedByNodes && currentNodeInfluence > 0.0) {
                    // Nodes are controlling the camera - override position/rotation
                    cameraSystem.setCameraPosition(baseTarget.getPosition());
                    cameraSystem.setCameraRotation(baseTarget.getYaw(), baseTarget.getPitch());

                    // Synchronize CameraEntity if it exists
                    if (entityFreecam) {
                        ninja.trek.util.CameraEntity camEnt = ninja.trek.util.CameraEntity.getCamera();
                        if (camEnt != null) {
                            Vec3 pos = baseTarget.getPosition();
                            camEnt.setPosRaw(pos.x, pos.y, pos.z);
                            camEnt.setCameraRotations(baseTarget.getYaw(), baseTarget.getPitch());
                            camEnt.setDeltaMovement(Vec3.ZERO); // Prevent physics interference
                        }
                    }
                }
                // Avoid per-frame logs; rely on Diag guards inside CameraSystem
                else if (entityFreecam) {
                    // Route mouse input to CameraSystem (not CameraEntity)
                    // CameraEntity is just a ghost for chunk rendering
                    if (rotating && client.mouseHandler instanceof IMouseMixin) {
                        IMouseMixin mouseMixin = (IMouseMixin) client.mouseHandler;
                        double deltaX = mouseMixin.getCapturedDeltaX();
                        double deltaY = -mouseMixin.getCapturedDeltaY();
                        if (deltaX != 0 || deltaY != 0) {
                            double mouseSensitivity = client.options.sensitivity().get();
                            double calculatedSensitivity = 0.6 * mouseSensitivity * mouseSensitivity * mouseSensitivity + 0.2;
                            // Adjust sensitivity based on FOV for zoom
                            if (baseTarget != null) {
                                calculatedSensitivity *= baseTarget.getFovMultiplier();
                            }
                            // Route to CameraSystem instead of CameraEntity
                            cameraSystem.updateRotation(
                                deltaX * calculatedSensitivity * 0.55D,
                                deltaY * calculatedSensitivity * 0.55D,
                                1.0
                            );
                        }
                    }
                } else {
                    if (rotating && client.mouseHandler instanceof IMouseMixin) {
                        IMouseMixin mouseMixin = (IMouseMixin) client.mouseHandler;
                        double deltaX = mouseMixin.getCapturedDeltaX();
                        double deltaY = -mouseMixin.getCapturedDeltaY();
                        if (deltaX != 0 || deltaY != 0) {
                            double mouseSensitivity = client.options.sensitivity().get();
                            double calculatedSensitivity = 0.6 * mouseSensitivity * mouseSensitivity * mouseSensitivity + 0.2;
                            // Adjust sensitivity based on FOV for zoom
                            if (baseTarget != null) {
                                calculatedSensitivity *= baseTarget.getFovMultiplier();
                            }
                            cameraSystem.updateRotation(
                                deltaX * calculatedSensitivity * 0.55D,
                                deltaY * calculatedSensitivity * 0.55D,
                                1.0
                            );
                        }
                    } else if (!cameraActivatedByNodes) {
                        // Use the movement manager's position/rotation if not freely rotating and not controlled by nodes
                        cameraSystem.setCameraPosition(baseTarget.getPosition());
                        cameraSystem.setCameraRotation(baseTarget.getYaw(), baseTarget.getPitch());
                    }
                }

                // Apply rotation easing and update camera
                cameraSystem.applyRotationEasing(deltaSeconds);
                cameraSystem.updateCamera(camera);

                // Sync the ghost CameraEntity to match CameraSystem
                cameraSystem.syncCameraEntity();

                // Update our tracking variables for legacy code support
                freeCamPosition = cameraSystem.getCameraPosition();
                freeCamYaw = cameraSystem.getCameraYaw();
                freeCamPitch = cameraSystem.getCameraPitch();
            } else {
                // Legacy camera handling
                // Only update freeCamPosition from movement if we're not in free movement mode
                if (currentKeyMoveMode != POST_MOVE_KEYS.MOVE_CAMERA_FLAT &&
                        currentKeyMoveMode != POST_MOVE_KEYS.MOVE_CAMERA_FREE) {
                    freeCamPosition = baseTarget.getPosition();
                }

                // Handle rotation based on movement mode
                if (currentMouseMoveMode == POST_MOVE_MOUSE.ROTATE_CAMERA && client.mouseHandler instanceof IMouseMixin) {
                    IMouseMixin mouseMixin = (IMouseMixin) client.mouseHandler;
                    double deltaX = mouseMixin.getCapturedDeltaX();
                    double deltaY = -mouseMixin.getCapturedDeltaY();
                    double mouseSensitivity = client.options.sensitivity().get();
                    double calculatedSensitivity = 0.6 * mouseSensitivity * mouseSensitivity * mouseSensitivity + 0.2;
                    // Adjust sensitivity based on FOV for zoom
                    if (baseTarget != null) {
                        calculatedSensitivity *= baseTarget.getFovMultiplier();
                    }
                    deltaX *= calculatedSensitivity * 0.55D;
                    deltaY *= calculatedSensitivity * 0.55D;
                    if (deltaX != 0 || deltaY != 0) {
                        freeCamYaw += deltaX;
                        freeCamPitch = (float) Math.max(-90.0F, Math.min(90.0F, freeCamPitch - deltaY));
                    }
                } else {
                    freeCamYaw = baseTarget.getYaw();
                    freeCamPitch = baseTarget.getPitch();
                }

                // Apply the camera position and rotation (no log)
                ((CameraAccessor) camera).invokesetPos(freeCamPosition);
                ((CameraAccessor) camera).invokeSetRotation(freeCamYaw, freeCamPitch);
            }
        } else if (cameraSystemActive) {
            // If we have no target but the camera system is active, let it update
            cameraSystem.updateCamera(camera);
            
            // Update tracking variables
            freeCamPosition = cameraSystem.getCameraPosition();
            freeCamYaw = cameraSystem.getCameraYaw();
            freeCamPitch = cameraSystem.getCameraPitch();
        }

        // Handle keyboard movement for camera modes (also when editor is open)
        if (currentKeyMoveMode == POST_MOVE_KEYS.MOVE_CAMERA_FLAT ||
            currentKeyMoveMode == POST_MOVE_KEYS.MOVE_CAMERA_FREE) {
            handleKeyboardMovement(client, camera);
        }

        applyPlayerHeadLock(client);

        updateMessageTimer();
    }



    //=== Message Handling ========================================================

    public void showMessage(String message) {
        currentMessage = message;
        messageTimer = System.currentTimeMillis() + MESSAGE_DURATION;
    }

    public String getCurrentMessage() {
        return currentMessage;
    }

    public boolean hasActiveMessage() {
        return System.currentTimeMillis() < messageTimer;
    }

    private void updateMessageTimer() {
        if (System.currentTimeMillis() >= messageTimer) {
            currentMessage = "";
        }
    }

    /**
     * Updates the camera each frame. This method (which used to be called handleCameraUpdate)
     * is responsible for applying the computed camera state (from the movement manager) as well as
     * processing any free keyboard/mouse input. It should be placed in CameraController.
     */
    public void handleCameraUpdate(BlockGetter area, Entity focusedEntity, boolean thirdPerson,
                                   boolean inverseView, float tickDelta, float frameSeconds, Camera camera) {
        // Verify that both the camera and the focused entity exist.
        if (camera == null || focusedEntity == null) return;

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) return;

        // Disable camera control when sleeping to avoid clipping inside the player's head
        if (client.player != null && client.player.isSleeping()) {
            return;
        }

        // Update the camera based on movement-manager and free control states.
        updateCamera(client, camera, tickDelta, frameSeconds);

        // Optionally update keyboard input (e.g. disable it when free control is active)
        updateKeyboardInput(client);
    }

    private void updateKeyboardInput(Minecraft client) {
        if (client.player != null && client.player.input instanceof IKeyboardInputMixin) {
            // Only disable player movement when our post-move mode requires camera keyboard control.
            // Do NOT blanket-disable just because the camera system is active; Bezier out-phase needs player input.
            boolean shouldDisable = currentKeyMoveMode == POST_MOVE_KEYS.MOVE8 ||
                                   currentKeyMoveMode == POST_MOVE_KEYS.MOVE_CAMERA_FLAT ||
                                   currentKeyMoveMode == POST_MOVE_KEYS.MOVE_CAMERA_FREE;
            ((IKeyboardInputMixin) client.player.input).setDisabled(shouldDisable);
        }
    }

    public void onComplete() {
        // If node edit is active, preserve freecam and input states
        if (ninja.trek.nodes.NodeManager.get().isEditing()) {
            return;
        }

        // Reset all movement modes completely
        currentMouseMoveMode = POST_MOVE_MOUSE.NONE;
        currentKeyMoveMode = POST_MOVE_KEYS.NONE;
        currentYawOffset = 0f;
        clearPlayerHeadLock();

        // Reset the keyboard movement tracking flag
        hasMovedWithKeyboard = false;

        // Reset node activation tracking
        cameraActivatedByNodes = false;
        lastNodeInfluence = 0.0;

        // Ensure keyboard input is enabled for the player
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.player != null && client.player.input instanceof IKeyboardInputMixin) {
            ((IKeyboardInputMixin) client.player.input).setDisabled(false);
        }

        // Disable mouse interception
        MouseInterceptor.setIntercepting(false);

        // Make sure to restore default camera behavior by deactivating the camera system
        CameraSystem cameraSystem = CameraSystem.getInstance();
        if (cameraSystem.isCameraActive()) {
            cameraSystem.deactivateCamera();
        }

        // Reset the camera position to follow the player
        if (client != null && client.player != null) {
            Vec3 eyePos = client.player.getEyePosition();
            float yaw = client.player.getYRot();
            float pitch = client.player.getXRot();

            freeCamPosition = eyePos;
            freeCamYaw = yaw;
            freeCamPitch = pitch;

            Camera camera = client.gameRenderer.mainCamera();
            if (camera != null) {
                ((CameraAccessor) camera).invokesetPos(eyePos);
                ((CameraAccessor) camera).invokeSetRotation(yaw, pitch);
            }

            // Keep internal camera state aligned so reactivation doesn't snap to stale positions.
            cameraSystem.setCameraPosition(eyePos);
            cameraSystem.setCameraRotation(yaw, pitch);
            cameraSystem.resetVelocity();

            ninja.trek.util.CameraEntity camEnt = ninja.trek.util.CameraEntity.getCamera();
            if (camEnt != null) {
                camEnt.setPosRaw(eyePos.x, eyePos.y, eyePos.z);
                camEnt.setCameraRotations(yaw, pitch);
                camEnt.setDeltaMovement(Vec3.ZERO);
            }
        }

        // Reset FOV to default
        if (client != null && client.gameRenderer.mainCamera() instanceof FovAccessor) {
            ((FovAccessor) client.gameRenderer.mainCamera()).setFovModifier(1.0f);
        }
    }

    private void capturePlayerHeadLock(Minecraft client) {
        if (client == null || client.player == null) return;
        Player player = client.player;
        lockPlayerHead = true;
        lockedPlayerYaw = player.getYRot();
        lockedPlayerPitch = player.getXRot();
        lockedPlayerHeadYaw = player.getYHeadRot();
        lockedPlayerBodyYaw = player.getVisualRotationYInDegrees();
    }

    private void clearPlayerHeadLock() {
        lockPlayerHead = false;
    }

    private void applyPlayerHeadLock(Minecraft client) {
        if (!lockPlayerHead || client == null || client.player == null) return;
        Player player = client.player;
        player.setYRot(lockedPlayerYaw);
        player.setXRot(lockedPlayerPitch);
        player.setYHeadRot(lockedPlayerHeadYaw);
        player.setYBodyRot(lockedPlayerBodyYaw);
         
    }
}
