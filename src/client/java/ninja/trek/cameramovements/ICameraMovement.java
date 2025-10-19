package ninja.trek.cameramovements;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;

public interface ICameraMovement {
    void start(MinecraftClient client, Camera camera);
    /**
     * Calculate the movement state for this frame.
     * @param client Minecraft client
     * @param camera Active camera
     * @param deltaSeconds Seconds elapsed since last frame
     */
    MovementState calculateState(MinecraftClient client, Camera camera, float deltaSeconds);
    void queueReset(MinecraftClient client, Camera camera);
    void adjustDistance(boolean increase, MinecraftClient client);
    String getName();
    float getWeight(); // For blending calculations
    boolean isComplete(); // To determine if movement should be removed
    RaycastType getRaycastType();
    default boolean hasCompletedOutPhase() { return false; }
}

