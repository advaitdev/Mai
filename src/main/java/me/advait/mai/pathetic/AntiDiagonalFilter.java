package me.advait.mai.pathetic;

import de.metaphoriker.pathetic.api.pathing.filter.PathFilter;
import de.metaphoriker.pathetic.api.pathing.filter.PathValidationContext;
import de.metaphoriker.pathetic.api.provider.NavigationPointProvider;
import de.metaphoriker.pathetic.api.wrapper.PathPosition;
import de.metaphoriker.pathetic.bukkit.provider.BukkitNavigationPoint;
import me.advait.mai.monitor.Monitor;
import me.advait.mai.util.PatheticUtil;
import org.jetbrains.annotations.NotNull;

public class AntiDiagonalFilter implements PathFilter {

    @Override
    public boolean filter(@NotNull PathValidationContext pathValidationContext) {
        PathPosition parent = pathValidationContext.getParent();
        PathPosition current = pathValidationContext.getPosition();

        return (parent.getX() == current.getX() || parent.getZ() == current.getZ()) && !PatheticUtil.isSurroundedByAir(current);

//        var parentNavigationPoint = (BukkitNavigationPoint) navigationPointProvider.getNavigationPoint(parent);
//        var currentNavigationPoint = (BukkitNavigationPoint) navigationPointProvider.getNavigationPoint(current);
//
//        int parentX = parentNavigationPoint.getBlockState().getX();
//        int parentZ = parentNavigationPoint.getBlockState().getZ();
//        int currentX = currentNavigationPoint.getBlockState().getX();
//        int currentZ = currentNavigationPoint.getBlockState().getZ();
//
//        Monitor.log("parentNavigationPoint: " + parentNavigationPoint.getBlockState().getBlockData().getAsString() +
//                ", currentNavigationPoint: " + currentNavigationPoint.getBlockState().getBlockData().getAsString());
//        Monitor.log("parentX: " + parentX + ", currentX: " + currentX + ", parentZ: " + parentZ + ", currentZ: " + currentZ);
//
//        // If x and z are both different, we went diagonally; the path isn't valid.
//        return parentX == currentX || parentZ == currentZ;
    }

}
