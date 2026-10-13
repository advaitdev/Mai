package me.advait.mai.pathetic.movement.types;

import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import me.advait.mai.pathetic.BlockClassifier;
import me.advait.mai.pathetic.PathContext;
import me.advait.mai.pathetic.debug.PathDebugLog;
import me.advait.mai.pathetic.movement.*;

/**
 * Fall 2-3 blocks (no damage) into an adjacent column. Landing must be
 * standable, and the destination column must be clear from the source
 * level all the way down — the bot walks off the lip at the source height
 * and drops through that column, so that is the column to check (not the
 * source column, which is always solid underfoot).
 */
public record FallSafe() implements MovementType {

    @Override
    public String key() { return "fall_safe"; }

    @Override
    public boolean matches(PathPosition current, PathPosition previous, PathContext ctx) {
        int dx = current.getFlooredX() - previous.getFlooredX();
        int dy = current.getFlooredY() - previous.getFlooredY();
        int dz = current.getFlooredZ() - previous.getFlooredZ();

        // dy must be -2 or -3, horizontal offset exactly 1 (cardinal or diagonal)
        if (dy > -2 || dy < -3) return false;
        if (Math.abs(dx) > 1 || Math.abs(dz) > 1) return false;
        if (dx == 0 && dz == 0) return false;   // can't fall through solid floor

        // Landing must be standable
        if (!WalkFlat.isStandable(current, ctx.materials())) return false;

        // Destination column must be open from source level down to landing
        return isFallClear(previous, current, ctx.materials());
    }

    @Override
    public double computeCost(PathPosition current, PathPosition previous, PathContext ctx) {
        int blocks = Math.abs(current.getFlooredY() - previous.getFlooredY());
        return ctx.config().getFallSafeBase() + blocks * ctx.config().getFallPerBlock();
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
        return tickFall(ctx);
    }

    /**
     * Shared fall executor (also used by {@link FallUnsafe}). On the ground:
     * walk toward the landing column at walking speed (precision over
     * speed — a sprinting bot overshoots a 1-wide landing). In the air:
     * steer toward the landing. Success on touchdown at the landing column.
     */
    static MovementStatus tickFall(TickContext ctx) {
        // Standing on the landing is success no matter how we got here. The
        // landing is 2+ blocks below the source, so reachedFully (|dy| < 1.5)
        // can't fire before the drop. Without this, a fall whose airborne
        // ticks were never observed (the drop completed between our ticks)
        // left the bot standing on its waypoint forever.
        if (ctx.onGround() && MovementExecutors.reachedFully(ctx)) {
            return MovementStatus.SUCCESS;
        }
        if (ctx.phase == 0) {
            if (!ctx.onGround()) {
                ctx.phase = 1;
            } else {
                MovementExecutors.groundAccelerate(ctx, 1.0, false);
                return MovementStatus.RUNNING;
            }
        }

        AirControl.steer(ctx, ctx.waypoint.x(), ctx.waypoint.z(), ctx.waypoint.y(),
                MovementExecutors.WALK_AIR_ACCEL);

        if (ctx.onGround() && ctx.entity().getVelocity().getY() <= 0) {
            // Touched down somewhere that isn't the landing (a ledge on the
            // way down, or we drifted). Replan from here.
            PathDebugLog.event("FALL_OFF_TARGET bot=(%.2f,%.2f,%.2f) wp=(%.2f,%.2f,%.2f)",
                    ctx.current.getX(), ctx.current.getY(), ctx.current.getZ(),
                    ctx.waypoint.x(), ctx.waypoint.y(), ctx.waypoint.z());
            return MovementStatus.FAILED;
        }
        return MovementStatus.RUNNING;
    }

    @Override
    public boolean safeToCancel(TickContext ctx) {
        // Mid-fall can't be interrupted cleanly; gravity is committing us.
        return ctx.phase == 0;
    }

    /**
     * The destination column must be traversable at feet and head level for
     * every Y from the source level down to the landing: the bot steps into
     * it at the source height, then drops. For diagonal steps the two
     * cardinal side columns at the source level must also be open so the
     * 0.6-wide hitbox doesn't catch a corner on the way out.
     */
    static boolean isFallClear(PathPosition source, PathPosition landing, MaterialProvider materials) {
        int lx = landing.getFlooredX(), lz = landing.getFlooredZ();
        int sy = source.getFlooredY(), ly = landing.getFlooredY();
        for (int y = sy + 1; y >= ly; y--) {
            if (!BlockClassifier.isTraversable(materials.getMaterial(lx, y, lz))) return false;
        }
        int dx = lx - source.getFlooredX(), dz = lz - source.getFlooredZ();
        if (dx != 0 && dz != 0) {
            int sx = source.getFlooredX(), sz = source.getFlooredZ();
            if (!BlockClassifier.isTraversable(materials.getMaterial(sx + dx, sy, sz))) return false;
            if (!BlockClassifier.isTraversable(materials.getMaterial(sx + dx, sy + 1, sz))) return false;
            if (!BlockClassifier.isTraversable(materials.getMaterial(sx, sy, sz + dz))) return false;
            if (!BlockClassifier.isTraversable(materials.getMaterial(sx, sy + 1, sz + dz))) return false;
        }
        return true;
    }
}
