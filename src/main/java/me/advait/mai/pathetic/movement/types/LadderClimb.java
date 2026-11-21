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
        // Climbing moves one block at a time; without this a 20-block fall or
        // a 4-block jump that ends in a vine would classify as a climb.
        if (Math.abs(current.getFlooredX() - previous.getFlooredX()) > 1
                || Math.abs(current.getFlooredY() - previous.getFlooredY()) > 1
                || Math.abs(current.getFlooredZ() - previous.getFlooredZ()) > 1) return false;
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
    public boolean allowsAutoUnstick() {
        // Climbing uses a steady upward impulse — a jump here would fling
        // the bot off the ladder.
        return false;
    }

    @Override
    public MovementStatus tick(TickContext ctx) {
        // Vanilla climbing: on a ladder or vine, holding jump (or pushing into
        // a wall) climbs at 0.2 b/t; letting go slides down at most 0.15 b/t.
        double dy = ctx.waypoint.y() - ctx.current.getY();
        double[] dir = MovementExecutors.direction2D(ctx.current, ctx.waypoint);
        MovementExecutors.face(ctx.entity(), dir);
        boolean centered = MovementExecutors.reachedHorizontally(ctx);
        ctx.body().keys(centered ? 0 : 1, 0).sprint(false);
        if (dy >= 0) ctx.body().jump();

        return MovementExecutors.reachedFully(ctx) ? MovementStatus.SUCCESS : MovementStatus.RUNNING;
    }
}
