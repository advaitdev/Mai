package me.advait.mai.pathetic.movement.types;

import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import me.advait.mai.pathetic.PathContext;
import me.advait.mai.pathetic.capabilities.HumanoidCapabilities;
import me.advait.mai.pathetic.debug.PathDebugLog;
import me.advait.mai.pathetic.movement.*;
import org.bukkit.Material;

/**
 * Drop any height into water. Vanilla resets fall distance the moment the
 * entity touches water, so the landing is harmless however far it is. The
 * bot walks off the edge, steers onto the water column in the air, and is
 * done once it is in the water over the landing.
 */
public record FallIntoWater() implements MovementType {

    @Override
    public String key() { return "fall_into_water"; }

    @Override
    public boolean matches(PathPosition current, PathPosition previous, PathContext ctx) {
        int dx = current.getFlooredX() - previous.getFlooredX();
        int dy = current.getFlooredY() - previous.getFlooredY();
        int dz = current.getFlooredZ() - previous.getFlooredZ();
        if (dy > -2) return false;
        if (Math.abs(dx) > 1 || Math.abs(dz) > 1 || (dx == 0 && dz == 0)) return false;
        if (ctx.materials().getMaterial(current) != Material.WATER) return false;
        return FallSafe.isFallClear(previous, current, ctx.materials());
    }

    @Override
    public double computeCost(PathPosition current, PathPosition previous, PathContext ctx) {
        int blocks = Math.abs(current.getFlooredY() - previous.getFlooredY());
        return ctx.config().fallCostTicks(blocks) + ctx.config().getFallSafeBase();
    }

    @Override
    public ExecutionHint executionHint(PathPosition current, PathPosition previous) {
        return ExecutionHint.walk();
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
    public MovementStatus tick(TickContext ctx) {
        if (ctx.entity().isInWater() && MovementExecutors.reachedHorizontally(ctx)) {
            return MovementStatus.SUCCESS;
        }
        if (ctx.phase == 0) {
            if (!ctx.onGround()) {
                ctx.phase = 1;
            } else {
                MovementExecutors.walk(ctx, false);
                return MovementStatus.RUNNING;
            }
        }
        AirControl.steer(ctx, ctx.waypoint.x(), ctx.waypoint.z(), ctx.waypoint.y());
        if (ctx.onGround()) {
            PathDebugLog.event("FALL_WATER_MISSED bot=(%.2f,%.2f,%.2f) wp=(%.2f,%.2f,%.2f)",
                    ctx.current.getX(), ctx.current.getY(), ctx.current.getZ(),
                    ctx.waypoint.x(), ctx.waypoint.y(), ctx.waypoint.z());
            return MovementStatus.FAILED;
        }
        return MovementStatus.RUNNING;
    }

    @Override
    public boolean safeToCancel(TickContext ctx) {
        return ctx.phase == 0;
    }
}
