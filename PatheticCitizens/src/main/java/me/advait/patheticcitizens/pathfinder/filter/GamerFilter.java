package me.advait.patheticcitizens.pathfinder.filter;

import de.metaphoriker.pathetic.api.pathing.filter.PathFilter;
import de.metaphoriker.pathetic.api.pathing.filter.PathValidationContext;
import de.metaphoriker.pathetic.api.wrapper.PathPosition;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

/**
 * Gamer filter (doesn't touch grass; for testing).
 */
public class GamerFilter implements PathFilter {

    @Override
    public boolean filter(PathValidationContext pathValidationContext) {
        PathPosition current = pathValidationContext.getPosition();

        return !isSurroundedByGrass(current);
    }

    private boolean isSurroundedByGrass(PathPosition pathPosition) {
        World world = Bukkit.getWorld(pathPosition.getPathEnvironment().getName());

        Location current = new Location(world, pathPosition.getX(), pathPosition.getY(), pathPosition.getZ());
        Material below = current.clone().add(0, 1, 0).getBlock().getType();
        Material above = current.clone().add(0, -1, 0).getBlock().getType();
        Material side1 = current.clone().add(1, 0, 0).getBlock().getType();
        Material side2 = current.clone().add(-1, 0, 0).getBlock().getType();
        Material side3 = current.clone().add(0, 0, 1).getBlock().getType();
        Material side4 = current.clone().add(0, 0, -1).getBlock().getType();

        return !(below == Material.GRASS_BLOCK ||
                above == Material.GRASS_BLOCK ||
                side1 == Material.GRASS_BLOCK ||
                side2 == Material.GRASS_BLOCK ||
                side3 == Material.GRASS_BLOCK ||
                side4 == Material.GRASS_BLOCK);
    }

}
