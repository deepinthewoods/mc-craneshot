package ninja.trek.cameramovements.movements;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import ninja.trek.config.MovementSetting;
import ninja.trek.config.MovementSettingType;
import ninja.trek.Craneshot;

public class FollowMovement extends SpringLinearMovement {
    @MovementSetting(
            label = "Auto Run & Jump",
            type = MovementSettingType.BOOLEAN,
            description = "Forces forward+sprint and jumps early over full blocks and small gaps while Follow is active"
    )
    private boolean autoRunAndJump = false;

    // Predictive auto-jump lead: leadDistance = horizontalSpeed * LEAD_TIME + PAD, then clamped to [MIN_LEAD, MAX_LEAD].
    private static final double AUTO_JUMP_LEAD_TIME_SECONDS = 0.62; // How far ahead (in time) we anticipate collisions when computing lead distance.
    private static final double AUTO_JUMP_PAD_BLOCKS = 0.60;        // Fixed extra distance added to the lead distance to jump earlier (helps when speed reads low).
    private static final double AUTO_JUMP_MIN_LEAD_BLOCKS = 1.25;   // Minimum lead distance, even at low speed (prevents "jump too late" at near-zero velocity).
    private static final double AUTO_JUMP_MAX_LEAD_BLOCKS = 2.50;   // Maximum lead distance at high speed (prevents jumping absurdly early).

    // Key simulation timing.
    private static final int AUTO_JUMP_PRESS_TICKS = 1;    // How many client ticks we hold the jump key down per jump trigger.
    private static final int AUTO_JUMP_COOLDOWN_TICKS = 10; // Cooldown after a triggered jump before we can trigger another (reduces pogo / repeat triggers).

    // Gap jumping: treat it as a gap only if BOTH left+right "foot" samples have no ground, and then ground reappears within the scan window.
    private static final double AUTO_GAP_MIN_CHECK_BLOCKS = 0.75;  // Minimum distance ahead to start checking for missing ground (avoid jumping tiny dips).
    private static final double AUTO_GAP_MAX_SCAN_BLOCKS = 3.0;    // Furthest distance to search for ground reappearing (limits jumping into huge voids).
    private static final double AUTO_GAP_SCAN_STEP_BLOCKS = 0.5;   // Step size for scanning along the path for ground reappearing (smaller = more accurate, more checks).

    // Logging throttles.
    private static final long AUTO_JUMP_LOG_COOLDOWN_MS = 750L;           // Minimum time between "auto-jump" log lines.
    private static final long AUTO_ASSIST_STATUS_LOG_COOLDOWN_MS = 5000L; // Minimum time between "auto-run active" status log lines.

    private Boolean savedVanillaAutoJump = null;
    private boolean forcedForward = false;
    private boolean forcedSprint = false;
    private boolean forcedJump = false;
    private int jumpPressTicksRemaining = 0;
    private int jumpCooldownTicksRemaining = 0;
    private long lastAutoJumpLogTimeMs = 0L;
    private long lastAutoAssistStatusLogTimeMs = 0L;

    // Elytra takeoff: triggered by double-tapping the follow key.
    private static final int ELYTRA_SECOND_JUMP_DELAY_TICKS = 7;
    private int elytraTakeoffTicksRemaining = 0;

    public boolean isAutoRunAndJump() {
        return autoRunAndJump;
    }

    public void tickAutoRunAndJump(Minecraft client) {
        if (!autoRunAndJump) {
            stopAutoRunAndJump(client);
            return;
        }

        if (client == null || client.level == null || client.player == null) {
            stopAutoRunAndJump(client);
            return;
        }

        Player player = client.player;
        if (shouldSuppressAllAssist(player)) {
            stopForcedKeys(client);
            restoreVanillaAutoJump(client);
            return;
        }

        disableVanillaAutoJump(client);

        forceMoveKeys(client);
        tickJumpTimers(client);

        // --- Elytra takeoff: delayed second jump to activate gliding ---
        if (elytraTakeoffTicksRemaining > 0) {
            elytraTakeoffTicksRemaining--;
            if (elytraTakeoffTicksRemaining == 0) {
                triggerJump(client);
                Craneshot.LOGGER.info("Follow elytra takeoff: second jump fired");
            }
        }

        // --- Swimming assist: hold space when underwater, jump out at water edge ---
        if (player.isInWater()) {
            handleSwimmingAssist(client, player);
            return;
        }

        // Keep running while airborne, but only *decide* new jumps when grounded.
        if (!player.onGround()) {
            return;
        }

        if (jumpCooldownTicksRemaining > 0) {
            return;
        }

        Vec3 vel = player.getDeltaMovement();
        double horizontalSpeed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        double leadDistance = horizontalSpeed * AUTO_JUMP_LEAD_TIME_SECONDS + AUTO_JUMP_PAD_BLOCKS;
        leadDistance = Mth.clamp(leadDistance, AUTO_JUMP_MIN_LEAD_BLOCKS, AUTO_JUMP_MAX_LEAD_BLOCKS);

        Vec3 dir = Vec3.directionFromRotation(0.0f, player.getYRot()).normalize();
        if (dir.lengthSqr() < 1e-9) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastAutoAssistStatusLogTimeMs >= AUTO_ASSIST_STATUS_LOG_COOLDOWN_MS) {
            lastAutoAssistStatusLogTimeMs = now;
            Craneshot.LOGGER.info(
                    "Follow auto-run active: speed={} lead={} onGround={}",
                    format3(horizontalSpeed),
                    format3(leadDistance),
                    player.onGround()
            );
        }

        AutoJumpDecision decision = getAutoJumpDecision(client, player, dir, leadDistance);
        if (decision == null) {
            return;
        }

        triggerJump(client);
        if (now - lastAutoJumpLogTimeMs >= AUTO_JUMP_LOG_COOLDOWN_MS) {
            lastAutoJumpLogTimeMs = now;
            Craneshot.LOGGER.info(
                    "Follow auto-jump: reason={} speed={} lead={} dist={} pos={}",
                    decision.reason,
                    format3(horizontalSpeed),
                    format3(leadDistance),
                    format3(decision.distance),
                    decision.blockPos
            );
        }
    }

    /**
     * Initiates elytra takeoff: jumps immediately and schedules a second jump
     * after a short delay to activate gliding. Only works if the player has
     * an elytra equipped and is not already flying.
     */
    public void triggerElytraTakeoff(Minecraft client) {
        if (client == null || client.player == null) return;
        Player player = client.player;

        // Must have elytra equipped in chest slot
        if (!player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) return;

        // Already gliding — nothing to do
        if (player.isFallFlying()) return;

        // First jump to leave the ground
        triggerJump(client);
        // Schedule the second jump to activate elytra
        elytraTakeoffTicksRemaining = ELYTRA_SECOND_JUMP_DELAY_TICKS;

        Craneshot.LOGGER.info("Follow elytra takeoff: initiated");
    }

    public void stopAutoRunAndJump(Minecraft client) {
        stopForcedKeys(client);
        restoreVanillaAutoJump(client);
        jumpPressTicksRemaining = 0;
        jumpCooldownTicksRemaining = 0;
        forcedJump = false;
        elytraTakeoffTicksRemaining = 0;
        lastAutoJumpLogTimeMs = 0L;
        lastAutoAssistStatusLogTimeMs = 0L;
    }

    private void disableVanillaAutoJump(Minecraft client) {
        if (savedVanillaAutoJump == null) {
            try {
                savedVanillaAutoJump = client.options.autoJump().get();
            } catch (Throwable t) {
                savedVanillaAutoJump = null;
            }
        }
        try {
            client.options.autoJump().set(false);
        } catch (Throwable ignored) {
        }
    }

    private void restoreVanillaAutoJump(Minecraft client) {
        if (savedVanillaAutoJump == null || client == null) {
            savedVanillaAutoJump = null;
            return;
        }
        try {
            client.options.autoJump().set(savedVanillaAutoJump);
        } catch (Throwable ignored) {
        } finally {
            savedVanillaAutoJump = null;
        }
    }

    private void forceMoveKeys(Minecraft client) {
        client.options.keyUp.setDown(true);
        client.options.keySprint.setDown(true);
        forcedForward = true;
        forcedSprint = true;
    }

    private void stopForcedKeys(Minecraft client) {
        if (client == null) return;
        if (forcedForward) {
            client.options.keyUp.setDown(false);
            forcedForward = false;
        }
        if (forcedSprint) {
            client.options.keySprint.setDown(false);
            forcedSprint = false;
        }
        if (forcedJump) {
            client.options.keyJump.setDown(false);
            forcedJump = false;
        }
    }

    private void tickJumpTimers(Minecraft client) {
        if (jumpCooldownTicksRemaining > 0) {
            jumpCooldownTicksRemaining--;
        }

        if (jumpPressTicksRemaining > 0) {
            client.options.keyJump.setDown(true);
            forcedJump = true;
            jumpPressTicksRemaining--;
            if (jumpPressTicksRemaining == 0) {
                client.options.keyJump.setDown(false);
                forcedJump = false;
            }
        }
    }

    private void handleSwimmingAssist(Minecraft client, Player player) {
        // If underwater (not getting air at surface), hold space to swim up
        if (player.isUnderWater()) {
            client.options.keyJump.setDown(true);
            forcedJump = true;
            return;
        }

        // Player is at the water surface — check if near the edge to jump out
        Vec3 dir = Vec3.directionFromRotation(0.0f, player.getYRot()).normalize();
        if (dir.lengthSqr() < 1e-9) {
            releaseJumpIfForced(client);
            return;
        }

        // Scan ahead for solid ground (water edge)
        for (double d = 0.5; d <= 1.5; d += 0.5) {
            BlockPos checkPos = BlockPos.containing(
                    player.getX() + dir.x * d,
                    player.getY(),
                    player.getZ() + dir.z * d
            );

            // Solid block at player level means land ahead — jump out
            if (hasCollision(client, checkPos)) {
                client.options.keyJump.setDown(true);
                forcedJump = true;
                return;
            }

            // Non-fluid block with solid ground below means water edge — jump out
            BlockState stateAtLevel = client.level.getBlockState(checkPos);
            if (stateAtLevel.getFluidState().isEmpty() && hasCollision(client, checkPos.below())) {
                client.options.keyJump.setDown(true);
                forcedJump = true;
                return;
            }
        }

        // Not near edge — release jump
        releaseJumpIfForced(client);
    }

    private void releaseJumpIfForced(Minecraft client) {
        if (forcedJump) {
            client.options.keyJump.setDown(false);
            forcedJump = false;
        }
    }

    private void triggerJump(Minecraft client) {
        jumpPressTicksRemaining = AUTO_JUMP_PRESS_TICKS;
        jumpCooldownTicksRemaining = AUTO_JUMP_COOLDOWN_TICKS;
        client.options.keyJump.setDown(true);
        forcedJump = true;
    }

    private static boolean shouldSuppressAssist(Player player) {
        return shouldSuppressAllAssist(player);
    }

    private static boolean shouldSuppressAllAssist(Player player) {
        if (player == null) return true;
        return false;
    }

    private static boolean hasCollision(Minecraft client, BlockPos pos) {
        if (client == null || client.level == null || pos == null) return false;
        try {
            return !client.level.getBlockState(pos).getCollisionShape(client.level, pos).isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isJumpHeadroomClear(Minecraft client, BlockPos groundPos) {
        if (groundPos == null) return false;
        return !hasCollision(client, groundPos.above(1)) && !hasCollision(client, groundPos.above(2));
    }

    private static Vec3[] getForwardCornerOffsetsXZ(Vec3 dir, double extent) {
        if (dir == null || extent <= 1e-9) {
            return new Vec3[] { Vec3.ZERO };
        }

        double ax = Math.abs(dir.x);
        double az = Math.abs(dir.z);
        double eps = 1e-6;

        if (ax < eps && az < eps) {
            return new Vec3[] { Vec3.ZERO };
        }

        double sx = Math.signum(dir.x);
        double sz = Math.signum(dir.z);

        // If we're almost perfectly aligned with an axis, include both corners along the other axis
        // to avoid picking only one corner due to tiny floating-point components.
        if (ax < eps) {
            return new Vec3[] {
                    new Vec3(extent, 0.0, sz * extent),
                    new Vec3(-extent, 0.0, sz * extent)
            };
        }
        if (az < eps) {
            return new Vec3[] {
                    new Vec3(sx * extent, 0.0, extent),
                    new Vec3(sx * extent, 0.0, -extent)
            };
        }

        return new Vec3[] { new Vec3(sx * extent, 0.0, sz * extent) };
    }

    private static AutoJumpDecision getAutoJumpDecision(
            Minecraft client,
            Player player,
            Vec3 dir,
            double leadDistance
    ) {
        BlockPos groundPos = BlockPos.containing(player.getX(), player.getY() - 0.01, player.getZ());
        if (!isJumpHeadroomClear(client, groundPos)) {
            return null;
        }

        Vec3 right = new Vec3(-dir.z, 0.0, dir.x);
        double rightLen2 = right.lengthSqr();
        if (rightLen2 > 1e-9) {
            right = right.scale(1.0 / Math.sqrt(rightLen2));
        } else {
            right = Vec3.ZERO;
        }
        double sideOffset = Math.max(0.0, player.getBbWidth() * 0.5 - 0.05);
        Vec3 leftOffset = right.scale(-sideOffset);
        Vec3 rightOffset = right.scale(sideOffset);

        // 1) Full-block step up ahead (raycast from both sides of the player's body).
        Vec3 startBase = new Vec3(player.getX(), player.getY() + 0.2, player.getZ());
        Vec3 startLeft = startBase.add(leftOffset);
        Vec3 startRight = startBase.add(rightOffset);
        Vec3 endLeft = startLeft.add(dir.scale(leadDistance));
        Vec3 endRight = startRight.add(dir.scale(leadDistance));

        AutoJumpDecision bestStep = null;
        BlockHitResult hitLeft = client.level.clip(new ClipContext(
                startLeft,
                endLeft,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));
        bestStep = pickBestStepDecision(client, groundPos, startLeft, hitLeft, "left", bestStep);

        BlockHitResult hitRight = client.level.clip(new ClipContext(
                startRight,
                endRight,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));
        bestStep = pickBestStepDecision(client, groundPos, startRight, hitRight, "right", bestStep);

        // Sample from the forward-most corner(s) of the player's (axis-aligned) body. This fixes
        // "jump too late when running diagonally into a block corner" by accounting for the fact that
        // the leading point of the player's AABB is a corner when moving at an angle.
        double cornerInset = 0.05;
        double cornerExtent = Math.max(0.0, player.getBbWidth() * 0.5 - cornerInset);
        Vec3[] cornerOffsets = getForwardCornerOffsetsXZ(dir, cornerExtent);
        for (int i = 0; i < cornerOffsets.length; i++) {
            Vec3 rayStart = startBase.add(cornerOffsets[i]);
            Vec3 rayEnd = rayStart.add(dir.scale(leadDistance));
            BlockHitResult hit = client.level.clip(new ClipContext(
                    rayStart,
                    rayEnd,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    player
            ));
            bestStep = pickBestStepDecision(client, groundPos, rayStart, hit, "corner_" + i, bestStep);
        }

        if (bestStep != null) {
            return bestStep;
        }

        // 2) Gap ahead: jump only if we can see ground again within a short scan window.
        double checkDist = Math.max(AUTO_GAP_MIN_CHECK_BLOCKS, leadDistance);
        double x0 = player.getX();
        double z0 = player.getZ();
        BlockPos firstLeft = BlockPos.containing(
                x0 + leftOffset.x + dir.x * checkDist,
                player.getY() - 0.01,
                z0 + leftOffset.z + dir.z * checkDist
        );
        BlockPos firstRight = BlockPos.containing(
                x0 + rightOffset.x + dir.x * checkDist,
                player.getY() - 0.01,
                z0 + rightOffset.z + dir.z * checkDist
        );

        // Only treat it as a gap if BOTH sides have no ground at our level, and neither side is a step-up.
        if (hasCollision(client, firstLeft) || hasCollision(client, firstRight)) {
            return null;
        }
        if (hasCollision(client, firstLeft.above(1)) || hasCollision(client, firstRight.above(1))) {
            return null;
        }

        for (double d = checkDist + AUTO_GAP_SCAN_STEP_BLOCKS; d <= AUTO_GAP_MAX_SCAN_BLOCKS; d += AUTO_GAP_SCAN_STEP_BLOCKS) {
            BlockPos pLeft = BlockPos.containing(x0 + leftOffset.x + dir.x * d, player.getY() - 0.01, z0 + leftOffset.z + dir.z * d);
            BlockPos pRight = BlockPos.containing(x0 + rightOffset.x + dir.x * d, player.getY() - 0.01, z0 + rightOffset.z + dir.z * d);
            if (hasCollision(client, pLeft) && hasCollision(client, pRight)) {
                return new AutoJumpDecision("gap", d, BlockPos.containing(x0 + dir.x * d, player.getY() - 0.01, z0 + dir.z * d));
            }
        }

        return null;
    }

    private static AutoJumpDecision pickBestStepDecision(
            Minecraft client,
            BlockPos groundPos,
            Vec3 rayStart,
            BlockHitResult hit,
            String side,
            AutoJumpDecision best
    ) {
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            return best;
        }

        BlockPos hitPos = hit.getBlockPos();
        if (hitPos == null) {
            return best;
        }

        // For horizontal raycasts, check obstacle height at the same horizontal position
        // but starting from the player's ground level (handles partial blocks at any Y level)
        BlockPos checkPosAtGround = new BlockPos(hitPos.getX(), groundPos.getY(), hitPos.getZ());

        // Calculate the total height of obstacles from ground level
        // This handles partial blocks (slabs, hoppers, chests) and stacked blocks (slab on slab)
        double obstacleHeight = getObstacleHeightAboveGround(client, groundPos, checkPosAtGround);

        // Only jump if obstacle is taller than vanilla player step height (0.6 blocks)
        // This matches Minecraft's automatic step-up mechanic: players can walk up blocks ≤0.6 without jumping
        // Examples: slabs (0.5) = walk up, hoppers (0.625) = need jump, chests (0.875) = need jump
        if (obstacleHeight <= 0.6) {
            return best;
        }

        // Check that there's enough headroom to jump over the obstacle
        // We need 2 blocks of clearance above the top of the obstacle
        int obstacleTopBlockY = groundPos.getY() + (int) Math.ceil(obstacleHeight);
        BlockPos aboveObstacle1 = new BlockPos(hitPos.getX(), obstacleTopBlockY + 1, hitPos.getZ());
        BlockPos aboveObstacle2 = new BlockPos(hitPos.getX(), obstacleTopBlockY + 2, hitPos.getZ());

        if (hasCollision(client, aboveObstacle1) || hasCollision(client, aboveObstacle2)) {
            return best;
        }

        double hitDist = hit.getLocation().distanceTo(rayStart);
        String reason = String.format("step_%s_h%.2f", side, obstacleHeight);
        AutoJumpDecision candidate = new AutoJumpDecision(reason, hitDist, hitPos);

        if (best == null || hitDist < best.distance) {
            return candidate;
        }
        return best;
    }

    /**
     * Calculates the total height of obstacles above the ground position.
     * Scans upward from ground level to detect stacked blocks (e.g., slab on slab, chest on hopper).
     * Uses actual collision shape heights for accuracy with partial blocks.
     *
     * @param client Minecraft client
     * @param groundPos Player's current ground position
     * @param checkPos Position to check for obstacles (at ground Y level)
     * @return Height in blocks above ground level, or 0.0 if no obstacle
     */
    private static double getObstacleHeightAboveGround(
            Minecraft client,
            BlockPos groundPos,
            BlockPos checkPos) {
        if (client == null || client.level == null || groundPos == null || checkPos == null) {
            return 0.0;
        }

        double groundY = groundPos.getY();
        double maxObstacleY = groundY;

        // Scan upward from ground level to 3 blocks high (handles stacked blocks like slab+slab+slab)
        for (int dy = 1; dy <= 3; dy++) {
            BlockPos scanPos = new BlockPos(checkPos.getX(), groundPos.getY() + dy, checkPos.getZ());
            BlockState state = client.level.getBlockState(scanPos);

            if (state.isAir()) {
                // No obstacle at this height, continue checking above in case of floating blocks
                continue;
            }

            try {
                VoxelShape collisionShape = state.getCollisionShape(client.level, scanPos);

                if (collisionShape.isEmpty()) {
                    // Block exists but has no collision (e.g., torch, flower, wheat)
                    continue;
                }

                // Get the maximum Y value of the collision shape (top surface)
                double shapeMaxY = collisionShape.max(Direction.Axis.Y);

                // Collision shapes are relative to block position, so add block's Y coordinate
                double absoluteMaxY = scanPos.getY() + shapeMaxY;

                // Update the maximum obstacle height found
                if (absoluteMaxY > maxObstacleY) {
                    maxObstacleY = absoluteMaxY;
                }

                // If this block is a full cube or reaches block ceiling, stop scanning up
                // (no point checking above a solid block)
                if (shapeMaxY >= 0.99) {
                    break;
                }
            } catch (Throwable t) {
                // If we can't get collision shape, assume no obstacle and continue
                continue;
            }
        }

        // Return height difference from ground level
        return maxObstacleY - groundY;
    }

    private static String format3(double v) {
        return String.format(java.util.Locale.ROOT, "%.3f", v);
    }

    private static final class AutoJumpDecision {
        private final String reason;
        private final double distance;
        private final BlockPos blockPos;

        private AutoJumpDecision(String reason, double distance, BlockPos blockPos) {
            this.reason = reason;
            this.distance = distance;
            this.blockPos = blockPos;
        }
    }

    @Override
    public void start(Minecraft client, Camera camera) {
        super.start(client, camera);

        // Reset assist runtime state (setting persists)
        jumpPressTicksRemaining = 0;
        jumpCooldownTicksRemaining = 0;
        forcedJump = false;
        forcedForward = false;
        forcedSprint = false;
        savedVanillaAutoJump = null;
        lastAutoJumpLogTimeMs = 0L;
        lastAutoAssistStatusLogTimeMs = 0L;
    }

    @Override
    public String getName() {
        return "Follow";
    }
}
