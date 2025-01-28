package me.advait.mai.pathetic;

import de.metaphoriker.pathetic.api.pathing.filter.PathFilter;
import de.metaphoriker.pathetic.api.pathing.filter.PathValidationContext;
import de.metaphoriker.pathetic.api.wrapper.PathPosition;
import de.metaphoriker.pathetic.bukkit.provider.BukkitNavigationPoint;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;

/**
 * A PathFilter implementation that only finds "player-possible" paths (i.e. no diagonal blocks when tunneling, no building straight down, etc.).
 * This should be used in plugins where Pathetic acts as a substitute for an entity-realistic pathfinder, such as Citizens.
 */

public class NavigationRealismFilter implements PathFilter {

    @Override
    public boolean filter(@NotNull PathValidationContext pathValidationContext) {
        PathPosition parent = pathValidationContext.getParent();
        PathPosition current = pathValidationContext.getPosition();

        if (!isPlayerMineable(current)) return false;

        Block currentBlock = getBukkitBlock(current);

        boolean underground = !currentBlock.isPassable() && !currentBlock.getRelative(0, 1, 0).isPassable();

        if (underground) {

            // If the y-level hasn't changed, simply check that we aren't mining diagonally.
            if (parent.getY() == current.getY()) {
                return parent.getX() == current.getX() || parent.getZ() == current.getZ();
            }

            // If the y-level has changed, both our x and z cannot change, or else we are going diagonally (think of a "staircase" path when mining).
            else {
                return parent.getX() == current.getX() && parent.getZ() == current.getZ();
            }
        }

        else {
            // If the y-level hasn't changed, simply check that we aren't bridging diagonally.
            if (parent.getY() == current.getY()) {
                return parent.getX() == current.getX() || parent.getZ() == current.getZ();
            }

            // If we are bridging "downwards," the only way this is possible is if there's already a solid block placed next to the current location.
            else if (current.getY() < parent.getY()) {
                return (parent.getX() != current.getX() || parent.getZ() != current.getZ()) && !isSurroundedByAir(current);
            }

            // If we are bridging "upwards," just check to make sure that our x and z haven't changed. If they have, we're bridging diagonally, which is impossible.
            else {
                return parent.getX() == current.getX() && parent.getZ() == current.getZ();
            }
        }

    }

    private Block getBukkitBlock(PathPosition pathPosition) {
        World world = Bukkit.getWorld(pathPosition.getPathEnvironment().getName());
        return world.getBlockAt(new Location(world, pathPosition.getX(), pathPosition.getY(), pathPosition.getZ()));
    }

    private boolean isSurroundedByAir(PathPosition pathPosition) {
        World world = Bukkit.getWorld(pathPosition.getPathEnvironment().getName());

        Location current = new Location(world, pathPosition.getX(), pathPosition.getY(), pathPosition.getZ());
        Material below = current.clone().add(0, 1, 0).getBlock().getType();
        Material above = current.clone().add(0, -1, 0).getBlock().getType();
        Material side1 = current.clone().add(1, 0, 0).getBlock().getType();
        Material side2 = current.clone().add(-1, 0, 0).getBlock().getType();
        Material side3 = current.clone().add(0, 0, 1).getBlock().getType();
        Material side4 = current.clone().add(0, 0, -1).getBlock().getType();

        return !(below.isSolid() ||
                above.isSolid() ||
                side1.isSolid() ||
                side2.isSolid() ||
                side3.isSolid() ||
                side4.isSolid());
    }

    private boolean isPlayerMineable(PathPosition pathPosition) {
        Block currentBlock = getBukkitBlock(pathPosition);
        Block aboveBlock = currentBlock.getRelative(0, 1, 0);

        return !(currentBlock.getType().getHardness() == -1 || aboveBlock.getType().getHardness() == -1);
    }

}
