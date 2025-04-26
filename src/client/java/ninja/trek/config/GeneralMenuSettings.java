package ninja.trek.config;

import ninja.trek.cameramovements.movements.FreeCamReturnMovement;

public class GeneralMenuSettings {
    private static boolean autoAdvance = false;
    private static final FreeCamSettings freeCamSettings = new FreeCamSettings();
    private static final FreeCamReturnMovement freeCamReturnMovement = new FreeCamReturnMovement();


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
}