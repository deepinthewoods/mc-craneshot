package ninja.trek.cameramovements;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;

public interface ICameraMovement {
    void start(MinecraftClient client, Camera camera);
    MovementState calculateState(MinecraftClient client, Camera camera);
    void queueReset(MinecraftClient client, Camera camera);
    void adjustDistance(boolean increase, MinecraftClient client);
    String getName();
    float getWeight(); // For blending calculations
    boolean isComplete(); // To determine if movement should be removed
    RaycastType getRaycastType();
    default boolean hasCompletedOutPhase() { return false; }
}

