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
     * Makes an entity look at the given location by setting yaw/pitch.
     * Uses setRotation instead of teleport to avoid resetting velocity.
     */
    public static void faceLocation(Entity entity, Location target) {
        Location eye = entity.getLocation();
        if (entity instanceof LivingEntity le) {
            eye = eye.clone().add(0, le.getEyeHeight(), 0);
        }

        double dx = target.getX() + 0.5 - eye.getX();
        double dy = target.getY() + 0.5 - eye.getY();
        double dz = target.getZ() + 0.5 - eye.getZ();

        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, horizontalDist));

        entity.setRotation(yaw, pitch);
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
