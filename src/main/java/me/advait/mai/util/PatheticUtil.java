package me.advait.mai.util;

import de.metaphoriker.pathetic.api.pathing.result.Path;
import de.metaphoriker.pathetic.api.provider.NavigationPointProvider;
import de.metaphoriker.pathetic.api.wrapper.PathPosition;
import de.metaphoriker.pathetic.bukkit.provider.BukkitNavigationPoint;
import de.metaphoriker.pathetic.bukkit.provider.LoadingNavigationPointProvider;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.Iterator;

public final class PatheticUtil {

    /**
     * Determines if the second parameter passed is a "subpath" of the first; meaning, all of the positions in the shorter
     * path exist in the longer path.
     *
     * @param longer The longer path.
     * @param shorter The shorter path.
     */
    public static boolean isSubpathEquivalent(Path longer, Path shorter) {
        int lengthDifference = longer.length() - shorter.length();
        if (lengthDifference < 0) return false; // If the "shorter" path is somehow longer, the actual path was 100% recalculated

        // Iterate over the paths, starting from the trimmed position in the longer path
        Iterator<PathPosition> longerIterator = longer.iterator();
        Iterator<PathPosition> shorterIterator = shorter.iterator();

        // Skip the first "lengthDifference" positions of the longer path
        for (int i = 0; i < lengthDifference; i++) {
            if (longerIterator.hasNext()) {
                longerIterator.next();
            } else {
                return false;
            }
        }

        while (shorterIterator.hasNext() && longerIterator.hasNext()) {
            if (!longerIterator.next().equals(shorterIterator.next())) {
                return false;
            }
        }

        // If we exhaust both iterators without mismatch, the paths are equivalent
        return !shorterIterator.hasNext() && !longerIterator.hasNext();
    }

    public static boolean isTraversable(Path path) {
        NavigationPointProvider navigationPointProvider = new LoadingNavigationPointProvider();

        for (PathPosition pathPosition : path)
            if (!navigationPointProvider.getNavigationPoint(pathPosition).isTraversable()) return false;

        return true;
    }

    public static boolean isSurroundedByAir(PathPosition pathPosition) {
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


}
