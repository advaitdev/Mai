package me.advait.mai.util;

import me.advait.mai.Settings;
import org.bukkit.Location;

public final class NPCUtil {

    /**
     * @param entityLocation Current location of the entity (e.g. mannequin).
     * @param destination    Target destination.
     * @return If the entity is "near" the target based on "HUMANOID_NEAR_TARGET_DISTANCE" in settings.yml.
     */
    public static boolean isEntityNearDestination(Location entityLocation, Location destination) {
        return entityLocation.distance(destination) <= Settings.HUMANOID_NEAR_TARGET_DISTANCE;
    }

}
