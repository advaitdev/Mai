package me.advait.mai.pathetic.movement.types;

import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import me.advait.mai.pathetic.config.MovementConfig;
import me.advait.mai.pathetic.movement.*;

/**
 * Step down 1 block: dy=-1 with horizontal distance 0-1.
 * Walk off the edge of a block and land 1 block below. No damage.
 */
public record StepDown() implements MovementType {

    @Override
    public String key() { return "step_down"; }

    @Override
    public boolean matches(PathPosition current, PathPosition previous, MaterialProvider materials) {
        int dx = current.getFlooredX() - previous.getFlooredX();
        int dy = current.getFlooredY() - previous.getFlooredY();
        int dz = current.getFlooredZ() - previous.getFlooredZ();

        if (dy != -1) return false;
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        if (horizDist > 1.5) return false;

        return WalkFlat.isStandable(current, materials);
    }

    @Override
    public double computeCost(PathPosition current, PathPosition previous,
                              MaterialProvider materials, MovementConfig config) {
        return config.getStepDown();
    }

    @Override
    public ExecutionHint executionHint(PathPosition current, PathPosition previous) {
        return ExecutionHint.walk();
    }
}
