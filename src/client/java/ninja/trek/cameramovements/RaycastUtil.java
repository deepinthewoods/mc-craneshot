package ninja.trek.cameramovements;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Utility class for handling camera raycasting and collision detection
 */
public class RaycastUtil {
    private static final double CAMERA_OFFSET = 0.1; // Small offset to prevent z-fighting

    /**
     * Adjusts a target camera position based on raycast collision checks
     */
    public static Vec3d adjustForCollision(Vec3d playerPos, Vec3d targetPos, RaycastType raycastType) {
        if (raycastType == RaycastType.NONE) {
            return targetPos;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return targetPos;

        RaycastContext.FluidHandling fluidHandling = RaycastContext.FluidHandling.NONE;
        RaycastContext.ShapeType shapeType = RaycastContext.ShapeType.VISUAL;

        if (raycastType == RaycastType.NEAR) {
            // Cast from player to desired camera position
            BlockHitResult hit = client.world.raycast(new RaycastContext(
                    playerPos,
                    targetPos,
                    shapeType,
                    fluidHandling,
                    client.player
            ));

            if (hit.getType() == HitResult.Type.BLOCK) {
                // Move slightly in front of the hit position
                Vec3d hitPos = hit.getPos();
                Vec3d directionVector = hitPos.subtract(playerPos).normalize();
                return hitPos.subtract(directionVector.multiply(CAMERA_OFFSET));
            }
        } else if (raycastType == RaycastType.FAR) {
            // Cast from desired camera position back to player
            BlockHitResult hit = client.world.raycast(new RaycastContext(
                    targetPos,
                    playerPos,
                    shapeType,
                    fluidHandling,
                    client.player
            ));

            if (hit.getType() == HitResult.Type.BLOCK) {
                // Move slightly away from the hit position towards the camera direction
                Vec3d hitPos = hit.getPos();
                Vec3d directionVector = targetPos.subtract(playerPos).normalize();
                return hitPos.add(directionVector.multiply(CAMERA_OFFSET));
            }
        }

        return targetPos;
    }
}