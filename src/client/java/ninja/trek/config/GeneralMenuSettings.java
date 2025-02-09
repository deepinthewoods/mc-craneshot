package ninja.trek.config;

import ninja.trek.config.FreeCamSettings;

public class GeneralMenuSettings {
    private static boolean autoAdvance = false;
    private static final FreeCamSettings freeCamSettings = new FreeCamSettings();



    public static boolean isAutoAdvance() {
        return autoAdvance;
    }

    public static void setAutoAdvance(boolean value) {
        autoAdvance = value;
    }

    public static FreeCamSettings getFreeCamSettings() {
        return freeCamSettings;
    }
}