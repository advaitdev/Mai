package me.advait.patheticcitizens;

import co.aikar.commands.PaperCommandManager;
import me.advait.patheticcitizens.command.PNPCCommand;
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

        registerCommands();
    }

    @Override
    public void onDisable() {

        INSTANCE = null;

    }

    private void registerCommands() {
        PaperCommandManager pm = new PaperCommandManager(this);
        pm.registerCommand(new PNPCCommand());
    }

    public static PatheticCitizens getInstance() {
        return INSTANCE;
    }

}

