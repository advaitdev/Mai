package me.advait.patheticcitizens.npc;

import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PatheticNPCRegistry {

    public static final PatheticNPCRegistry INSTANCE = new PatheticNPCRegistry();

    public static PatheticNPCRegistry getInstance() {
        return INSTANCE;
    }

    private final Map<UUID /* Minecraft-provided UUID */, PatheticNPC> npcMap = new HashMap<>();

    public void register(PatheticNPC patheticNPC) {
        npcMap.put(patheticNPC.getCitizensNPC().getMinecraftUniqueId(), patheticNPC);
        Bukkit.getLogger().info("Registered Pathetic NPC: " + patheticNPC.getName()
                + " (" + patheticNPC.getMinecraftUUID() + ")");
    }

    public void unregister(PatheticNPC patheticNPC) {
        npcMap.remove(patheticNPC.getCitizensNPC().getMinecraftUniqueId());
        Bukkit.getLogger().info("Unregistered Pathetic NPC: " + patheticNPC.getName()
                + " (" + patheticNPC.getMinecraftUUID() + ")");
    }

    public PatheticNPC getPatheticNPC(NPC npc) {
        return npcMap.get(npc.getUniqueId());
    }

    public boolean isRegistered(NPC npc) {
        return npcMap.containsKey(npc.getUniqueId());
    }

}
