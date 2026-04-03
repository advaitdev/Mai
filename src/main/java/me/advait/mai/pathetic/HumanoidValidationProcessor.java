package me.advait.mai.pathetic;

import de.bsommerfeld.pathetic.api.pathing.processing.ValidationProcessor;
import de.bsommerfeld.pathetic.api.pathing.processing.context.EvaluationContext;
import de.bsommerfeld.pathetic.api.provider.NavigationPoint;
import de.bsommerfeld.pathetic.api.provider.NavigationPointProvider;
import de.bsommerfeld.pathetic.api.pathing.context.EnvironmentContext;
import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import de.bsommerfeld.pathetic.bukkit.provider.BukkitNavigationPoint;
import org.bukkit.Material;

/**
 * Validates that a position is walkable for a 2-block-tall humanoid.
 *
 * Uses the thread-safe NavigationPointProvider (chunk snapshots) for async
 * pathfinding. Checks material-based traversability that correctly handles
 * blocks like leaves which have collision but aren't "solid" per Material API.
 */
public class HumanoidValidationProcessor implements ValidationProcessor {

    @Override
    public boolean isValid(EvaluationContext context) {
        PathPosition pos = context.getCurrentPathPosition();
        NavigationPointProvider provider = context.getNavigationPointProvider();
        EnvironmentContext env = context.getEnvironmentContext();

        Material below = getMaterial(provider, pos.add(0, -1, 0), env);
        Material atFeet = getMaterial(provider, pos, env);
        Material atHead = getMaterial(provider, pos.add(0, 1, 0), env);

        return !isTraversable(below)  // solid ground to stand on
            && isTraversable(atFeet)  // can walk through at feet
            && isTraversable(atHead); // can walk through at head
    }

    private Material getMaterial(NavigationPointProvider provider, PathPosition pos, EnvironmentContext env) {
        NavigationPoint point = provider.getNavigationPoint(pos, env);
        if (point instanceof BukkitNavigationPoint bukkit) {
            return bukkit.getMaterial();
        }
        return Material.AIR;
    }

    /**
     * Whether an entity can pass through a block of this material.
     * Material.isSolid() misses blocks like leaves that have collision
     * but aren't classified as "solid". We explicitly reject those.
     */
    private static boolean isTraversable(Material material) {
        if (material.isAir()) return true;
        if (material.isSolid()) return false;
        // Non-solid blocks that still have collision boxes
        if (material.name().endsWith("_LEAVES")) return false;
        if (material == Material.COBWEB) return false;
        if (material == Material.POWDER_SNOW) return false;
        if (material == Material.SWEET_BERRY_BUSH) return false;
        return true;
    }
}
