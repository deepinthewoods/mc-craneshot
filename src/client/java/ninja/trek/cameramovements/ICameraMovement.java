package ninja.trek.cameramovements;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;

public interface ICameraMovement {
    void start(MinecraftClient client, Camera camera);
    boolean update(MinecraftClient client, Camera camera);
    void reset(MinecraftClient client, Camera camera);

    void adjustDistance(boolean increase);

    String getName();

}