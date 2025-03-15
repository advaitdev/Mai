package me.advait.patheticcitizens.npc;

import net.citizensnpcs.api.npc.NPC;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PatheticNPCRegistry {

    private PatheticNPCRegistry() {}

    private static final Map<NPC, PatheticNPC> npcMap = new ConcurrentHashMap<>();

    public static void register(PatheticNPC patheticNPC) {
        npcMap.put(patheticNPC.getCitizensNPC(), patheticNPC);
    }

    public static void unregister(PatheticNPC patheticNPC) {
        npcMap.remove(patheticNPC.getCitizensNPC());
    }

    public static PatheticNPC getPatheticNPC(NPC npc) {
        return npcMap.get(npc);
    }

    public static boolean isRegistered(NPC npc) {
        return npcMap.containsKey(npc);
    }

}
