# Spectator Mode: Follow Target Player Implementation Plan

## Overview
Enable the camera system to follow a specific target player (instead of the local player) when in spectator mode. All existing camera movements (Linear, Bezier, Follow, etc.) and camera targets (HEAD_BACK, VELOCITY_BACK, etc.) will automatically work with the target player by modifying how `controlStick` is updated.

## Requirements
- **Global Setting**: Single setting that affects all camera movements
- **Configuration**: Via config file/menu (user types player name)
- **Fallback Behavior**: Fall back to local player if target not found
- **Mode Restriction**: Only works when local player is in spectator mode
- **Seamless Integration**: No changes needed to existing movement classes

---

## Architecture Changes

### 1. Add Target Player Settings to GeneralMenuSettings

**File**: `src/client/java/ninja/trek/config/GeneralMenuSettings.java`

**Changes**:
```java
public class GeneralMenuSettings {
    // Add new fields
    private static String targetPlayerName = "";  // Empty = use local player
    private static boolean spectatorFollowEnabled = true;  // Master toggle

    // Add getters/setters
    public static String getTargetPlayerName() { return targetPlayerName; }
    public static void setTargetPlayerName(String name) {
        targetPlayerName = (name == null) ? "" : name.trim();
    }

    public static boolean isSpectatorFollowEnabled() { return spectatorFollowEnabled; }
    public static void setSpectatorFollowEnabled(boolean enabled) {
        spectatorFollowEnabled = enabled;
    }
}
```

**Rationale**:
- `targetPlayerName`: Empty string means "use local player" (default behavior)
- `spectatorFollowEnabled`: Allows quick enable/disable without clearing the name
- Trim whitespace to avoid issues with accidental spaces

---

### 2. Modify CameraController to Support Target Player

**File**: `src/client/java/ninja/trek/CameraController.java`

#### 2a. Add Target Player Resolution

**Add new fields** (after line 24):
```java
// Target player tracking for spectator mode
private static PlayerEntity cachedTargetPlayer = null;
private static String cachedTargetPlayerName = "";
private static long lastTargetCheckTime = 0;
private static final long TARGET_CHECK_INTERVAL_MS = 1000; // Check every 1 second
```

**Add new method** (helper to find target player):
```java
/**
 * Attempts to resolve the target player entity from the configured name.
 * Uses caching to avoid searching every frame.
 *
 * @param client Minecraft client instance
 * @return The target PlayerEntity, or null if not found
 */
private static PlayerEntity resolveTargetPlayer(MinecraftClient client) {
    if (client == null || client.world == null) {
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

    for (PlayerEntity player : client.world.getPlayers()) {
        if (player.getName().getString().equalsIgnoreCase(targetName)) {
            cachedTargetPlayer = player;
            break;
        }
    }

    return cachedTargetPlayer;
}

/**
 * Checks if target player following is currently active.
 * Only active when: enabled, in spectator mode, and target player is found.
 */
private static boolean shouldUseTargetPlayer(MinecraftClient client) {
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
    PlayerEntity target = resolveTargetPlayer(client);
    return target != null;
}
```

#### 2b. Modify updateControlStick Method

**Replace `updateControlStick` method** (starting at line 47):

```java
private void updateControlStick(MinecraftClient client, float tickDelta) {
    if (currentKeyMoveMode != POST_MOVE_KEYS.MOVE_CAMERA_FLAT &&
            currentKeyMoveMode != POST_MOVE_KEYS.MOVE_CAMERA_FREE) {

        if (client.player == null) return;

        // Determine which player to track
        PlayerEntity trackedPlayer = client.player;
        if (shouldUseTargetPlayer(client)) {
            PlayerEntity target = resolveTargetPlayer(client);
            if (target != null) {
                trackedPlayer = target;
            }
            // If target is null, falls back to client.player
        }

        Camera camera = client.gameRenderer.getCamera();
        if (camera != null) {
            Vec3d eyePos = trackedPlayer.getCameraPosVec(tickDelta);
            float yaw = trackedPlayer.getYaw(tickDelta);
            float pitch = trackedPlayer.getPitch(tickDelta);

            // Update movement tracking for VELOCITY targets
            if (currentEndTarget == AbstractMovementSettings.END_TARGET.VELOCITY_BACK ||
                    currentEndTarget == AbstractMovementSettings.END_TARGET.VELOCITY_FRONT) {
                // Track the target player's position (not local player)
                updateMovementTracking(new Vec3d(trackedPlayer.getX(), trackedPlayer.getY(), trackedPlayer.getZ()));
            }

            // Calculate final angles based on target type
            float finalYaw = calculateTargetYaw(yaw);
            float finalPitch = calculateTargetPitch(pitch);

            controlStick.set(eyePos, finalYaw, finalPitch);
        }
    }
}
```

**Rationale**:
- Determines which player to track (local or target)
- Falls back to local player if target is not found/invalid
- Velocity tracking uses target player's position
- Minimal changes to existing logic

---

### 3. Update Config Save/Load

**File**: `src/client/java/ninja/trek/config/GeneralSettingsIO.java`

#### 3a. Save Settings (in `saveSettings()` method)

**Add after line 113** (after autoAdvance):
```java
// Save spectator follow settings
settingsObj.addProperty("spectatorFollowEnabled", GeneralMenuSettings.isSpectatorFollowEnabled());
settingsObj.addProperty("targetPlayerName", GeneralMenuSettings.getTargetPlayerName());
```

#### 3b. Load Settings (in `loadSettings()` method)

**Add after line 292** (after autoAdvance loading):
```java
// Load spectator follow settings
if (settingsObj.has("spectatorFollowEnabled")) {
    try {
        GeneralMenuSettings.setSpectatorFollowEnabled(
            settingsObj.get("spectatorFollowEnabled").getAsBoolean()
        );
    } catch (Exception ignored) {}
}

if (settingsObj.has("targetPlayerName")) {
    try {
        GeneralMenuSettings.setTargetPlayerName(
            settingsObj.get("targetPlayerName").getAsString()
        );
    } catch (Exception ignored) {}
}
```

---

### 4. Add UI Controls to Settings Menu

**File**: `src/client/java/ninja/trek/config/MenuOverlayScreen.java`

**Location**: In the "General" tab rendering section (need to find where general settings are rendered)

**Add**:
1. **Toggle button** for spectator follow enabled/disabled
2. **Text field** for target player name input
3. **Status indicator** showing:
   - "Following: [PlayerName]" (when target found)
   - "Target not found - using local player" (when target not found)
   - "Disabled - not in spectator mode" (when not in spectator)
   - "Disabled - following local player" (when feature disabled or no target)

**Implementation Notes**:
- Use existing UI widget patterns from the codebase (SettingSlider, buttons, etc.)
- Text field should:
  - Show placeholder text: "Player name (empty = local player)"
  - Update `GeneralMenuSettings.setTargetPlayerName()` on change
  - Trigger `GeneralSettingsIO.saveSettings()` on change
- Status indicator updates every frame to reflect current state
- Consider adding a "Test" button that checks if the target player can be found

---

## Edge Cases & Error Handling

### 1. Target Player Disconnects
- **Detection**: `resolveTargetPlayer()` will return null on next check
- **Behavior**: Automatically fall back to local player
- **User Experience**: Status indicator updates to show "Target not found - using local player"

### 2. Target Player Changes Dimension
- **Detection**: Player entity will be removed from current world's player list
- **Behavior**: Same as disconnect - fall back to local player
- **Alternative**: Could track UUID and search all dimensions, but simpler to fall back

### 3. Local Player Exits Spectator Mode
- **Detection**: `shouldUseTargetPlayer()` returns false when `!client.player.isSpectator()`
- **Behavior**: Immediately switch to using local player
- **User Experience**: Camera smoothly transitions to local player's perspective

### 4. Empty/Null Target Name
- **Detection**: `targetPlayerName.trim().isEmpty()`
- **Behavior**: Always use local player (default behavior)
- **User Experience**: Normal operation, feature effectively disabled

### 5. Target Player Name Has Spaces
- **Detection**: `setTargetPlayerName()` trims the input
- **Behavior**: Leading/trailing spaces removed automatically
- **Note**: Player names with spaces in the middle are valid in some server configs

### 6. Multiple Players with Similar Names
- **Current Behavior**: First match wins (case-insensitive)
- **Potential Improvement**: Could use UUID instead of name for precision
  - **Decision**: Start with name for simplicity, add UUID support later if needed

---

## Testing Checklist

### Basic Functionality
- [ ] Set target player name in config → saves and loads correctly
- [ ] In spectator mode, camera follows target player instead of local player
- [ ] All camera movements (Linear, Bezier, Follow, Static) work with target player
- [ ] All camera targets (HEAD_BACK, VELOCITY_BACK, FIXED_BACK, etc.) work with target player
- [ ] Empty target name → uses local player
- [ ] Toggle disabled → uses local player even with name set

### Spectator Mode Enforcement
- [ ] Target following works in spectator mode
- [ ] Target following disabled in survival mode
- [ ] Target following disabled in creative mode
- [ ] Target following disabled in adventure mode
- [ ] Switching from spectator to survival → immediately uses local player

### Fallback Behavior
- [ ] Target player not found → uses local player
- [ ] Target player disconnects mid-session → smoothly falls back to local player
- [ ] Target player changes dimension → falls back to local player
- [ ] Local player reconnects and target player is back → resumes following

### UI/Config
- [ ] Text field shows current target player name
- [ ] Status indicator accurately reflects current state
- [ ] Changes to text field immediately update the setting
- [ ] Settings persist across game restarts
- [ ] Settings save when closing the menu

### Performance
- [ ] Caching reduces player lookups (not searching every frame)
- [ ] No noticeable performance impact with 10+ players online
- [ ] Smooth camera movement even with network latency

### Edge Cases
- [ ] Target player name with unusual characters (underscores, numbers)
- [ ] Case-insensitive matching works (e.g., "PlayerName" matches "playername")
- [ ] Very long player names don't break UI
- [ ] Rapid switching between target players
- [ ] Local player spectating target player with F5 mode cycling

---

## Implementation Order

### Phase 1: Core Functionality (Backend)
1. Add settings fields to `GeneralMenuSettings`
2. Add player resolution logic to `CameraController`
3. Modify `updateControlStick()` to use target player
4. Test manually by editing config file directly

### Phase 2: Config Persistence
5. Update `GeneralSettingsIO.saveSettings()`
6. Update `GeneralSettingsIO.loadSettings()`
7. Test config save/load

### Phase 3: UI Integration
8. Add toggle button to settings menu
9. Add text field for player name
10. Add status indicator
11. Test full workflow with UI

### Phase 4: Polish & Testing
12. Edge case testing
13. Performance testing
14. Documentation updates
15. Add helpful tooltips/descriptions in UI

---

## Alternative Approaches Considered

### Approach A: UUID Instead of Player Name
**Pros**:
- More precise (no ambiguity with similar names)
- Works even if player changes name

**Cons**:
- UUIDs are not user-friendly (e.g., `550e8400-e29b-41d4-a716-446655440000`)
- Harder to configure manually
- Requires UI to copy UUID from player

**Decision**: Start with name for usability. Can add UUID support later as an advanced option.

---

### Approach B: Custom Networking to Sync Camera Data
**Pros**:
- Higher update frequency than player entity sync
- Could send custom camera positions

**Cons**:
- Requires server-side mod/plugin
- Much more complex
- Adds network overhead
- Breaks compatibility with vanilla servers

**Decision**: Not needed. Player entity sync is sufficient and works on any server.

---

### Approach C: New "FollowPlayer" Movement Type
**Pros**:
- Isolated functionality
- Existing movements unchanged

**Cons**:
- Duplicates code from existing movements
- User must switch to specific movement type
- Doesn't work with all camera targets (HEAD_BACK, VELOCITY_BACK, etc.)

**Decision**: Modifying `controlStick` is cleaner and more powerful.

---

## Future Enhancements (Out of Scope)

1. **UUID Support**: Add option to specify player by UUID for precision
2. **Target Selection UI**: Click on player in world to target them (like spectator mode)
3. **Target History**: Dropdown of recently tracked players
4. **Multi-Target**: Interpolate between multiple players (average position)
5. **Offset Configuration**: Add position offset relative to target (e.g., always 2 blocks to the left)
6. **Auto-Spectate**: Automatically enter spectator mode when target following is enabled
7. **Keybind**: Quick keybind to toggle target following on/off
8. **Target Indicators**: Render marker above target player's head
9. **Smooth Dimension Transitions**: When target changes dimension, smoothly transition instead of instant fallback
10. **Distance Warnings**: Alert when target player is too far away (>render distance)

---

## Notes & Considerations

- **Network Latency**: Target player position may lag slightly due to server → client sync. This is inherent to Minecraft's networking and cannot be fully eliminated client-side.

- **Render Distance**: If target player is beyond render distance, they won't be in the world's player list. Feature will fall back to local player automatically.

- **Nametag vs Username**: Using `player.getName().getString()` gets the display name, which should match the username in most cases. Server plugins that modify display names might cause issues.

- **Third-Party Compatibility**: Should work on any server (vanilla, Spigot, Paper, Fabric) since it only uses client-side player entity data.

- **Spectator Mode Camera**: When in spectator mode, the vanilla camera is already detached. Our mod's camera system integrates well with this.

- **Auto-Run & Jump**: The `FollowMovement` auto-run feature still controls the LOCAL player, not the target. This is intentional - we're only following the target with the camera, not making our player move.

---

## File Changes Summary

| File | Changes | Lines (est.) |
|------|---------|--------------|
| `GeneralMenuSettings.java` | Add target player settings | +15 |
| `CameraController.java` | Add target resolution + modify updateControlStick | +80 |
| `GeneralSettingsIO.java` | Add save/load for target settings | +20 |
| `MenuOverlayScreen.java` | Add UI controls for target player | +100 |
| **Total** | | **~215 lines** |

**Estimated Implementation Time**: 2-3 hours for core functionality, 1-2 hours for UI, 1 hour for testing.

---

## Success Criteria

The implementation is successful when:

1. ✅ A second Minecraft account (in spectator mode) can follow the first account's player
2. ✅ Camera uses the existing Follow movement settings (height, distance, easing, etc.)
3. ✅ Configuration is simple (just type player name in settings menu)
4. ✅ Works on any dedicated server without server-side mods
5. ✅ Gracefully handles edge cases (disconnect, dimension change, etc.)
6. ✅ No performance impact or noticeable lag
7. ✅ All existing camera movements continue to work normally
8. ✅ Settings persist across sessions

---

*End of Implementation Plan*
