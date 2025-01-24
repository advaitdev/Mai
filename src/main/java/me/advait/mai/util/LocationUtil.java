package me.advait.mai.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.EnumSet;

public final class LocationUtil {

    /**
     * Determines whether a block is targetable (using Minecraft's reach limit of about 4.5 blocks).
     * @param standingLocation
     * @param block
     * @return
     */
    public static boolean isBlockTargetable(Location standingLocation, Block block) {
        return !(standingLocation.distance(block.getLocation()) > 4.5);
    }

    private static final EnumSet<Material> BUILDABLE_MATERIALS = EnumSet.of(
            Material.AIR, Material.SHORT_GRASS, Material.TALL_GRASS, Material.SEAGRASS, Material.DEAD_BUSH, Material.FERN);

    public static boolean isBuildable(Location location) {
        return BUILDABLE_MATERIALS.contains(location.getBlock().getType());
    }

}
