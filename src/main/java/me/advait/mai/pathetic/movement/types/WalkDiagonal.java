package me.advait.mai.pathetic.movement.types;

import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
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

        return WalkFlat.isStandable(current, ctx.materials());
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
        MovementExecutors.groundAccelerate(ctx, ctx.speedFactor, sprinting);
        return MovementExecutors.reachedFully(ctx) ? MovementStatus.SUCCESS : MovementStatus.RUNNING;
    }
}
