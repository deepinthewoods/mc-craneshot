package ninja.trek.cameramovements;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public class RaycastUtil {
    private static final double CAMERA_OFFSET = 0.5;
    private static final double STEP_SIZE = 0.5;
    private static final double FINE_STEP_SIZE = 0.1;

    public static Vec3d adjustForCollision(Vec3d playerPos, Vec3d targetPos, RaycastType raycastType) {
        // Handle null inputs safely
        if (playerPos == null || targetPos == null) {
            return targetPos;
        }
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || raycastType == null || raycastType == RaycastType.NONE) {
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

    private static Vec3d handleNearRaycast(MinecraftClient client, Vec3d playerPos, Vec3d targetPos) {
        BlockHitResult hit = client.world.raycast(new RaycastContext(
                playerPos,
                targetPos,
                RaycastContext.ShapeType.VISUAL,
                RaycastContext.FluidHandling.NONE,
                client.player
        ));

        if (hit.getType() == HitResult.Type.BLOCK) {
            Vec3d hitPos = hit.getPos();
            Vec3d directionVector = hitPos.subtract(playerPos).normalize();
            Vec3d adjusted = hitPos.subtract(directionVector.multiply(CAMERA_OFFSET));
            try {
                ninja.trek.Craneshot.LOGGER.info(
                    "RaycastUtil.NEAR: playerPos={} targetPos={} hitPos={} adjustedPos={}",
                    playerPos, targetPos, hitPos, adjusted
                );
            } catch (Throwable ignore) { }
            return adjusted;
        }
        return targetPos;
    }

    private static Vec3d handleFarRaycast(MinecraftClient client, Vec3d playerPos, Vec3d targetPos) {
        Vec3d direction = targetPos.subtract(playerPos).normalize();
        double totalDistance = targetPos.distanceTo(playerPos);

        // Start from target position
        Vec3d currentPos = targetPos;

        if (isPositionInAir(client, currentPos)) {
            Vec3d refined = refinePosition(client, currentPos, direction);
            try {
                ninja.trek.Craneshot.LOGGER.info(
                    "RaycastUtil.FAR startInAir: playerPos={} targetPos={} refinedPos={}",
                    playerPos, targetPos, refined
                );
            } catch (Throwable ignore) { }
            return refined;
        }

        // Coarse search
        for (double distance = STEP_SIZE; distance < totalDistance; distance += STEP_SIZE) {
            Vec3d checkPos = targetPos.subtract(direction.multiply(distance));

            if (isPositionInAir(client, checkPos)) {
                Vec3d refined = refinePosition(client, checkPos, direction.multiply(-1));
                try {
                    ninja.trek.Craneshot.LOGGER.info(
                        "RaycastUtil.FAR coarseRefine: playerPos={} targetPos={} candidate={} refinedPos={}",
                        playerPos, targetPos, checkPos, refined
                    );
                } catch (Throwable ignore) { }
                return refined;
            }
        }

        return playerPos;
    }

    private static Vec3d refinePosition(MinecraftClient client, Vec3d startPos, Vec3d direction) {
        // Raycast forward until we hit something
        BlockHitResult hit = client.world.raycast(new RaycastContext(
                startPos,
                startPos.add(direction.multiply(2.0)), // Look 2 blocks ahead
                RaycastContext.ShapeType.VISUAL,
                RaycastContext.FluidHandling.NONE,
                client.player
        ));

        if (hit.getType() == HitResult.Type.BLOCK) {
            Vec3d hitPos = hit.getPos();
            Vec3d adjusted = hitPos.subtract(direction.multiply(CAMERA_OFFSET));
            try {
                ninja.trek.Craneshot.LOGGER.info(
                    "RaycastUtil.refinePosition: startPos={} direction={} hitPos={} adjustedPos={}",
                    startPos, direction, hitPos, adjusted
                );
            } catch (Throwable ignore) { }
            return adjusted;
        }

        return startPos;
    }

    private static boolean isPositionInAir(MinecraftClient client, Vec3d pos) {
        // Perform null checks
        if (client == null || client.world == null || pos == null) {
            return true; // Assume air if we can't check
        }
        
        try {
            BlockPos blockPos = BlockPos.ofFloored(pos);
            return client.world.getBlockState(blockPos).isAir();
        } catch (Exception e) {
            // Fallback in case of any error
            return true; // Assume air in case of error
        }
    }
}
