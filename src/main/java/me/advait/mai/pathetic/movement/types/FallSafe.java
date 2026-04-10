package me.advait.mai.pathetic.movement.types;

import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import me.advait.mai.pathetic.BlockClassifier;
import me.advait.mai.pathetic.PathContext;
import me.advait.mai.pathetic.movement.*;

/**
 * Fall 2-3 blocks (no damage). Landing must be standable,
 * and the vertical path must be clear.
 */
public record FallSafe() implements MovementType {

    @Override
    public String key() { return "fall_safe"; }

    @Override
    public boolean matches(PathPosition current, PathPosition previous, PathContext ctx) {
        int dx = current.getFlooredX() - previous.getFlooredX();
        int dy = current.getFlooredY() - previous.getFlooredY();
        int dz = current.getFlooredZ() - previous.getFlooredZ();

        // dy must be -2 or -3, horizontal offset 0-1
        if (dy > -2 || dy < -3) return false;
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        if (horizDist > 1.5) return false;

        // Landing must be standable
        if (!WalkFlat.isStandable(current, ctx.materials())) return false;

        // Vertical path must be clear (check each Y level between prev and current)
        return isFallClear(previous, dy, ctx.materials());
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
        // Walk/fall toward the landing. Gravity does the work; we just
        // steer horizontally. Success when we touch down at the landing
        // column.
        if (ctx.onGround()) {
            MovementExecutors.groundAccelerate(ctx, ctx.speedFactor, ctx.speedFactor > 1.0);
        } else {
            ctx.phase = 1;
            MovementExecutors.airSteer(ctx, MovementExecutors.WALK_AIR_ACCEL);
        }

        if (ctx.phase == 1 && ctx.onGround() && MovementExecutors.reachedFully(ctx)) {
            return MovementStatus.SUCCESS;
        }
        return MovementStatus.RUNNING;
    }

    @Override
    public boolean safeToCancel(TickContext ctx) {
        // Mid-fall can't be interrupted cleanly; gravity is committing us.
        return ctx.phase == 0;
    }

    /** Checks that every block between the source and landing is traversable. */
    static boolean isFallClear(PathPosition source, int dy, MaterialProvider materials) {
        for (int y = -1; y >= dy; y--) {
            if (!BlockClassifier.isTraversable(materials.getMaterial(
                    source.getFlooredX(), source.getFlooredY() + y, source.getFlooredZ()))) {
                return false;
            }
        }
        return true;
    }
}
