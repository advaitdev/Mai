package me.advait.patheticcitizens;

import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.npc.CitizensNPC;
import net.citizensnpcs.npc.ai.AStarNavigationStrategy;
import net.citizensnpcs.npc.ai.CitizensNavigator;
import org.bukkit.plugin.java.JavaPlugin;

public final class PatheticCitizens extends JavaPlugin {

    private static PatheticCitizens INSTANCE = null;

    @Override
    public void onEnable() {
        // Plugin startup logic

        INSTANCE = this;

        NPC npc;
        CitizensNPC citizensNPC;

    }

    @Override
    public void onDisable() {

        INSTANCE = null;

    }

    public static PatheticCitizens getInstance() {
        return INSTANCE;
    }

}

