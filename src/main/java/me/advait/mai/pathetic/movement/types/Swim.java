package me.advait.mai.pathetic.movement.types;

import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import me.advait.mai.pathetic.BlockClassifier;
import me.advait.mai.pathetic.capabilities.HumanoidCapabilities;
import me.advait.mai.pathetic.config.MovementConfig;
import me.advait.mai.pathetic.movement.*;
import org.bukkit.Material;

/**
 * Swimming through water or lava. Current position's feet block is liquid.
 */
public record Swim() implements MovementType {

    @Override
    public String key() { return "swim"; }

    @Override
    public boolean matches(PathPosition current, PathPosition previous, MaterialProvider materials) {
        Material atFeet = materials.getMaterial(current);
        return BlockClassifier.isLiquid(atFeet);
    }

    @Override
    public double computeCost(PathPosition current, PathPosition previous,
                              MaterialProvider materials, MovementConfig config) {
        Material atFeet = materials.getMaterial(current);
        double mult = BlockClassifier.isLava(atFeet) ? config.getLavaMultiplier() : 1.0;
        return config.getSwim() * mult;
    }

    @Override
    public ExecutionHint executionHint(PathPosition current, PathPosition previous) {
        return ExecutionHint.swim();
    }

    @Override
    public boolean isAllowed(HumanoidCapabilities caps) {
        return caps.canSwim();
    }

    @Override
    public boolean canReachAsEndpoint(PathPosition position, HumanoidCapabilities caps,
                                      MaterialProvider materials) {
        // Any position whose feet block is a liquid is a valid swim endpoint.
        Material atFeet = materials.getMaterial(position);
        return BlockClassifier.isLiquid(atFeet);
    }
}
