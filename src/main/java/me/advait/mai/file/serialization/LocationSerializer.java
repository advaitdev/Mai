package me.advait.mai.file.serialization;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Serializes and deserializes Bukkit locations to/from configuration sections.
 */
public final class LocationSerializer {

    private LocationSerializer() {}

    /**
     * Serializes a location to a configuration section.
     *
     * @param section the section to write to
     * @param location the location to serialize
     */
    public static void serialize(ConfigurationSection section, Location location) {
        if (location == null) {
            return;
        }

        section.set("world", location.getWorld() != null ? location.getWorld().getName() : null);
        section.set("x", location.getX());
        section.set("y", location.getY());
        section.set("z", location.getZ());
        section.set("yaw", location.getYaw());
        section.set("pitch", location.getPitch());
    }

    /**
     * Deserializes a location from a configuration section.
     *
     * @param section the section to read from
     * @return the deserialized location, or null if invalid
     */
    public static Location deserialize(ConfigurationSection section) {
        if (section == null) {
            return null;
        }

        String worldName = section.getString("world");
        if (worldName == null) {
            return null;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            // World not loaded yet, return null
            return null;
        }

        double x = section.getDouble("x");
        double y = section.getDouble("y");
        double z = section.getDouble("z");
        float yaw = (float) section.getDouble("yaw");
        float pitch = (float) section.getDouble("pitch");

        return new Location(world, x, y, z, yaw, pitch);
    }

    /**
     * Converts a location to a human-readable string.
     *
     * @param location the location
     * @return formatted string like "world: 100, 64, 200"
     */
    public static String toReadableString(Location location) {
        if (location == null) {
            return "Unknown";
        }
        String worldName = location.getWorld() != null ? location.getWorld().getName() : "?";
        return String.format("%s: %.0f, %.0f, %.0f", worldName, location.getX(), location.getY(), location.getZ());
    }
}
