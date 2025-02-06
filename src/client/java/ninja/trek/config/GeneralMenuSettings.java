package ninja.trek.config;

public class GeneralMenuSettings {
    private static TransitionMode currentTransitionMode = TransitionMode.IMMEDIATE;

    public static TransitionMode getCurrentTransitionMode() {
        return currentTransitionMode;
    }

    public static void setCurrentTransitionMode(TransitionMode mode) {
        currentTransitionMode = mode;
    }
}