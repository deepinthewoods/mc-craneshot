package ninja.trek.cameramovements;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;

public interface ICameraMovement {
    void start(Minecraft client, Camera camera);
    /**
     * Calculate the movement state for this frame.
     * @param client Minecraft client
     * @param camera Active camera
     * @param tickDelta Partial tick used to interpolate tracked entity poses
     * @param deltaSeconds Seconds elapsed since last frame
     */
    MovementState calculateState(Minecraft client, Camera camera, float tickDelta, float deltaSeconds);
    void queueReset(Minecraft client, Camera camera);
    void adjustDistance(boolean increase, Minecraft client);
    String getName();
    float getWeight(); // For blending calculations
    boolean isComplete(); // To determine if movement should be removed
    RaycastType getRaycastType();
    default boolean hasCompletedOutPhase() { return false; }
}

