package me.advait.mai.pathetic;

import de.bsommerfeld.pathetic.api.pathing.processing.ValidationProcessor;
import de.bsommerfeld.pathetic.api.pathing.processing.context.EvaluationContext;
import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import de.bsommerfeld.pathetic.bukkit.context.BukkitEnvironmentContext;
import de.bsommerfeld.pathetic.bukkit.mapper.BukkitMapper;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Validates that a position is walkable for a 2-block-tall humanoid.
 *
 * Uses Block.isPassable() instead of Material.isSolid() — this checks actual
 * collision boxes, so blocks like leaves (which have collision but aren't "solid"
 * per the Material API) are correctly treated as obstacles.
 */
public class HumanoidValidationProcessor implements ValidationProcessor {

    @Override
    public boolean isValid(EvaluationContext context) {
        PathPosition pos = context.getCurrentPathPosition();
        if (!(context.getEnvironmentContext() instanceof BukkitEnvironmentContext bc)) return true;
        World world = bc.getWorld();
        if (world == null) return true;

        Block below = BukkitMapper.toLocation(pos.add(0, -1, 0), world).getBlock();
        Block atFeet = BukkitMapper.toLocation(pos, world).getBlock();
        Block atHead = BukkitMapper.toLocation(pos.add(0, 1, 0), world).getBlock();

        return !below.isPassable()  // solid ground to stand on
            && atFeet.isPassable()  // can exist at feet level
            && atHead.isPassable(); // can exist at head level
    }
}
