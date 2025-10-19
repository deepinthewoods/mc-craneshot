package ninja.trek.config;

import ninja.trek.cameramovements.movements.FreeCamReturnMovement;

public class GeneralMenuSettings {
    private static boolean autoAdvance = false;
    private static final FreeCamSettings freeCamSettings = new FreeCamSettings();
    private static final FreeCamReturnMovement freeCamReturnMovement = new FreeCamReturnMovement();
    // Default idle movement control
    private static boolean useDefaultIdleMovement = false;
    private static final ninja.trek.cameramovements.movements.LinearMovement defaultIdleMovement =
            new ninja.trek.cameramovements.movements.LinearMovement();
    // Node Editor drag sensitivity multiplier (applies in Node Editor only)
    private static double nodeEditSensitivityMultiplier = 6.0; // default: 6x


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
}
