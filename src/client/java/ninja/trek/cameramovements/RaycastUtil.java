package ninja.trek.cameramovements;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class RaycastUtil {
    private static final double CAMERA_OFFSET = 0.5;
    private static final double STEP_SIZE = 0.5;
    private static final double FINE_STEP_SIZE = 0.1;

    public static Vec3 adjustForCollision(Vec3 playerPos, Vec3 targetPos, RaycastType raycastType) {
        // Handle null inputs safely
        if (playerPos == null || targetPos == null) {
            return targetPos;
        }
        
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || raycastType == null || raycastType == RaycastType.NONE) {
            return targetPos;
        }

        switch (raycastType) {
            case NEAR:
                return handleNearRaycast(client, playerPos, targetPos);
            case FAR:
                return handleFarRaycast(client, playerPos, targetPos);
            default:
                return targetPos;
        }
    }

    private static Vec3 handleNearRaycast(Minecraft client, Vec3 playerPos, Vec3 targetPos) {
        BlockHitResult hit = client.level.clip(new ClipContext(
                playerPos,
                targetPos,
                ClipContext.Block.VISUAL,
                ClipContext.Fluid.NONE,
                client.player
        ));

        if (hit.getType() == HitResult.Type.BLOCK) {
            Vec3 hitPos = hit.getLocation();
            Vec3 directionVector = hitPos.subtract(playerPos).normalize();
            Vec3 adjusted = hitPos.subtract(directionVector.scale(CAMERA_OFFSET));
            // No logging here to reduce noise
            return adjusted;
        }
        return targetPos;
    }

    private static Vec3 handleFarRaycast(Minecraft client, Vec3 playerPos, Vec3 targetPos) {
        Vec3 direction = targetPos.subtract(playerPos).normalize();
        double totalDistance = targetPos.distanceTo(playerPos);

        // Start from target position
        Vec3 currentPos = targetPos;

        if (isPositionInAir(client, currentPos)) {
            return refinePosition(client, currentPos, direction);
        }

        // Coarse search
        for (double distance = STEP_SIZE; distance < totalDistance; distance += STEP_SIZE) {
            Vec3 checkPos = targetPos.subtract(direction.scale(distance));

            if (isPositionInAir(client, checkPos)) {
                return refinePosition(client, checkPos, direction.scale(-1));
            }
        }

        return playerPos;
    }

    private static Vec3 refinePosition(Minecraft client, Vec3 startPos, Vec3 direction) {
        // Raycast forward until we hit something
        BlockHitResult hit = client.level.clip(new ClipContext(
                startPos,
                startPos.add(direction.scale(2.0)), // Look 2 blocks ahead
                ClipContext.Block.VISUAL,
                ClipContext.Fluid.NONE,
                client.player
        ));

        if (hit.getType() == HitResult.Type.BLOCK) {
            Vec3 hitPos = hit.getLocation();
            return hitPos.subtract(direction.scale(CAMERA_OFFSET));
        }

        return startPos;
    }

    private static boolean isPositionInAir(Minecraft client, Vec3 pos) {
        // Perform null checks
        if (client == null || client.level == null || pos == null) {
            return true; // Assume air if we can't check
        }
        
        try {
            BlockPos blockPos = BlockPos.containing(pos);
            return client.level.getBlockState(blockPos).isAir();
        } catch (Exception e) {
            // Fallback in case of any error
            return true; // Assume air in case of error
        }
    }
}
