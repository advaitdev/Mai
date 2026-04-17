package me.advait.mai.pathetic.movement.types;

import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import me.advait.mai.pathetic.BlockClassifier;
import me.advait.mai.pathetic.PathContext;
import me.advait.mai.pathetic.movement.*;
import org.bukkit.Material;

/**
 * Diagonal flat walk: dy=0, both dx and dz are ±1.
 * Cost is sqrt(2) times the cardinal walk cost.
 */
public record WalkDiagonal() implements MovementType {

    @Override
    public String key() { return "walk_diagonal"; }

    @Override
    public boolean matches(PathPosition current, PathPosition previous, PathContext ctx) {
        int dx = current.getFlooredX() - previous.getFlooredX();
        int dy = current.getFlooredY() - previous.getFlooredY();
        int dz = current.getFlooredZ() - previous.getFlooredZ();

        if (dy != 0) return false;
        if (Math.abs(dx) != 1 || Math.abs(dz) != 1) return false;

        // Reject corner-cutting through walls. A diagonal step from (px,pz)
        // to (px+dx, pz+dz) sweeps the player's 0.6-wide bounding box
        // across both cardinal neighbors. If either is blocked at feet or
        // head level, the entity will catch on the corner even though the
        // destination cell itself is standable.
        MaterialProvider m = ctx.materials();
        int px = previous.getFlooredX(), py = previous.getFlooredY(), pz = previous.getFlooredZ();
        if (!BlockClassifier.isTraversable(m.getMaterial(px + dx, py,     pz))) return false;
        if (!BlockClassifier.isTraversable(m.getMaterial(px,     py,     pz + dz))) return false;
        if (!BlockClassifier.isTraversable(m.getMaterial(px + dx, py + 1, pz))) return false;
        if (!BlockClassifier.isTraversable(m.getMaterial(px,     py + 1, pz + dz))) return false;

        return WalkFlat.isStandable(current, m);
    }

    @Override
    public double computeCost(PathPosition current, PathPosition previous, PathContext ctx) {
        Material below = ctx.materials().getMaterial(current.getFlooredX(), current.getFlooredY() - 1, current.getFlooredZ());
        double blockMult = WalkFlat.blockCostMultiplier(below, ctx.config());
        return ctx.config().getWalkDiagonal() * blockMult + ctx.config().getHungerSprintCost();
    }

    @Override
    public ExecutionHint executionHint(PathPosition current, PathPosition previous) {
        return ExecutionHint.sprint();
    }

    @Override
    public boolean canReachAsEndpoint(PathPosition position, PathContext ctx) {
        return WalkFlat.isStandable(position, ctx.materials());
    }

    @Override
    public MovementStatus tick(TickContext ctx) {
        boolean sprinting = ctx.speedFactor > 1.0;
        double[] dir = MovementExecutors.direction2D(ctx.current, ctx.waypoint);

        // Same anti-clip fallback as WalkFlat. Matters on diagonals too,
        // since the 45° sweep can touch an unexpected block on either
        // cardinal side.
        if (ctx.onGround() && ctx.jumpCooldown == 0
                && MovementExecutors.obstacleAheadNeedsJump(ctx.current, dir)) {
            MovementExecutors.jump(ctx, sprinting);
        }

        MovementExecutors.groundAccelerate(ctx, ctx.speedFactor, sprinting);
        return MovementExecutors.reachedFully(ctx) ? MovementStatus.SUCCESS : MovementStatus.RUNNING;
    }
}
