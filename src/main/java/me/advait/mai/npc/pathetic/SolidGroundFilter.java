package me.advait.mai.npc.pathetic;

import de.metaphoriker.pathetic.api.pathing.filter.PathFilter;
import de.metaphoriker.pathetic.api.pathing.filter.PathValidationContext;
import de.metaphoriker.pathetic.api.provider.NavigationPointProvider;
import de.metaphoriker.pathetic.api.wrapper.PathPosition;
import de.metaphoriker.pathetic.bukkit.mapper.BukkitMapper;
import de.metaphoriker.pathetic.bukkit.provider.BukkitNavigationPoint;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

public class SolidGroundFilter implements PathFilter {

    @Override
    public boolean filter(PathValidationContext pathValidationContext) {
        PathPosition below = pathValidationContext.getPosition().subtract(0, 1, 0);
        NavigationPointProvider navigationPointProvider = pathValidationContext.getNavigationPointProvider();
        var currentNavigationPoint = (BukkitNavigationPoint) navigationPointProvider.getNavigationPoint(below);

        return currentNavigationPoint.getMaterial().isSolid();
    }

}
