package me.advait.mai.pathetic.movement.types;

import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
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
    public boolean matches(PathPosition current, PathPosition previous, MaterialProvider materials) {
        int dx = current.getFlooredX() - previous.getFlooredX();
        int dy = current.getFlooredY() - previous.getFlooredY();
        int dz = current.getFlooredZ() - previous.getFlooredZ();

        // dy must be -4 or worse, horizontal offset 0-1
        if (dy > -4) return false;
        int fallDist = Math.abs(dy);
        if (fallDist > fallConfig.getMaxFallHeight()) return false;
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        if (horizDist > 1.5) return false;

        // Landing must be standable
        if (!WalkFlat.isStandable(current, materials)) return false;

        // Vertical path must be clear
        return FallSafe.isFallClear(previous, dy, materials);
    }

    @Override
    public double computeCost(PathPosition current, PathPosition previous,
                              MaterialProvider materials, MovementConfig config) {
        int blocks = Math.abs(current.getFlooredY() - previous.getFlooredY());
        int damage = MovementConfig.fallDamage(blocks);
        double fallTime = config.fallCostTicks(blocks);
        return fallTime + damage * config.getFallDamagePenalty();
    }

    @Override
    public ExecutionHint executionHint(PathPosition current, PathPosition previous) {
        return ExecutionHint.walk();
    }
}
