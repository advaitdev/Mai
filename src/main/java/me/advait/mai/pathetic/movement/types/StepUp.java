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

        // No +2 out of deep water: floating at the surface, vanilla's
        // climb-out boost peaks about 0.1 short of a bank a block above the
        // water (measured), so such a pool is a trap without blocks.
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
        // A slab, path or carpet is climbed by the vanilla step height; only
        // jump when the real rise is taller than that.
        boolean needsJump = MovementExecutors.needsJumpUp(ctx.current, ctx.waypoint);

        // Vanilla jump: peak y=1.2522 at tick ~5-6; the feet are at least one
        // block up for ticks 3-8. Jump on the earliest tick whose arc still
        // carries the hitbox over the step by then, from the real speed.
        if (ctx.onGround() && ctx.jumpCooldown == 0 && needsJump
                && MovementExecutors.stepUpJumpNow(ctx, ctx.waypoint)) {
            PathDebugLog.event("STEP_UP_JUMP dist=%.2f speed=%.3f sprinting=%s bot=(%.2f,%.2f,%.2f)",
                    horizDist, currentSpeed, sprinting,
                    ctx.current.getX(), ctx.current.getY(), ctx.current.getZ());
            MovementExecutors.jump(ctx);
        }

        MovementExecutors.walk(ctx, sprinting);

        // Success once we're standing on the step: feet at (or above) the
        // real top of its support, which is half a block short of the
        // waypoint's cell for a slab, 1/16 short for a path or carpet.
        if (MovementExecutors.reachedHorizontally(ctx)
                && MovementExecutors.stepRise(ctx.current, ctx.waypoint) <= 0.1) {
            return MovementStatus.SUCCESS;
        }
        return MovementStatus.RUNNING;
    }
}
