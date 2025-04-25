package ninja.trek;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * Manages the orthographic camera mode state.
 * This class handles toggling between perspective and orthographic projection modes.
 */
public class OrthographicCameraManager {
    private static boolean orthographicMode = false;
    private static float orthoScale = 20.0f; // Controls the zoom level of the orthographic view
    private static final float MIN_SCALE = 1.0f;
    private static final float MAX_SCALE = 100.0f;
    private static final float SCALE_STEP = 1.0f;

    /**
     * Toggles the camera mode between perspective and orthographic.
     * @return The new state (true if orthographic mode is now enabled)
     */
    public static boolean toggleOrthographicMode() {
        orthographicMode = !orthographicMode;
        
        // Display a message to the player
        if (MinecraftClient.getInstance().player != null) {
            MinecraftClient.getInstance().player.sendMessage(
                Text.translatable("message.craneshot.orthographic." + (orthographicMode ? "enabled" : "disabled")),
                true
            );
        }
        
        return orthographicMode;
    }

    /**
     * Checks if orthographic mode is currently enabled.
     * @return true if orthographic mode is active, false otherwise
     */
    public static boolean isOrthographicMode() {
        return orthographicMode;
    }

    /**
     * Gets the current orthographic scale factor.
     * @return The scale value that determines the zoom level
     */
    public static float getOrthoScale() {
        return orthoScale;
    }

    /**
     * Sets the orthographic scale factor.
     * @param scale The new scale value
     */
    public static void setOrthoScale(float scale) {
        orthoScale = clampScale(scale);
    }

    /**
     * Adjusts the orthographic scale by the given amount.
     * Positive values zoom out (increase scale), negative values zoom in (decrease scale).
     * @param amount The amount to add to the current scale
     */
    public static void adjustOrthoScale(float amount) {
        orthoScale = clampScale(orthoScale + amount);
        
        // Display the current zoom level when changed
        if (MinecraftClient.getInstance().player != null && orthographicMode) {
            MinecraftClient.getInstance().player.sendMessage(
                Text.literal("Orthographic zoom: " + String.format("%.1f", orthoScale)),
                true
            );
        }
    }
    
    /**
     * Zoom in (decrease scale)
     */
    public static void zoomIn() {
        adjustOrthoScale(-SCALE_STEP);
    }
    
    /**
     * Zoom out (increase scale)
     */
    public static void zoomOut() {
        adjustOrthoScale(SCALE_STEP);
    }
    
    /**
     * Ensures the scale stays within the valid range
     * @param scale The scale to clamp
     * @return The clamped scale value
     */
    private static float clampScale(float scale) {
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
    }
}