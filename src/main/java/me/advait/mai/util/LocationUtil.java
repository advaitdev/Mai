package me.advait.mai.util;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

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

}
