package me.advait.mai.util;

import me.advait.mai.Settings;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;

public final class NPCUtil {

    /**
     * @param npc The Citizens NPC of interest.
     * @param destination The Bukkit Location of interest.
     * @return If the NPC is "near" the target destination based on the "HUMANOID_NEAR_TARGET_DISTANCE" option in settings.yml.
     */
    public static boolean isNPCNearDestination(NPC npc, Location destination) {
        return npc.getStoredLocation().distance(destination) <= Settings.HUMANOID_NEAR_TARGET_DISTANCE;
    }



}
