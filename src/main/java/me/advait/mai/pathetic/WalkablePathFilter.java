package me.advait.mai.pathetic;

import de.metaphoriker.pathetic.api.pathing.filter.PathFilter;
import de.metaphoriker.pathetic.api.pathing.filter.PathValidationContext;
import de.metaphoriker.pathetic.api.provider.NavigationPointProvider;
import de.metaphoriker.pathetic.api.wrapper.PathPosition;
import de.metaphoriker.pathetic.bukkit.provider.BukkitNavigationPoint;

public class WalkablePathFilter implements PathFilter {

    @Override
    public boolean filter(PathValidationContext pathValidationContext) {
        PathPosition above = pathValidationContext.getPosition().add(0, 1, 0);
        NavigationPointProvider navigationPointProvider = pathValidationContext.getNavigationPointProvider();

        var currentNavigationPoint = navigationPointProvider.getNavigationPoint(pathValidationContext.getPosition());
        var aboveNavigationPoint = (BukkitNavigationPoint) navigationPointProvider.getNavigationPoint(above);

        return currentNavigationPoint.isTraversable() && aboveNavigationPoint.isTraversable();
    }
}
