package me.advait.mai.util;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public final class LocationUtil {

    public static Block getBlockInFrontAtEyeLevel(Player player, double distance) {
        return player.getLocation().add(player.getLocation().getDirection().multiply(distance)).getBlock();
    }

}
