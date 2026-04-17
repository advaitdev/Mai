package me.advait.mai.pathetic.movement.types;

import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import me.advait.mai.pathetic.BlockClassifier;
import me.advait.mai.pathetic.PathContext;
import me.advait.mai.pathetic.capabilities.HumanoidCapabilities;
import me.advait.mai.pathetic.debug.PathDebugLog;
import me.advait.mai.pathetic.movement.*;
import org.bukkit.Material;

/**
 * Jump up 1 block while moving 1 block horizontally.
 * Requires solid ground at destination, passable at feet+head of destination,
 * and 3-block headroom at source (room to jump).
 */
public record StepUp() implements MovementType {

    @Override
    public String key() { return "step_up"; }

    @Override
    public boolean matches(PathPosition current, PathPosition previous, PathContext ctx) {
        int dx = current.getFlooredX() - previous.getFlooredX();
        int dy = current.getFlooredY() - previous.getFlooredY();
        int dz = current.getFlooredZ() - previous.getFlooredZ();

        if (dy != 1) return false;
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        if (horizDist < 0.5 || horizDist > 1.5) return false;

        // Destination must be standable
        if (!WalkFlat.isStandable(current, ctx.materials())) return false;

        // Source must have 3 blocks of headroom for jumping (feet, head, above head)
        Material aboveHead = ctx.materials().getMaterial(
                previous.getFlooredX(), previous.getFlooredY() + 2, previous.getFlooredZ());
        return BlockClassifier.isTraversable(aboveHead);
    }

    @Override
    public double computeCost(PathPosition current, PathPosition previous, PathContext ctx) {
        Material below = ctx.materials().getMaterial(current.getFlooredX(), current.getFlooredY() - 1, current.getFlooredZ());
        double blockMult = WalkFlat.blockCostMultiplier(below, ctx.config());
        // Step up = sprint cost + jump penalty (hunger from both sprint + jump)
        return (ctx.config().getStepUp() + ctx.config().getJumpPenalty()) * blockMult
                + ctx.config().getHungerSprintCost();
    }

    @Override
    public ExecutionHint executionHint(PathPosition current, PathPosition previous) {
        return ExecutionHint.walkJump();
    }

    @Override
    public boolean isAllowed(HumanoidCapabilities caps) {
        return caps.canJump();
    }

    @Override
    public boolean canReachAsEndpoint(PathPosition position, PathContext ctx) {
        return WalkFlat.isStandable(position, ctx.materials());
    }

    @Override
    public MovementStatus tick(TickContext ctx) {
        boolean sprinting = ctx.speedFactor > 1.0;
        double horizDist = MovementExecutors.horizontalDistance(ctx.current, ctx.waypoint);
        double currentSpeed = MovementExecutors.horizontalSpeed(ctx.entity().getVelocity());
        boolean needsJump = ctx.waypoint.y() > ctx.current.getY() + 0.3;

        // Vanilla jump: peak y=1.2522 at tick ~5-6; bot is ≥1 block up for
        // ticks 3-8 (5-tick clearance window). At terminal sprint 0.28 b/t
        // that covers ~1.4 blocks; at walk 0.21 b/t, ~1.05 blocks. Use
        // a wider window when sprinting because the bot carries more
        // horizontal momentum into the arc.
        double jumpWindowMax = sprinting ? 2.3 : 1.6;

        // Takeoff clearance velocity. Rather than waiting for ground accel
        // to bring us up to this speed (which takes 3+ ticks and makes
        // step-ups feel sluggish), we inject it directly before jumping.
        // 0.18 b/t is enough for air drift to cover the 1-block horizontal
        // distance during the 5-tick clearance window.
        double takeoffSpeed = sprinting ? 0.24 : 0.18;

        if (ctx.onGround() && ctx.jumpCooldown == 0 && needsJump
                && horizDist > 0.4 && horizDist < jumpWindowMax) {
            double[] dir = MovementExecutors.direction2D(ctx.current, ctx.waypoint);
            MovementExecutors.kickToward(ctx, dir, takeoffSpeed);
            PathDebugLog.event("STEP_UP_JUMP dist=%.2f speed=%.3f sprinting=%s bot=(%.2f,%.2f,%.2f)",
                    horizDist, currentSpeed, sprinting,
                    ctx.current.getX(), ctx.current.getY(), ctx.current.getZ());
            MovementExecutors.jump(ctx, sprinting);
        }

        MovementExecutors.groundAccelerate(ctx, ctx.speedFactor, sprinting);

        // Success only once we're up at the target Y
        if (MovementExecutors.reachedHorizontally(ctx)
                && ctx.current.getY() >= ctx.waypoint.y() - 0.1) {
            return MovementStatus.SUCCESS;
        }
        return MovementStatus.RUNNING;
    }
}
