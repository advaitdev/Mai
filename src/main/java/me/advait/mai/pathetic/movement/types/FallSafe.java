package me.advait.mai.pathetic.movement.types;

import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import me.advait.mai.pathetic.BlockClassifier;
import me.advait.mai.pathetic.capabilities.HumanoidCapabilities;
import me.advait.mai.pathetic.config.MovementConfig;
import me.advait.mai.pathetic.movement.*;

/**
 * Fall 2-3 blocks (no damage). Landing must be standable,
 * and the vertical path must be clear.
 */
public record FallSafe() implements MovementType {

    @Override
    public String key() { return "fall_safe"; }

    @Override
    public boolean matches(PathPosition current, PathPosition previous, MaterialProvider materials) {
        int dx = current.getFlooredX() - previous.getFlooredX();
        int dy = current.getFlooredY() - previous.getFlooredY();
        int dz = current.getFlooredZ() - previous.getFlooredZ();

        // dy must be -2 or -3, horizontal offset 0-1
        if (dy > -2 || dy < -3) return false;
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        if (horizDist > 1.5) return false;

        // Landing must be standable
        if (!WalkFlat.isStandable(current, materials)) return false;

        // Vertical path must be clear (check each Y level between prev and current)
        return isFallClear(previous, dy, materials);
    }

    @Override
    public double computeCost(PathPosition current, PathPosition previous,
                              MaterialProvider materials, MovementConfig config) {
        int blocks = Math.abs(current.getFlooredY() - previous.getFlooredY());
        return config.getFallSafeBase() + blocks * config.getFallPerBlock();
    }

    @Override
    public ExecutionHint executionHint(PathPosition current, PathPosition previous) {
        return ExecutionHint.walk();
    }

    @Override
    public boolean canReachAsEndpoint(PathPosition position, HumanoidCapabilities caps,
                                      MaterialProvider materials) {
        return WalkFlat.isStandable(position, materials);
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
