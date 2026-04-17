package me.advait.mai.pathetic.movement.types;

import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import me.advait.mai.pathetic.BlockClassifier;
import me.advait.mai.pathetic.PathContext;
import me.advait.mai.pathetic.capabilities.HumanoidCapabilities;
import me.advait.mai.pathetic.movement.*;
import org.bukkit.Material;

/**
 * Swimming through water or lava. Current position's feet block is liquid.
 */
public record Swim() implements MovementType {

    @Override
    public String key() { return "swim"; }

    @Override
    public boolean matches(PathPosition current, PathPosition previous, PathContext ctx) {
        Material atFeet = ctx.materials().getMaterial(current);
        return BlockClassifier.isLiquid(atFeet);
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
        // Any position whose feet block is a liquid is a valid swim endpoint.
        Material atFeet = ctx.materials().getMaterial(position);
        return BlockClassifier.isLiquid(atFeet);
    }

    @Override
    public boolean allowsAutoUnstick() {
        // Swimming has no notion of a jump; an unstick impulse would just
        // add a stray vertical to a bot already in liquid.
        return false;
    }

    @Override
    public MovementStatus tick(TickContext ctx) {
        double[] dir3d = MovementExecutors.direction3D(ctx.current, ctx.waypoint);
        org.bukkit.util.Vector vel = ctx.entity().getVelocity();
        double swimAccel = 0.04;
        ctx.entity().setVelocity(new org.bukkit.util.Vector(
                vel.getX() + dir3d[0] * swimAccel,
                vel.getY() + dir3d[1] * swimAccel,
                vel.getZ() + dir3d[2] * swimAccel));
        MovementExecutors.face(ctx.entity(), new double[]{dir3d[0], dir3d[2]});

        return MovementExecutors.reachedFully(ctx) ? MovementStatus.SUCCESS : MovementStatus.RUNNING;
    }
}
