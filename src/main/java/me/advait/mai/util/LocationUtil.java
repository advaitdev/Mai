package me.advait.mai.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.EnumSet;

public final class LocationUtil {

    /**
     * Makes an entity look at the given location (sets yaw/pitch).
     */
    public static void faceLocation(Entity entity, Location target) {
        Location loc = entity.getLocation();
        Vector dir = target.clone().add(0.5, 0.5, 0.5)
                .subtract(entity instanceof LivingEntity le
                        ? loc.clone().add(0, le.getEyeHeight(), 0)
                        : loc.clone())
                .toVector();
        if (dir.lengthSquared() > 0) {
            loc.setDirection(dir);
            entity.teleport(loc);
        }
    }

    /**
     * Determines whether a block is targetable (using Minecraft's reach limit of about 4.5 blocks).
     * @param standingLocation
     * @param block
     * @return
     */
    public static boolean isBlockTargetable(Location standingLocation, Block block) {
        return !(standingLocation.distance(block.getLocation()) > 4.5);
    }

    public static boolean canSeeLocation(LivingEntity entity, Location location) {
        return entity.getLineOfSight(null, 10).contains(location.getBlock());
    }

    private static final EnumSet<Material> BUILDABLE_MATERIALS = EnumSet.of(
            Material.AIR, Material.SHORT_GRASS, Material.TALL_GRASS, Material.SEAGRASS, Material.DEAD_BUSH, Material.FERN);

    public static boolean isBuildable(Location location) {
        return BUILDABLE_MATERIALS.contains(location.getBlock().getType());
    }

}
