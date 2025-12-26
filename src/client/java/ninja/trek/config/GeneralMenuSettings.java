package ninja.trek.config;

import ninja.trek.cameramovements.movements.FreeCamReturnMovement;
import ninja.trek.cameramovements.movements.FollowMovement;

public class GeneralMenuSettings {
    private static boolean autoAdvance = false;
    private static final FreeCamSettings freeCamSettings = new FreeCamSettings();
    private static final FreeCamReturnMovement freeCamReturnMovement = new FreeCamReturnMovement();
    // Follow movement (keybind-only)
    private static final FollowMovement followMovement = new FollowMovement();
    // Zoom movement (keybind-only)
    private static final ninja.trek.cameramovements.movements.ZoomMovement zoomMovement =
            new ninja.trek.cameramovements.movements.ZoomMovement();
    // Default idle movement control
    private static boolean useDefaultIdleMovement = false;
    private static final ninja.trek.cameramovements.movements.LinearMovement defaultIdleMovement =
            new ninja.trek.cameramovements.movements.LinearMovement();
    // Node Editor drag sensitivity multiplier (applies in Node Editor only)
    private static double nodeEditSensitivityMultiplier = 6.0; // default: 6x

    // Crosshair settings
    private static boolean showVanillaCrosshair = true;
    private static boolean showCameraCrosshair = true;
    // Size interpreted as half-length in pixels for cross arms or half-side for square
    private static int cameraCrosshairSize = 3;
    private static boolean cameraCrosshairSquare = false;
    // Node overlay visibility outside edit mode
    private static boolean showNodesOutsideEdit = false;

    // Spectator target player following settings
    private static String targetPlayerName = "";  // Empty = use local player
    private static boolean spectatorFollowEnabled = true;  // Master toggle

    // Minimum speed enforcement settings
    private static boolean enforceMinimumSpeed = false;
    private static double minimumSpeedMultiplier = 1.5;  // Default: 1.5x player speed

    public static boolean isAutoAdvance() {
        return autoAdvance;
    }

    public static void setAutoAdvance(boolean value) {
        autoAdvance = value;
    }

    public static FreeCamSettings getFreeCamSettings() {
        return freeCamSettings;
    }
    
    public static FreeCamReturnMovement getFreeCamReturnMovement() {
        return freeCamReturnMovement;
    }

    public static FollowMovement getFollowMovement() {
        return followMovement;
    }

    public static ninja.trek.cameramovements.movements.ZoomMovement getZoomMovement() {
        return zoomMovement;
    }

    // Default idle movement settings
    public static boolean isUseDefaultIdleMovement() {
        return useDefaultIdleMovement;
    }

    public static void setUseDefaultIdleMovement(boolean value) {
        useDefaultIdleMovement = value;
    }

    public static ninja.trek.cameramovements.movements.LinearMovement getDefaultIdleMovement() {
        return defaultIdleMovement;
    }

    // Node Editor sensitivity controls
    public static double getNodeEditSensitivityMultiplier() {
        return nodeEditSensitivityMultiplier;
    }

    public static void setNodeEditSensitivityMultiplier(double value) {
        // Clamp to reasonable range to avoid extreme values
        nodeEditSensitivityMultiplier = Math.max(0.1, Math.min(20.0, value));
    }

    // Crosshair settings accessors
    public static boolean isShowVanillaCrosshair() { return showVanillaCrosshair; }
    public static void setShowVanillaCrosshair(boolean value) { showVanillaCrosshair = value; }

    public static boolean isShowCameraCrosshair() { return showCameraCrosshair; }
    public static void setShowCameraCrosshair(boolean value) { showCameraCrosshair = value; }

    public static int getCameraCrosshairSize() { return cameraCrosshairSize; }
    public static void setCameraCrosshairSize(int value) {
        cameraCrosshairSize = Math.max(1, Math.min(20, value));
    }

    public static boolean isCameraCrosshairSquare() { return cameraCrosshairSquare; }
    public static void setCameraCrosshairSquare(boolean value) { cameraCrosshairSquare = value; }

    // Node overlay
    public static boolean isShowNodesOutsideEdit() { return showNodesOutsideEdit; }
    public static void setShowNodesOutsideEdit(boolean value) { showNodesOutsideEdit = value; }

    // Spectator target player settings
    public static String getTargetPlayerName() { return targetPlayerName; }
    public static void setTargetPlayerName(String name) {
        targetPlayerName = (name == null) ? "" : name.trim();
    }

    public static boolean isSpectatorFollowEnabled() { return spectatorFollowEnabled; }
    public static void setSpectatorFollowEnabled(boolean enabled) {
        spectatorFollowEnabled = enabled;
    }

    // Minimum speed enforcement settings
    public static boolean isEnforceMinimumSpeed() { return enforceMinimumSpeed; }
    public static void setEnforceMinimumSpeed(boolean value) { enforceMinimumSpeed = value; }

    public static double getMinimumSpeedMultiplier() { return minimumSpeedMultiplier; }
    public static void setMinimumSpeedMultiplier(double value) {
        // Clamp to range 1.0x - 3.0x
        minimumSpeedMultiplier = Math.max(1.0, Math.min(3.0, value));
    }
}
