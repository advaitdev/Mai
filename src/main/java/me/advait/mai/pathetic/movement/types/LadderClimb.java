package me.advait.mai.pathetic.movement.types;

import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import me.advait.mai.pathetic.BlockClassifier;
import me.advait.mai.pathetic.PathContext;
import me.advait.mai.pathetic.capabilities.HumanoidCapabilities;
import me.advait.mai.pathetic.movement.*;
import org.bukkit.Material;

/**
 * Climbing a ladder or vine. Can go up, down, or stay level.
 * Current position must have a climbable block at feet level.
 */
public record LadderClimb() implements MovementType {

    @Override
    public String key() { return "ladder_climb"; }

    @Override
    public boolean matches(PathPosition current, PathPosition previous, PathContext ctx) {
        MaterialProvider materials = ctx.materials();
        Material atFeet = materials.getMaterial(current);
        if (!BlockClassifier.isClimbable(atFeet)) return false;

        // Must have passable head space
        Material atHead = materials.getMaterial(
                current.getFlooredX(), current.getFlooredY() + 1, current.getFlooredZ());
        return BlockClassifier.isTraversable(atHead);
    }

    @Override
    public double computeCost(PathPosition current, PathPosition previous, PathContext ctx) {
        int dy = current.getFlooredY() - previous.getFlooredY();
        return dy >= 0 ? ctx.config().getLadderUp() : ctx.config().getLadderDown();
    }

    @Override
    public ExecutionHint executionHint(PathPosition current, PathPosition previous) {
        return ExecutionHint.climb();
    }

    @Override
    public boolean isAllowed(HumanoidCapabilities caps) {
        return caps.canClimb();
    }

    @Override
    public boolean canReachAsEndpoint(PathPosition position, PathContext ctx) {
        // Any position whose feet block is a ladder/vine is a valid
        // landing for a climb, regardless of what's below it.
        MaterialProvider materials = ctx.materials();
        Material atFeet = materials.getMaterial(position);
        if (!BlockClassifier.isClimbable(atFeet)) return false;
        Material atHead = materials.getMaterial(
                position.getFlooredX(), position.getFlooredY() + 1, position.getFlooredZ());
        return BlockClassifier.isTraversable(atHead);
    }

    @Override
    public MovementStatus tick(TickContext ctx) {
        double dy = ctx.waypoint.y() - ctx.current.getY();
        double climbSpeed = dy >= 0 ? 0.12 : -0.15;
        double[] dir = MovementExecutors.direction2D(ctx.current, ctx.waypoint);

        org.bukkit.util.Vector vel = ctx.entity().getVelocity();
        ctx.entity().setVelocity(new org.bukkit.util.Vector(
                vel.getX() + dir[0] * 0.02,
                climbSpeed,
                vel.getZ() + dir[1] * 0.02));
        MovementExecutors.face(ctx.entity(), dir);

        return MovementExecutors.reachedFully(ctx) ? MovementStatus.SUCCESS : MovementStatus.RUNNING;
    }
}
