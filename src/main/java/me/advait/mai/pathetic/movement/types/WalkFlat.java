package me.advait.mai.pathetic.movement.types;

import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import me.advait.mai.pathetic.BlockClassifier;
import me.advait.mai.pathetic.PathContext;
import me.advait.mai.pathetic.config.MovementConfig;
import me.advait.mai.pathetic.movement.*;
import org.bukkit.Material;

/**
 * Cardinal flat walk: dy=0, horizontal distance ~1 block.
 * Solid ground below, passable at feet and head.
 */
public record WalkFlat() implements MovementType {

    @Override
    public String key() { return "walk_flat"; }

    @Override
    public boolean matches(PathPosition current, PathPosition previous, PathContext ctx) {
        int dx = current.getFlooredX() - previous.getFlooredX();
        int dy = current.getFlooredY() - previous.getFlooredY();
        int dz = current.getFlooredZ() - previous.getFlooredZ();

        if (dy != 0) return false;
        // Cardinal: exactly one of dx/dz is ±1, the other is 0
        if (!((Math.abs(dx) == 1 && dz == 0) || (dx == 0 && Math.abs(dz) == 1))) return false;

        return isStandable(current, ctx.materials());
    }

    @Override
    public double computeCost(PathPosition current, PathPosition previous, PathContext ctx) {
        Material below = ctx.materials().getMaterial(current.getFlooredX(), current.getFlooredY() - 1, current.getFlooredZ());
        double blockMult = blockCostMultiplier(below, ctx.config());
        // Base sprint cost + hunger drain from sprinting
        return ctx.config().getWalkFlat() * blockMult + ctx.config().getHungerSprintCost();
    }

    @Override
    public ExecutionHint executionHint(PathPosition current, PathPosition previous) {
        return ExecutionHint.sprint();
    }

    @Override
    public boolean canReachAsEndpoint(PathPosition position, PathContext ctx) {
        // Walking requires a standable target — solid below, passable at
        // feet and head. This is the canonical endpoint check for all
        // ground-based movement types; others delegate here.
        return isStandable(position, ctx.materials());
    }

    @Override
    public MovementStatus tick(TickContext ctx) {
        boolean sprinting = ctx.speedFactor > 1.0;
        MovementExecutors.groundAccelerate(ctx, ctx.speedFactor, sprinting);
        return MovementExecutors.reachedFully(ctx) ? MovementStatus.SUCCESS : MovementStatus.RUNNING;
    }

    static boolean isStandable(PathPosition pos, MaterialProvider materials) {
        Material below = materials.getMaterial(pos.getFlooredX(), pos.getFlooredY() - 1, pos.getFlooredZ());
        Material atFeet = materials.getMaterial(pos);
        Material atHead = materials.getMaterial(pos.getFlooredX(), pos.getFlooredY() + 1, pos.getFlooredZ());
        return BlockClassifier.isSolid(below)
                && BlockClassifier.isTraversable(atFeet)
                && BlockClassifier.isTraversable(atHead);
    }

    static double blockCostMultiplier(Material below, MovementConfig config) {
        if (below == Material.SOUL_SAND || below == Material.SOUL_SOIL) return config.getSoulSandMultiplier();
        if (below == Material.ICE || below == Material.PACKED_ICE
                || below == Material.FROSTED_ICE || below == Material.BLUE_ICE) return config.getIceMultiplier();
        if (BlockClassifier.isDangerous(below)) return 3.0; // must sneak on magma
        return 1.0;
    }
}
