package me.advait.mai.pathetic.movement.types;

import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import me.advait.mai.pathetic.BlockClassifier;
import me.advait.mai.pathetic.PathContext;
import me.advait.mai.pathetic.capabilities.HumanoidCapabilities;
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
        double horizDist = MovementExecutors.horizontalDistance(ctx.current, ctx.waypoint);
        boolean needsJump = ctx.waypoint.y() > ctx.current.getY() + 0.3;

        // Preemptive jump: fire once we're close enough to the obstacle.
        // 1.8-block window is far enough for vanilla physics to clear one
        // block comfortably even at walk speed, close enough that we don't
        // jump early and lose momentum.
        if (ctx.onGround() && ctx.jumpCooldown == 0 && needsJump && horizDist < 1.8) {
            MovementExecutors.jump(ctx, ctx.speedFactor > 1.0);
        }

        boolean sprinting = ctx.speedFactor > 1.0;
        MovementExecutors.groundAccelerate(ctx, ctx.speedFactor, sprinting);

        // Success only once we're up at the target Y
        if (MovementExecutors.reachedHorizontally(ctx)
                && ctx.current.getY() >= ctx.waypoint.y() - 0.1) {
            return MovementStatus.SUCCESS;
        }
        return MovementStatus.RUNNING;
    }
}
