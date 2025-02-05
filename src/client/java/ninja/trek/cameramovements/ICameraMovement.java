package ninja.trek.cameramovements;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;

public interface ICameraMovement {
    void start(MinecraftClient client, Camera camera);
    MovementState calculateState(MinecraftClient client, Camera camera);
    void reset(MinecraftClient client, Camera camera);
    void adjustDistance(boolean increase);
    String getName();
    float getWeight(); // For blending calculations
    boolean isComplete(); // To determine if movement should be removed
}

