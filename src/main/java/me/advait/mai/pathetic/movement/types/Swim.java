package me.advait.mai.pathetic.movement.types;

import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import me.advait.mai.pathetic.BlockClassifier;
import me.advait.mai.pathetic.PathContext;
import me.advait.mai.pathetic.capabilities.HumanoidCapabilities;
import me.advait.mai.pathetic.movement.*;
import org.bukkit.Material;

/**
 * Swimming through water. Lava is never a route: without fire resistance
 * it is death, so it is neither traversable nor swimmable.
 */
public record Swim() implements MovementType {

    @Override
    public String key() { return "swim"; }

    @Override
    public boolean matches(PathPosition current, PathPosition previous, PathContext ctx) {
        return ctx.materials().getMaterial(current) == Material.WATER;
    }

    @Override
    public double computeCost(PathPosition current, PathPosition previous, PathContext ctx) {
        Material atFeet = ctx.materials().getMaterial(current);
        double mult = BlockClassifier.isLava(atFeet) ? ctx.config().getLavaMultiplier() : 1.0;
        return ctx.config().getSwim() * mult;
    }

    @Override
    public ExecutionHint executionHint(PathPosition current, PathPosition previous) {
        return ExecutionHint.swim();
    }

    @Override
    public boolean isAllowed(HumanoidCapabilities caps) {
        return caps.canSwim();
    }

    @Override
    public boolean canReachAsEndpoint(PathPosition position, PathContext ctx) {
        return ctx.materials().getMaterial(position) == Material.WATER;
    }

    @Override
    public boolean allowsAutoUnstick() {
        // Swimming has no notion of a jump; an unstick impulse would just
        // add a stray vertical to a bot already in liquid.
        return false;
    }

    @Override
    public MovementStatus tick(TickContext ctx) {
        // Vanilla water movement: W pushes along the view, holding jump rises
        // (and keeps the bot afloat at the surface), letting go sinks.
        double[] dir = MovementExecutors.direction2D(ctx.current, ctx.waypoint);
        MovementExecutors.face(ctx.entity(), dir);
        boolean centered = MovementExecutors.reachedHorizontally(ctx);
        ctx.body().keys(centered ? 0 : 1, 0).sprint(false);
        if (ctx.waypoint.y() > ctx.current.getY() - 0.3) ctx.body().jump();

        return MovementExecutors.reachedFully(ctx) ? MovementStatus.SUCCESS : MovementStatus.RUNNING;
    }
}
