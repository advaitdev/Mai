package me.advait.mai.pathetic.movement.types;

import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import me.advait.mai.pathetic.BlockClassifier;
import me.advait.mai.pathetic.config.MovementConfig;
import me.advait.mai.pathetic.movement.*;
import org.bukkit.Material;

/**
 * Jump up 1 block while moving 1 block horizontally.
 * Requires solid ground at destination, passable at feet+head of destination,
 * and 3-block headroom at source (room to jump).
 */
public record StepUp() implements MovementType {

    @Override
    public String key() { return "step_up"; }

    @Override
    public boolean matches(PathPosition current, PathPosition previous, MaterialProvider materials) {
        int dx = current.getFlooredX() - previous.getFlooredX();
        int dy = current.getFlooredY() - previous.getFlooredY();
        int dz = current.getFlooredZ() - previous.getFlooredZ();

        if (dy != 1) return false;
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        if (horizDist < 0.5 || horizDist > 1.5) return false;

        // Destination must be standable
        if (!WalkFlat.isStandable(current, materials)) return false;

        // Source must have 3 blocks of headroom for jumping (feet, head, above head)
        Material aboveHead = materials.getMaterial(
                previous.getFlooredX(), previous.getFlooredY() + 2, previous.getFlooredZ());
        return BlockClassifier.isTraversable(aboveHead);
    }

    @Override
    public double computeCost(PathPosition current, PathPosition previous,
                              MaterialProvider materials, MovementConfig config) {
        Material below = materials.getMaterial(current.getFlooredX(), current.getFlooredY() - 1, current.getFlooredZ());
        double blockMult = WalkFlat.blockCostMultiplier(below, config);
        return (config.getStepUp() + config.getJumpPenalty()) * blockMult;
    }

    @Override
    public ExecutionHint executionHint(PathPosition current, PathPosition previous) {
        return ExecutionHint.walkJump();
    }
}
