package ninja.trek.cameramovements;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;

public interface ICameraMovement {
    void startTransition(MinecraftClient client, Camera camera);
    boolean updateTransition(MinecraftClient client, Camera camera);
    void reset(MinecraftClient client, Camera camera);

    void adjustDistance(boolean increase);

    String getName();

}