package me.advait.mai.pathetic.movement.types;

import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import me.advait.mai.pathetic.BlockClassifier;
import me.advait.mai.pathetic.capabilities.HumanoidCapabilities;
import me.advait.mai.pathetic.config.MovementConfig;
import me.advait.mai.pathetic.movement.*;
import org.bukkit.Material;

/**
 * Climbing a ladder or vine. Can go up, down, or stay level.
 * Current position must have a climbable block at feet level.
 */
public record LadderClimb() implements MovementType {

    @Override
    public String key() { return "ladder_climb"; }

    @Override
    public boolean matches(PathPosition current, PathPosition previous, MaterialProvider materials) {
        Material atFeet = materials.getMaterial(current);
        if (!BlockClassifier.isClimbable(atFeet)) return false;

        // Must have passable head space
        Material atHead = materials.getMaterial(
                current.getFlooredX(), current.getFlooredY() + 1, current.getFlooredZ());
        return BlockClassifier.isTraversable(atHead);
    }

    @Override
    public double computeCost(PathPosition current, PathPosition previous,
                              MaterialProvider materials, MovementConfig config) {
        int dy = current.getFlooredY() - previous.getFlooredY();
        return dy >= 0 ? config.getLadderUp() : config.getLadderDown();
    }

    @Override
    public ExecutionHint executionHint(PathPosition current, PathPosition previous) {
        return ExecutionHint.climb();
    }

    @Override
    public boolean isAllowed(HumanoidCapabilities caps) {
        return caps.canClimb();
    }

    @Override
    public boolean canReachAsEndpoint(PathPosition position, HumanoidCapabilities caps,
                                      MaterialProvider materials) {
        // Any position whose feet block is a ladder/vine is a valid
        // landing for a climb, regardless of what's below it.
        Material atFeet = materials.getMaterial(position);
        if (!BlockClassifier.isClimbable(atFeet)) return false;
        Material atHead = materials.getMaterial(
                position.getFlooredX(), position.getFlooredY() + 1, position.getFlooredZ());
        return BlockClassifier.isTraversable(atHead);
    }
}
