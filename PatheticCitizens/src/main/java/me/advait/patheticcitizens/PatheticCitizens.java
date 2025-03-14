package me.advait.patheticcitizens;

import co.aikar.commands.PaperCommandManager;
import de.metaphoriker.pathetic.bukkit.PatheticBukkit;
import de.metaphoriker.pathetic.engine.Pathetic;
import me.advait.patheticcitizens.command.PNPCCommand;
import me.advait.patheticcitizens.command.PatheticTestCommand;
import me.advait.patheticcitizens.npc.trait.SprintJumpTrait;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.TraitInfo;
import net.citizensnpcs.npc.CitizensNPC;
import net.citizensnpcs.npc.ai.AStarNavigationStrategy;
import net.citizensnpcs.npc.ai.CitizensNavigator;
import org.bukkit.plugin.java.JavaPlugin;

public final class PatheticCitizens extends JavaPlugin {

    private static PatheticCitizens INSTANCE = null;

    @Override
    public void onEnable() {
        // Plugin startup logic

        getLogger().info("Pathetic version:" + Pathetic.getEngineVersion());

        INSTANCE = this;

        registerCommands();
        initializePathetic();
        initializeCitizensTraits();
    }

    @Override
    public void onDisable() {

        INSTANCE = null;

    }

    private void registerCommands() {
        PaperCommandManager pm = new PaperCommandManager(this);

        pm.registerCommand(new PatheticTestCommand());
        pm.registerCommand(new PNPCCommand());
    }

    private void initializePathetic() {
        PatheticBukkit.initialize(this);
    }

    private void initializeCitizensTraits() {
        CitizensAPI.getTraitFactory().registerTrait(TraitInfo.create(SprintJumpTrait.class));
    }

    public static PatheticCitizens getInstance() {
        return INSTANCE;
    }

}

