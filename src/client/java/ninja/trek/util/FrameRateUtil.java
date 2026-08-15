package ninja.trek.util;

public final class FrameRateUtil {
    private static final double TICKS_PER_SECOND = 20.0;

    private FrameRateUtil() {
    }

    public static double tickScale(float deltaSeconds) {
        if (!Float.isFinite(deltaSeconds) || deltaSeconds <= 0.0f) {
            return 0.0;
        }
        return deltaSeconds * TICKS_PER_SECOND;
    }

    /**
     * Converts a blend fraction tuned per client tick into an equivalent fraction
     * for the elapsed render-frame time.
     */
    public static double perTickBlend(double blend, float deltaSeconds) {
        double clampedBlend = Math.max(0.0, Math.min(1.0, blend));
        double elapsedTicks = tickScale(deltaSeconds);
        if (clampedBlend == 0.0 || elapsedTicks == 0.0) {
            return 0.0;
        }
        if (clampedBlend == 1.0) {
            return 1.0;
        }
        return 1.0 - Math.pow(1.0 - clampedBlend, elapsedTicks);
    }
}
