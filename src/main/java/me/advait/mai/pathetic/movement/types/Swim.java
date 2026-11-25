package me.advait.mai.pathetic.movement.types;

import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import me.advait.mai.body.Body;
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
        // One cell at a time, like walking. Without this any jump or fall
        // offset that ended in water classified as a single "swim".
        int dx = current.getFlooredX() - previous.getFlooredX();
        int dy = current.getFlooredY() - previous.getFlooredY();
        int dz = current.getFlooredZ() - previous.getFlooredZ();
        if (Math.abs(dx) > 1 || Math.abs(dy) > 1 || Math.abs(dz) > 1) return false;
        if (ctx.materials().getMaterial(current) != Material.WATER) return false;
        Material head = ctx.materials().getMaterial(current.getFlooredX(), current.getFlooredY() + 1, current.getFlooredZ());
        return head == Material.WATER || BlockClassifier.isTraversable(head);
    }

    @Override
    public double computeCost(PathPosition current, PathPosition previous, PathContext ctx) {
        // Swimming with the head under water means holding breath and
        // fighting buoyancy; stay at the surface unless diving is the only way.
        Material head = ctx.materials().getMaterial(current.getFlooredX(), current.getFlooredY() + 1, current.getFlooredZ());
        double submerged = head == Material.WATER ? SUBMERGED_MULTIPLIER : 1.0;
        int dx = current.getFlooredX() - previous.getFlooredX();
        int dz = current.getFlooredZ() - previous.getFlooredZ();
        double horiz = (dx != 0 && dz != 0) ? Math.sqrt(2) : 1.0;
        return ctx.config().getSwim() * horiz * submerged;
    }

    private static final double SUBMERGED_MULTIPLIER = 1.5;
    /** Air ticks (of 300) below which a sprint-swimmer heads up to breathe. */
    private static final int LOW_AIR = 90;

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
        double[] dir = MovementExecutors.direction2D(ctx.current, ctx.waypoint);
        boolean centered = MovementExecutors.reachedHorizontally(ctx);
        boolean moreWater = ctx.nextWaypoint != null && ctx.nextWaypoint.type() != null
                && "swim".equals(ctx.nextWaypoint.type().key());

        // Sprint-swimming needs the head under water, so it needs water two
        // deep. In a shallow stream holding shift to duck under never gets
        // the head wet: the bot crept at sneak speed and lost to the current.
        var loc = ctx.current;
        boolean deep = ctx.entity().isUnderWater() || loc.getWorld().getBlockAt(
                loc.getBlockX(), (int) Math.floor(loc.getY()) + 1, loc.getBlockZ()).getType() == Material.WATER;

        if (moreWater && !centered && !deep) {
            // Wading: walk with jump held, like walking through any water.
            MovementExecutors.walk(ctx, false);
        } else if (moreWater && !centered) {
            // Open water: sprint-swim just under the surface like a player
            // (about twice surface speed). A sprint can't start with the head
            // above water, so first hold shift to duck under; once swimming,
            // the view steers depth: level to cruise, up to breathe or rise.
            float yaw = Body.yawOf(dir[0], dir[1]);
            if (!ctx.entity().isSwimming()) {
                boolean under = ctx.entity().isUnderWater();
                ctx.body().look(yaw, 0f).keys(1, 0).sneak(!under).sprint(under);
            } else {
                boolean lowAir = ctx.entity().getRemainingAir() < LOW_AIR;
                float pitch;
                if (lowAir || ctx.waypoint.y() > ctx.current.getY() + 0.6) pitch = -30f;
                else if (ctx.waypoint.y() < ctx.current.getY() - 0.6) pitch = 35f;
                else pitch = 0f;
                ctx.body().look(yaw, pitch).keys(1, 0).sprint(true);
            }
        } else {
            // Last stretch before land, or lining up: swim at the surface.
            // W pushes along the view, holding jump rises (and keeps the bot
            // afloat), letting go sinks.
            ctx.body().look(Body.yawOf(dir[0], dir[1]), 0f).keys(centered ? 0 : 1, 0).sprint(false);
            if (ctx.waypoint.y() > ctx.current.getY() - 0.5) ctx.body().jump();
        }

        return MovementExecutors.reachedFully(ctx) ? MovementStatus.SUCCESS : MovementStatus.RUNNING;
    }
}
