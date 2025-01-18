package me.advait.mai.util;

import me.advait.mai.Settings;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;

public final class NPCUtil {

    public static boolean isNPCNearDestination(NPC npc, Location destination) {
        return npc.getStoredLocation().distance(destination) <= Settings.HUMANOID_NEAR_TARGET_DISTANCE;
    }

}
