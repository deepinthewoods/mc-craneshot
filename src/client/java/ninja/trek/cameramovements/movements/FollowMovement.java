package ninja.trek.cameramovements.movements;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CarpetBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import ninja.trek.CameraController;
import ninja.trek.cameramovements.AbstractMovementSettings;
import ninja.trek.cameramovements.CameraTarget;
import ninja.trek.cameramovements.ICameraMovement;
import ninja.trek.cameramovements.MovementState;
import ninja.trek.config.MovementSetting;
import ninja.trek.config.MovementSettingType;
import ninja.trek.Craneshot;
import ninja.trek.mixin.client.FovAccessor;

public class FollowMovement extends AbstractMovementSettings implements ICameraMovement {
    @MovementSetting(label = "Follow Height", min = 0.0, max = 50.0)
    private double followHeight = 8.0;

    @MovementSetting(label = "XZ Threshold", min = 0.0, max = 50.0)
    private double xzThreshold = 2.0;

    @MovementSetting(label = "Y Threshold", min = 0.0, max = 50.0)
    private double yThreshold = 2.0;

    @MovementSetting(label = "Position Easing XZ", min = 0.01, max = 1.0)
    private double positionEasingXZ = 0.1;

    @MovementSetting(label = "Position Speed Limit XZ", min = 0.1, max = 200.0)
    private double positionSpeedLimitXZ = 10.0;

    @MovementSetting(label = "Position Easing Y", min = 0.01, max = 1.0)
    private double positionEasingY = 0.1;

    @MovementSetting(label = "Position Speed Limit Y", min = 0.1, max = 200.0)
    private double positionSpeedLimitY = 10.0;

    @MovementSetting(label = "Return Position Easing Y", min = 0.01, max = 1.0)
    private double returnPositionEasingY = 0.1;

    @MovementSetting(label = "Return Position Speed Limit Y", min = 0.1, max = 200.0)
    private double returnPositionSpeedLimitY = 10.0;

    @MovementSetting(label = "Rotation Easing", min = 0.01, max = 1.0)
    private double rotationEasing = 0.1;

    @MovementSetting(label = "Rotation Speed Limit", min = 0.1, max = 1000.0)
    private double rotationSpeedLimit = 500.0;

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

    private CameraTarget current = new CameraTarget();
    private float lastStickYaw = 0.0f;
    private Vec3d startPlayerPosXZ = null;
    private boolean clampArmed = false;
    private Vec3d orbitTargetXZ = null;
    private boolean resetting = false;

    private boolean finalInterpActive = false;
    private double finalInterpT = 0.0;
    private Vec3d finalInterpStart = null;

    public boolean isAutoRunAndJump() {
        return autoRunAndJump;
    }

    @Override
    public void updateSetting(String key, Object value) {
        if ("positionEasing".equals(key)) {
            double parsed = parseDouble(value, 0.1);
            positionEasingXZ = parsed;
            positionEasingY = parsed;
            return;
        }
        if ("positionSpeedLimit".equals(key)) {
            double parsed = parseDouble(value, 10.0);
            positionSpeedLimitXZ = parsed;
            positionSpeedLimitY = parsed;
            return;
        }
        super.updateSetting(key, value);
    }

    private static double parseDouble(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Double.parseDouble(stringValue);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    public void tickAutoRunAndJump(MinecraftClient client) {
        if (!autoRunAndJump) {
            stopAutoRunAndJump(client);
            return;
        }

        if (client == null || client.world == null || client.player == null) {
            stopAutoRunAndJump(client);
            return;
        }

        PlayerEntity player = client.player;
        if (shouldSuppressAllAssist(player)) {
            stopForcedKeys(client);
            restoreVanillaAutoJump(client);
            return;
        }

        disableVanillaAutoJump(client);

        forceMoveKeys(client);
        tickJumpTimers(client);

        // Keep running while airborne, but only *decide* new jumps when grounded.
        if (!player.isOnGround()) {
            return;
        }

        if (jumpCooldownTicksRemaining > 0) {
            return;
        }

        Vec3d vel = player.getVelocity();
        double horizontalSpeed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        double leadDistance = horizontalSpeed * AUTO_JUMP_LEAD_TIME_SECONDS + AUTO_JUMP_PAD_BLOCKS;
        leadDistance = MathHelper.clamp(leadDistance, AUTO_JUMP_MIN_LEAD_BLOCKS, AUTO_JUMP_MAX_LEAD_BLOCKS);

        Vec3d dir = Vec3d.fromPolar(0.0f, player.getYaw()).normalize();
        if (dir.lengthSquared() < 1e-9) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastAutoAssistStatusLogTimeMs >= AUTO_ASSIST_STATUS_LOG_COOLDOWN_MS) {
            lastAutoAssistStatusLogTimeMs = now;
            Craneshot.LOGGER.info(
                    "Follow auto-run active: speed={} lead={} onGround={}",
                    format3(horizontalSpeed),
                    format3(leadDistance),
                    player.isOnGround()
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

    public void stopAutoRunAndJump(MinecraftClient client) {
        stopForcedKeys(client);
        restoreVanillaAutoJump(client);
        jumpPressTicksRemaining = 0;
        jumpCooldownTicksRemaining = 0;
        forcedJump = false;
        lastAutoJumpLogTimeMs = 0L;
        lastAutoAssistStatusLogTimeMs = 0L;
    }

    private void disableVanillaAutoJump(MinecraftClient client) {
        if (savedVanillaAutoJump == null) {
            try {
                savedVanillaAutoJump = client.options.getAutoJump().getValue();
            } catch (Throwable t) {
                savedVanillaAutoJump = null;
            }
        }
        try {
            client.options.getAutoJump().setValue(false);
        } catch (Throwable ignored) {
        }
    }

    private void restoreVanillaAutoJump(MinecraftClient client) {
        if (savedVanillaAutoJump == null || client == null) {
            savedVanillaAutoJump = null;
            return;
        }
        try {
            client.options.getAutoJump().setValue(savedVanillaAutoJump);
        } catch (Throwable ignored) {
        } finally {
            savedVanillaAutoJump = null;
        }
    }

    private void forceMoveKeys(MinecraftClient client) {
        client.options.forwardKey.setPressed(true);
        client.options.sprintKey.setPressed(true);
        forcedForward = true;
        forcedSprint = true;
    }

    private void stopForcedKeys(MinecraftClient client) {
        if (client == null) return;
        if (forcedForward) {
            client.options.forwardKey.setPressed(false);
            forcedForward = false;
        }
        if (forcedSprint) {
            client.options.sprintKey.setPressed(false);
            forcedSprint = false;
        }
        if (forcedJump) {
            client.options.jumpKey.setPressed(false);
            forcedJump = false;
        }
    }

    private void tickJumpTimers(MinecraftClient client) {
        if (jumpCooldownTicksRemaining > 0) {
            jumpCooldownTicksRemaining--;
        }

        if (jumpPressTicksRemaining > 0) {
            client.options.jumpKey.setPressed(true);
            forcedJump = true;
            jumpPressTicksRemaining--;
            if (jumpPressTicksRemaining == 0) {
                client.options.jumpKey.setPressed(false);
                forcedJump = false;
            }
        }
    }

    private void triggerJump(MinecraftClient client) {
        jumpPressTicksRemaining = AUTO_JUMP_PRESS_TICKS;
        jumpCooldownTicksRemaining = AUTO_JUMP_COOLDOWN_TICKS;
        client.options.jumpKey.setPressed(true);
        forcedJump = true;
    }

    private static boolean shouldSuppressAssist(PlayerEntity player) {
        return shouldSuppressAllAssist(player);
    }

    private static boolean shouldSuppressAllAssist(PlayerEntity player) {
        if (player == null) return true;
        return false;
    }

    private static boolean hasCollision(MinecraftClient client, BlockPos pos) {
        if (client == null || client.world == null || pos == null) return false;
        try {
            return !client.world.getBlockState(pos).getCollisionShape(client.world, pos).isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isJumpHeadroomClear(MinecraftClient client, BlockPos groundPos) {
        if (groundPos == null) return false;
        return !hasCollision(client, groundPos.up(1)) && !hasCollision(client, groundPos.up(2));
    }

    private static Vec3d[] getForwardCornerOffsetsXZ(Vec3d dir, double extent) {
        if (dir == null || extent <= 1e-9) {
            return new Vec3d[] { Vec3d.ZERO };
        }

        double ax = Math.abs(dir.x);
        double az = Math.abs(dir.z);
        double eps = 1e-6;

        if (ax < eps && az < eps) {
            return new Vec3d[] { Vec3d.ZERO };
        }

        double sx = Math.signum(dir.x);
        double sz = Math.signum(dir.z);

        // If we're almost perfectly aligned with an axis, include both corners along the other axis
        // to avoid picking only one corner due to tiny floating-point components.
        if (ax < eps) {
            return new Vec3d[] {
                    new Vec3d(extent, 0.0, sz * extent),
                    new Vec3d(-extent, 0.0, sz * extent)
            };
        }
        if (az < eps) {
            return new Vec3d[] {
                    new Vec3d(sx * extent, 0.0, extent),
                    new Vec3d(sx * extent, 0.0, -extent)
            };
        }

        return new Vec3d[] { new Vec3d(sx * extent, 0.0, sz * extent) };
    }

    private static AutoJumpDecision getAutoJumpDecision(
            MinecraftClient client,
            PlayerEntity player,
            Vec3d dir,
            double leadDistance
    ) {
        BlockPos groundPos = BlockPos.ofFloored(player.getX(), player.getY() - 0.01, player.getZ());
        if (!isJumpHeadroomClear(client, groundPos)) {
            return null;
        }

        Vec3d right = new Vec3d(-dir.z, 0.0, dir.x);
        double rightLen2 = right.lengthSquared();
        if (rightLen2 > 1e-9) {
            right = right.multiply(1.0 / Math.sqrt(rightLen2));
        } else {
            right = Vec3d.ZERO;
        }
        double sideOffset = Math.max(0.0, player.getWidth() * 0.5 - 0.05);
        Vec3d leftOffset = right.multiply(-sideOffset);
        Vec3d rightOffset = right.multiply(sideOffset);

        // 1) Full-block step up ahead (raycast from both sides of the player's body).
        Vec3d startBase = new Vec3d(player.getX(), player.getY() + 0.2, player.getZ());
        Vec3d startLeft = startBase.add(leftOffset);
        Vec3d startRight = startBase.add(rightOffset);
        Vec3d endLeft = startLeft.add(dir.multiply(leadDistance));
        Vec3d endRight = startRight.add(dir.multiply(leadDistance));

        AutoJumpDecision bestStep = null;
        BlockHitResult hitLeft = client.world.raycast(new RaycastContext(
                startLeft,
                endLeft,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));
        bestStep = pickBestStepDecision(client, groundPos, startLeft, hitLeft, "left", bestStep);

        BlockHitResult hitRight = client.world.raycast(new RaycastContext(
                startRight,
                endRight,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));
        bestStep = pickBestStepDecision(client, groundPos, startRight, hitRight, "right", bestStep);

        // Sample from the forward-most corner(s) of the player's (axis-aligned) body. This fixes
        // "jump too late when running diagonally into a block corner" by accounting for the fact that
        // the leading point of the player's AABB is a corner when moving at an angle.
        double cornerInset = 0.05;
        double cornerExtent = Math.max(0.0, player.getWidth() * 0.5 - cornerInset);
        Vec3d[] cornerOffsets = getForwardCornerOffsetsXZ(dir, cornerExtent);
        for (int i = 0; i < cornerOffsets.length; i++) {
            Vec3d rayStart = startBase.add(cornerOffsets[i]);
            Vec3d rayEnd = rayStart.add(dir.multiply(leadDistance));
            BlockHitResult hit = client.world.raycast(new RaycastContext(
                    rayStart,
                    rayEnd,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
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
        BlockPos firstLeft = BlockPos.ofFloored(
                x0 + leftOffset.x + dir.x * checkDist,
                player.getY() - 0.01,
                z0 + leftOffset.z + dir.z * checkDist
        );
        BlockPos firstRight = BlockPos.ofFloored(
                x0 + rightOffset.x + dir.x * checkDist,
                player.getY() - 0.01,
                z0 + rightOffset.z + dir.z * checkDist
        );

        // Only treat it as a gap if BOTH sides have no ground at our level, and neither side is a step-up.
        if (hasCollision(client, firstLeft) || hasCollision(client, firstRight)) {
            return null;
        }
        if (hasCollision(client, firstLeft.up(1)) || hasCollision(client, firstRight.up(1))) {
            return null;
        }

        for (double d = checkDist + AUTO_GAP_SCAN_STEP_BLOCKS; d <= AUTO_GAP_MAX_SCAN_BLOCKS; d += AUTO_GAP_SCAN_STEP_BLOCKS) {
            BlockPos pLeft = BlockPos.ofFloored(x0 + leftOffset.x + dir.x * d, player.getY() - 0.01, z0 + leftOffset.z + dir.z * d);
            BlockPos pRight = BlockPos.ofFloored(x0 + rightOffset.x + dir.x * d, player.getY() - 0.01, z0 + rightOffset.z + dir.z * d);
            if (hasCollision(client, pLeft) && hasCollision(client, pRight)) {
                return new AutoJumpDecision("gap", d, BlockPos.ofFloored(x0 + dir.x * d, player.getY() - 0.01, z0 + dir.z * d));
            }
        }

        return null;
    }

    private static AutoJumpDecision pickBestStepDecision(
            MinecraftClient client,
            BlockPos groundPos,
            Vec3d rayStart,
            BlockHitResult hit,
            String side,
            AutoJumpDecision best
    ) {
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            return best;
        }

        BlockPos hitPos = hit.getBlockPos();
        if (hitPos == null || hitPos.getY() != groundPos.getY() + 1) {
            return best;
        }

        try {
            BlockState hitState = client.world.getBlockState(hitPos);
            if (!isAutoJumpStepBlock(client, hitPos, hitState)) {
                return best;
            }
        } catch (Throwable t) {
            return best;
        }

        if (hasCollision(client, hitPos.up(1)) || hasCollision(client, hitPos.up(2))) {
            return best;
        }

        double hitDist = hit.getPos().distanceTo(rayStart);
        AutoJumpDecision candidate = new AutoJumpDecision("full_block_step_" + side, hitDist, hitPos);
        if (best == null || hitDist < best.distance) {
            return candidate;
        }
        return best;
    }

    private static boolean isAutoJumpStepBlock(MinecraftClient client, BlockPos pos, BlockState state) {
        if (client == null || client.world == null || state == null || pos == null) {
            return false;
        }
        if (state.isOf(Blocks.DIRT_PATH)) {
            return true;
        }
        if (state.getBlock() instanceof CarpetBlock) {
            return true;
        }
        return state.isFullCube(client.world, pos);
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
    public void start(MinecraftClient client, Camera camera) {
        current = CameraTarget.fromCamera(camera);
        lastStickYaw = CameraController.controlStick.getYaw();
        Vec3d stickPos = CameraController.controlStick.getPosition();
        startPlayerPosXZ = new Vec3d(stickPos.x, 0.0, stickPos.z);
        orbitTargetXZ = new Vec3d(current.getPosition().x, 0.0, current.getPosition().z);
        clampArmed = false;
        resetting = false;
        alpha = 1.0;

        finalInterpActive = false;
        finalInterpT = 0.0;
        finalInterpStart = null;

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
    public MovementState calculateState(MinecraftClient client, Camera camera, float deltaSeconds) {
        if (client.player == null) return new MovementState(current, true);

        Vec3d stickPos = CameraController.controlStick.getPosition();
        float stickYaw = CameraController.controlStick.getYaw();
        float stickPitch = (float) (CameraController.controlStick.getPitch() + pitchOffset);

        Vec3d desiredPos;
        float targetYaw;
        float targetPitch;
        float targetFovDelta;

        if (resetting) {
            Vec3d playerPos = client.player.getEyePos();
            targetYaw = client.player.getYaw();
            targetPitch = (float) (client.player.getPitch() + pitchOffset);
            targetFovDelta = 1.0f;

            double remainingDistanceForFinal = current.getPosition().distanceTo(playerPos);
            if (!finalInterpActive && remainingDistanceForFinal <= FINAL_INTERP_DISTANCE_THRESHOLD) {
                finalInterpActive = true;
                finalInterpT = 0.0;
                finalInterpStart = current.getPosition();
            }

            if (finalInterpActive) {
                double step = deltaSeconds / FINAL_INTERP_TIME_SECONDS;
                finalInterpT = Math.min(1.0, finalInterpT + step);
                desiredPos = finalInterpStart.lerp(playerPos, finalInterpT);
            } else {
                desiredPos = easedStep(current.getPosition(), playerPos, deltaSeconds, returnPositionEasingY, returnPositionSpeedLimitY);
            }
        } else {
            targetYaw = stickYaw;
            targetPitch = stickPitch;
            targetFovDelta = fovMultiplier;

            Vec3d cur = current.getPosition();
            Vec3d playerXZ = new Vec3d(stickPos.x, 0.0, stickPos.z);

            float deltaYaw = stickYaw - lastStickYaw;
            while (deltaYaw > 180f) deltaYaw -= 360f;
            while (deltaYaw < -180f) deltaYaw += 360f;
            lastStickYaw = stickYaw;

            if (orbitTargetXZ == null) {
                orbitTargetXZ = new Vec3d(cur.x, 0.0, cur.z);
            }
            orbitTargetXZ = rotateAroundY(playerXZ, orbitTargetXZ, deltaYaw);

            if (!clampArmed && startPlayerPosXZ != null) {
                double moved = horizontalDistanceXZ(playerXZ, startPlayerPosXZ);
                if (moved > 0.01) {
                    clampArmed = true;
                }
            }

            if (clampArmed) {
                orbitTargetXZ = clampDistanceXZ(playerXZ, orbitTargetXZ, xzThreshold);
            }
            Vec3d desiredCamXZ = orbitTargetXZ;
            double desiredY = computeFollowY(stickPos.y, cur.y, followHeight, yThreshold, client.player.isOnGround());

            Vec3d desiredRaw = new Vec3d(desiredCamXZ.x, desiredY, desiredCamXZ.z);
            desiredPos = easedStep(cur, desiredRaw, deltaSeconds);
        }

        float newYaw = easedAngle(current.getYaw(), targetYaw, deltaSeconds);
        float newPitch = easedAngle(current.getPitch(), targetPitch, deltaSeconds);
        float newFovDelta = easedFov(current.getFovMultiplier(), targetFovDelta, deltaSeconds);

        current = new CameraTarget(desiredPos, newYaw, newPitch, newFovDelta);

        if (client.gameRenderer instanceof FovAccessor) {
            ((FovAccessor) client.gameRenderer).setFovModifier(current.getFovMultiplier());
        }

        boolean complete = resetting && isComplete();
        return new MovementState(current, complete);
    }

    private Vec3d rotateAroundY(Vec3d centerXZ, Vec3d pointXZ, float deltaYawDegrees) {
        if (Math.abs(deltaYawDegrees) < 1e-6f) return pointXZ;
        double theta = Math.toRadians(deltaYawDegrees);
        double cos = Math.cos(theta);
        double sin = Math.sin(theta);

        double ox = pointXZ.x - centerXZ.x;
        double oz = pointXZ.z - centerXZ.z;

        double rx = ox * cos - oz * sin;
        double rz = ox * sin + oz * cos;

        return new Vec3d(centerXZ.x + rx, 0.0, centerXZ.z + rz);
    }

    private Vec3d clampDistanceXZ(Vec3d playerXZ, Vec3d cameraXZ, double threshold) {
        Vec3d delta = new Vec3d(cameraXZ.x - playerXZ.x, 0.0, cameraXZ.z - playerXZ.z);
        double dist = delta.length();
        if (dist <= threshold || dist <= 1e-9) return cameraXZ;
        Vec3d dir = delta.multiply(1.0 / dist);
        return new Vec3d(playerXZ.x, 0.0, playerXZ.z).add(dir.multiply(threshold));
    }

    private double horizontalDistanceXZ(Vec3d a, Vec3d b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private double computeFollowY(double centerY, double currentY, double height, double threshold, boolean onGround) {
        double targetY = centerY + height;
        if (onGround) {
            return targetY;
        }
        if (Math.abs(targetY - currentY) > threshold) {
            return targetY;
        }
        return currentY;
    }

    private Vec3d easedStep(Vec3d currentPos, Vec3d targetPos, float deltaSeconds) {
        return easedStep(currentPos, targetPos, deltaSeconds, positionEasingY, positionSpeedLimitY);
    }

    private Vec3d easedStep(Vec3d currentPos, Vec3d targetPos, float deltaSeconds, double easingY, double speedLimitY) {
        Vec3d delta = targetPos.subtract(currentPos);
        if (delta.lengthSquared() <= 1e-24) {
            return currentPos;
        }

        Vec3d deltaXZ = new Vec3d(delta.x, 0.0, delta.z);
        Vec3d moveXZ = deltaXZ.multiply(positionEasingXZ);
        double maxMoveXZ = positionSpeedLimitXZ * deltaSeconds;
        double moveXZLength = moveXZ.length();
        if (moveXZLength > maxMoveXZ && moveXZLength > 1e-12) {
            moveXZ = moveXZ.multiply(maxMoveXZ / moveXZLength);
        }

        double moveY = delta.y * easingY;
        double maxMoveY = speedLimitY * deltaSeconds;
        if (Math.abs(moveY) > maxMoveY) {
            moveY = Math.copySign(maxMoveY, moveY);
        }

        return currentPos.add(moveXZ.x, moveY, moveXZ.z);
    }

    private float easedAngle(float currentAngle, float targetAngle, float deltaSeconds) {
        float err = targetAngle - currentAngle;
        while (err > 180) err -= 360;
        while (err < -180) err += 360;

        float desiredSpeed = (float) (err * rotationEasing);
        float maxRotation = (float) (rotationSpeedLimit * deltaSeconds);
        if (Math.abs(desiredSpeed) > maxRotation) desiredSpeed = Math.signum(desiredSpeed) * maxRotation;
        return currentAngle + desiredSpeed;
    }

    private float easedFov(float currentFov, float targetFov, float deltaSeconds) {
        float fovError = targetFov - currentFov;
        float absFovError = Math.abs(fovError);
        float adaptiveFovEasing = (float) (fovEasing * (0.5 + 0.5 * (absFovError / 0.1f)));
        if (adaptiveFovEasing > fovEasing) adaptiveFovEasing = (float) fovEasing;
        float desiredFovSpeed = fovError * adaptiveFovEasing;

        float maxFovChange = (float) (fovSpeedLimit * deltaSeconds);
        if (Math.abs(desiredFovSpeed) > maxFovChange) desiredFovSpeed = Math.signum(desiredFovSpeed) * maxFovChange;
        return currentFov + desiredFovSpeed;
    }

    @Override
    public void queueReset(MinecraftClient client, Camera camera) {
        if (!resetting) {
            resetting = true;
            finalInterpActive = false;
            finalInterpT = 0.0;
            finalInterpStart = null;
            current = CameraTarget.fromCamera(camera);
        }
    }

    public boolean isResetting() {
        return resetting;
    }

    public void resumeOutPhase(MinecraftClient client, Camera camera) {
        if (!resetting) {
            return;
        }
        resetting = false;
        finalInterpActive = false;
        finalInterpT = 0.0;
        finalInterpStart = null;
        if (camera != null) {
            current = CameraTarget.fromCamera(camera);
        }
        Vec3d stickPos = CameraController.controlStick.getPosition();
        lastStickYaw = CameraController.controlStick.getYaw();
        startPlayerPosXZ = new Vec3d(stickPos.x, 0.0, stickPos.z);
        orbitTargetXZ = new Vec3d(current.getPosition().x, 0.0, current.getPosition().z);
        clampArmed = false;
        alpha = 1.0;
    }

    @Override
    public void adjustDistance(boolean increase, MinecraftClient client) {
        if (mouseWheel == SCROLL_WHEEL.FOV) {
            adjustFov(increase, client);
        }
    }

    @Override
    public String getName() {
        return "Follow";
    }

    @Override
    public float getWeight() {
        return 1.0f;
    }

    @Override
    public boolean isComplete() {
        if (!resetting) return false;
        if (MinecraftClient.getInstance().player == null) return true;
        Vec3d playerPos = MinecraftClient.getInstance().player.getEyePos();
        double positionDistance = current.getPosition().distanceTo(playerPos);
        float fovDifference = Math.abs(current.getFovMultiplier() - 1.0f);
        boolean positionComplete = positionDistance < 0.005 || (finalInterpActive && finalInterpT >= 0.9999);
        boolean fovComplete = fovDifference < 0.01f;
        return positionComplete && fovComplete;
    }

    @Override
    public boolean hasCompletedOutPhase() {
        return false;
    }
}
