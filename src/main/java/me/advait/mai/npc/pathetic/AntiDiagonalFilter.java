package me.advait.mai.npc.pathetic;

import de.metaphoriker.pathetic.api.pathing.filter.PathFilter;
import de.metaphoriker.pathetic.api.pathing.filter.PathValidationContext;
import de.metaphoriker.pathetic.api.provider.NavigationPointProvider;
import de.metaphoriker.pathetic.api.wrapper.PathPosition;
import de.metaphoriker.pathetic.bukkit.provider.BukkitNavigationPoint;
import org.jetbrains.annotations.NotNull;

public class AntiDiagonalFilter implements PathFilter {

    @Override
    public boolean filter(@NotNull PathValidationContext pathValidationContext) {
        PathPosition parent = pathValidationContext.getParent();
        PathPosition current = pathValidationContext.getPosition();

        NavigationPointProvider navigationPointProvider = pathValidationContext.getNavigationPointProvider();

        var parentNavigationPoint = (BukkitNavigationPoint) navigationPointProvider.getNavigationPoint(parent);
        var currentNavigationPoint = (BukkitNavigationPoint) navigationPointProvider.getNavigationPoint(current);

        int parentX = parentNavigationPoint.getBlockState().getBlock().getX();
        int parentZ = parentNavigationPoint.getBlockState().getBlock().getZ();
        int currentX = currentNavigationPoint.getBlockState().getBlock().getX();
        int currentZ = currentNavigationPoint.getBlockState().getBlock().getZ();

        // If x and z are both different, we went diagonally; the path isn't valid.
        return parentX == currentX || parentZ == currentZ;
    }

}
