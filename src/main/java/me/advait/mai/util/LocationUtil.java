package me.advait.mai.util;

import me.advait.mai.Settings;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.EnumSet;

public final class LocationUtil {

    private LocationUtil() {}

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
     * Whether a block is within reach (4.5 blocks).
     */
    public static boolean isBlockTargetable(Location standingLocation, Block block) {
        return standingLocation.distance(block.getLocation()) <= 4.5;
    }

    /**
     * Whether the entity is within the configured "near target" distance.
     */
    public static boolean isNearDestination(Location entityLocation, Location destination) {
        return entityLocation.distance(destination) <= Settings.HUMANOID_NEAR_TARGET_DISTANCE;
    }

    private static final EnumSet<Material> BUILDABLE_MATERIALS = EnumSet.of(
            Material.AIR, Material.SHORT_GRASS, Material.TALL_GRASS,
            Material.SEAGRASS, Material.DEAD_BUSH, Material.FERN);

    public static boolean isBuildable(Location location) {
        return BUILDABLE_MATERIALS.contains(location.getBlock().getType());
    }
}
