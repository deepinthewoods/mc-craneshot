# FollowMovement implementation plan

## Goal

Add a new camera movement `FollowMovement` that is activated while a dedicated keybind is held, and deactivates (returns camera) when the key is released.

While active:
- Camera rises to a configurable height above the player.
- Camera does not move in XZ until the player exceeds a configurable XZ threshold (“deadzone”); once exceeded, camera moves just enough to keep the player at that threshold distance.
- Camera only moves in Y when the player is standing, or when the player exceeds a configurable Y threshold (“vertical deadzone”), like platformer cameras.

## Decisions (resolved)

- Activation: hold-to-follow (start on key down, stop on key up).
- Deactivation: smooth return via `queueReset()` back to player eye pos.
- Interaction: follow overrides any active movement immediately.
- Reference point: use player eye position (`CameraController.controlStick.getPosition()` / `player.getEyePos()` on reset).
- XZ threshold: radial deadzone.
- Vertical rule: treat “standing” as `player.isOnGround()`; while airborne, freeze Y unless beyond threshold.
- Position smoothing: XZ movement uses `LinearMovement`-style easing + speed cap, driven by `CameraController.controlStick`.
- Visibility: keybind-only (not selectable via slots / registry).

## Settings (movement fields)

Implement these as `@MovementSetting` fields on `FollowMovement` (and let `AbstractMovementSettings` cover raycast/FOV/post-move options):

- `followHeight` (double): target vertical offset above player eye.
- `xzThreshold` (double): radial horizontal deadzone before camera follows in XZ.
- `yThreshold` (double): vertical deadzone; while airborne, Y only updates when exceeded.
- `positionEasing` (double) + `positionSpeedLimit` (double): smoothing/speed cap for camera position changes.
- `rotationEasing` (double) + `rotationSpeedLimit` (double): to follow `controlStick` yaw/pitch smoothly.

## Keybind + general settings

Add a “global FollowMovement” instance similar to Default Idle Movement:

- In `ninja.trek.config.GeneralMenuSettings`:
  - `private static final FollowMovement followMovement = new FollowMovement();`
  - `public static FollowMovement getFollowMovement()`
  - (Optional) `private static boolean enableFollowKeybind = true;` if you want a menu toggle
- In `ninja.trek.CraneshotClient`:
  - Register `KeyBinding followMovementKey` (category `craneshot:camera`).
- In `ninja.trek.CraneShotEventHandler`:
  - Track pressed state transitions for `followMovementKey` and call movement-manager methods:
    - On press: start FollowMovement
    - On release: stop FollowMovement (queue reset)

Persist follow settings like `defaultIdleMovement`:
- In `ninja.trek.config.GeneralSettingsIO`:
  - Save/load `followMovement` JSON object via `AbstractMovementSettings.getSettings()` and `updateSetting(...)`.
  - Save/load optional `enableFollowKeybind` flag if added.

Expose follow settings in the menu like Default Idle Movement:
- In `ninja.trek.config.MenuOverlayScreen`:
  - Add a collapsible “Follow Movement” section that uses the same reflection-based `collectSettingFields(...)` + `createSettingControl(...)` pattern as Default Idle Movement.

## Movement-manager integration

Add two small public helpers to `ninja.trek.CameraMovementManager`:

- `public void startFollowMovement(MinecraftClient client, Camera camera)`
  - Set `activeMovementSlot = null`
  - Set `activeMovement = GeneralMenuSettings.getFollowMovement()`
  - Clear post-move states (`CAMERA_CONTROLLER.setPostMoveStates(null)`)
  - Call `movement.start(client, camera)` and `setPreMoveStates(...)`
  - Set `isOut = false` (and ideally ensure `inFreeCamReturnPhase = false`)
- `public void stopFollowMovement(MinecraftClient client, Camera camera)`
  - Only act if `activeMovement == GeneralMenuSettings.getFollowMovement()`
  - Call `activeMovement.queueReset(client, camera)` for smooth return

This keeps follow mode consistent with existing movement lifecycle (active until `queueReset()` completes).

## FollowMovement algorithm (proposed)

File: `src/client/java/ninja/trek/cameramovements/movements/FollowMovement.java`

State to store:
- `CameraTarget current`
- `boolean resetting`
- (Optional) “final interp” state like `LinearMovement` for guaranteed arrival during reset.

Per-frame logic (non-resetting):
1. Compute player reference position `playerRef`:
   - `playerRef = CameraController.controlStick.getPosition()`
2. Maintain a deadzoned follow-center `followCenter` in XZ (platformer-style):
   - If `distance(playerXZ, followCenterXZ) > xzThreshold`, shift `followCenterXZ` so the player lies on the threshold boundary.
3. Compute desired XZ as an orbit around `followCenter` using `controlStick` yaw:
   - Maintain a persistent `orbitTargetXZ` (world position) and rotate it around the player by `deltaYaw = controlStickYaw - lastControlStickYaw`.
   - This makes the orbit “complete” even with position easing (the target stays rotated until the camera reaches it).
   - After the player has started moving, clamp `distance(playerXZ, orbitTargetXZ)` to `xzThreshold` to enforce the deadzone.
3. Compute desired Y:
   - `targetY = playerRef.y + followHeight`
   - If `player.isOnGround()`: follow toward `targetY`
   - Else (airborne): only follow if `abs(targetY - current.y) > yThreshold`, otherwise hold Y
4. Move `current.position` toward `desiredPos` using easing + speed limit (mirrors `LinearMovement`’s “eased” branch).
5. Set yaw/pitch toward `CameraController.controlStick` yaw/pitch using rotation easing + speed limit.
6. Return `new MovementState(current, false)`

Reset logic (`queueReset` + `calculateState` while `resetting`):
- Match `LinearMovement` behavior: target `player.getEyePos()` and player yaw/pitch, and complete when close enough.

## File checklist

- Add `src/client/java/ninja/trek/cameramovements/movements/FollowMovement.java`
- Update `src/client/java/ninja/trek/config/GeneralMenuSettings.java`
- Update `src/client/java/ninja/trek/config/GeneralSettingsIO.java`
- Update `src/client/java/ninja/trek/config/MenuOverlayScreen.java`
- Update `src/client/java/ninja/trek/CraneshotClient.java`
- Update `src/client/java/ninja/trek/CraneShotEventHandler.java`
- Update `src/client/java/ninja/trek/CameraMovementManager.java`

## Validation (no tests)

- Manual in-game checks:
  - Hold follow key: camera transitions upward, XZ stays until deadzone exceeded, vertical deadzone works while jumping.
  - Release follow key: camera returns smoothly (no snap), input states restore correctly.
  - Verify it doesn’t interfere with slot movements unless intended (depends on clarification #2).
