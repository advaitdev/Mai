package me.advait.mai.pathetic.movement.types;

import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import me.advait.mai.pathetic.PathContext;
import me.advait.mai.pathetic.capabilities.HumanoidCapabilities;
import me.advait.mai.pathetic.config.MovementConfig;
import me.advait.mai.pathetic.movement.*;

/**
 * Fall more than 3 blocks, taking damage. Cost includes fall time
 * plus a penalty proportional to the damage taken.
 * Only enabled when config.allowUnsafeFalls is true.
 */
public record FallUnsafe(MovementConfig fallConfig) implements MovementType {

    @Override
    public String key() { return "fall_unsafe"; }

    @Override
    public boolean matches(PathPosition current, PathPosition previous, PathContext ctx) {
        int dx = current.getFlooredX() - previous.getFlooredX();
        int dy = current.getFlooredY() - previous.getFlooredY();
        int dz = current.getFlooredZ() - previous.getFlooredZ();

        // dy must be -4 or worse, horizontal offset exactly 1
        if (dy > -4) return false;
        int fallDist = Math.abs(dy);
        if (fallDist > fallConfig.getMaxFallHeight()) return false;
        if (Math.abs(dx) > 1 || Math.abs(dz) > 1) return false;
        if (dx == 0 && dz == 0) return false;

        // Landing must be standable
        if (!WalkFlat.isStandable(current, ctx.materials())) return false;

        // Destination column must be open from source level down to landing
        return FallSafe.isFallClear(previous, current, ctx.materials());
    }

    @Override
    public double computeCost(PathPosition current, PathPosition previous, PathContext ctx) {
        int blocks = Math.abs(current.getFlooredY() - previous.getFlooredY());
        int damage = MovementConfig.fallDamage(blocks);
        double fallTime = ctx.config().fallCostTicks(blocks);
        return fallTime + damage * ctx.config().getFallDamagePenalty();
    }

    @Override
    public ExecutionHint executionHint(PathPosition current, PathPosition previous) {
        return ExecutionHint.walk();
    }

    @Override
    public boolean isAllowed(HumanoidCapabilities caps) {
        // Unsafe falls are gated on how far the bot is willing to fall.
        // Once we track bot health, this can also consider survivable damage.
        return caps.maxFallHeight() >= 4;
    }

    @Override
    public boolean canReachAsEndpoint(PathPosition position, PathContext ctx) {
        return WalkFlat.isStandable(position, ctx.materials());
    }

    @Override
    public MovementStatus tick(TickContext ctx) {
        // Same executor as FallSafe — gravity does the work, we just steer.
        return FallSafe.tickFall(ctx);
    }

    @Override
    public boolean safeToCancel(TickContext ctx) {
        return ctx.phase == 0;
    }
}
