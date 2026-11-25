package me.advait.mai.pathetic.movement.types;

import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import me.advait.mai.pathetic.PathContext;
import me.advait.mai.pathetic.movement.*;

/**
 * Step down 1 block: dy=-1 with horizontal distance 0-1.
 * Walk off the edge of a block and land 1 block below. No damage.
 */
public record StepDown() implements MovementType {

    @Override
    public String key() { return "step_down"; }

    @Override
    public boolean matches(PathPosition current, PathPosition previous, PathContext ctx) {
        int dx = current.getFlooredX() - previous.getFlooredX();
        int dy = current.getFlooredY() - previous.getFlooredY();
        int dz = current.getFlooredZ() - previous.getFlooredZ();

        if (dy != -1) return false;
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        if (horizDist > 1.5) return false;

        // The bot walks out over the landing at the upper level before it
        // drops, so its head passes the cell two above the landing: a leaf
        // there (a jungle canopy) stopped it at the edge for good.
        if (!me.advait.mai.pathetic.BlockClassifier.isTraversable(ctx.materials().getMaterial(
                current.getFlooredX(), current.getFlooredY() + 2, current.getFlooredZ()))) return false;
        return WalkFlat.isStandable(current, ctx.materials())
                && WalkDiagonal.levelChangeCornersOk(previous, current, ctx.materials());
    }

    @Override
    public double computeCost(PathPosition current, PathPosition previous, PathContext ctx) {
        return ctx.config().getStepDown();
    }

    @Override
    public ExecutionHint executionHint(PathPosition current, PathPosition previous) {
        return ExecutionHint.walk();
    }

    @Override
    public boolean canReachAsEndpoint(PathPosition position, PathContext ctx) {
        return WalkFlat.isStandable(position, ctx.materials());
    }

    @Override
    public MovementStatus tick(TickContext ctx) {
        // Walk off the edge — gravity handles the drop. Keep applying
        // horizontal acceleration until we've reached the landing column.
        boolean sprinting = ctx.speedFactor > 1.0;
        // The last drop onto the block the walk stops on: aim the touchdown
        // at its center. Holding W through the air landed on the far edge at
        // sprint speed, and the bot slid off the block it had arrived on.
        double above = ctx.current.getY() - ctx.waypoint.y();
        if (!sprinting && ctx.nextWaypoint == null && !ctx.onGround() && !ctx.entity().isInWater()
                && above > 0 && above < 1.3 && MovementExecutors.horizontalDistance(ctx.current, ctx.waypoint) < 1.5) {
            AirControl.steer(ctx, ctx.waypoint.x(), ctx.waypoint.z(), ctx.waypoint.y());
            return MovementStatus.RUNNING;
        }
        MovementExecutors.walk(ctx, sprinting);
        // Landed: on the ground below. reachedFully allows 1.5 vertically,
        // so a bot still on the ledge over the target counted as down and
        // stopped there for good when it was the last waypoint.
        return (ctx.onGround() && MovementExecutors.reachedFully(ctx)
                && ctx.current.getY() < ctx.waypoint.y() + 0.5)
                ? MovementStatus.SUCCESS : MovementStatus.RUNNING;
    }
}
