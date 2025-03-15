package me.advait.patheticcitizens.npc;

import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PatheticNPCRegistry {

    private PatheticNPCRegistry() {}

    private static final Map<NPC, PatheticNPC> npcMap = new ConcurrentHashMap<>();

    public static void register(PatheticNPC patheticNPC) {
        npcMap.put(patheticNPC.getCitizensNPC(), patheticNPC);
        Bukkit.getLogger().info("Registered Pathetic NPC: " + patheticNPC.getName());
    }

    public static void unregister(PatheticNPC patheticNPC) {
        npcMap.remove(patheticNPC.getCitizensNPC());
        Bukkit.getLogger().info("Unregistered Pathetic NPC: " + patheticNPC.getName());
    }

    public static PatheticNPC getPatheticNPC(NPC npc) {
        return npcMap.get(npc);
    }

    public static boolean isRegistered(NPC npc) {
        return npcMap.containsKey(npc);
    }

}
