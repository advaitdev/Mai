package me.advait.mai.pathetic;

import de.metaphoriker.pathetic.api.pathing.filter.PathFilter;
import de.metaphoriker.pathetic.api.pathing.filter.PathValidationContext;
import de.metaphoriker.pathetic.api.wrapper.PathPosition;
import de.metaphoriker.pathetic.bukkit.provider.BukkitNavigationPoint;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

/**
 *
 */
public class BridgeRealismFilter implements PathFilter {

    @Override
    public boolean filter(@NotNull PathValidationContext pathValidationContext) {
        PathPosition parent = pathValidationContext.getParent();
        PathPosition current = pathValidationContext.getPosition();

        if (!isPlayerTraversable(current, pathValidationContext)) return false;

        // If the y-level hasn't changed, simply check that we aren't bridging diagonally.
        if (parent.getY() == current.getY()) {
            return parent.getX() == current.getX() || parent.getZ() == current.getZ();
        }

        // If we are bridging "downwards," the only way this is possible is if there's already a solid block placed next to the current location.
        else if (current.getY() < parent.getY()) {
            return (parent.getX() == current.getX() || parent.getZ() == current.getZ()) && !isSurroundedByAir(current);
        }

        // If we are bridging "upwards," just check to make sure that our x and z haven't changed. If they have, we're bridging diagonally, which is impossible.
        else {
            return parent.getX() == current.getX() && parent.getZ() == current.getZ();
        }

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

    private boolean isPlayerTraversable(PathPosition pathPosition, PathValidationContext pathValidationContext) {
        var navigationPointCurrent = (BukkitNavigationPoint) pathValidationContext.getNavigationPointProvider().getNavigationPoint(pathPosition);
        var navigationPointAbove = (BukkitNavigationPoint) pathValidationContext.getNavigationPointProvider().getNavigationPoint(pathPosition.clone().add(0, 1, 0));

        return navigationPointCurrent.isTraversable() && navigationPointAbove.isTraversable();
    }

}
