package me.advait.mai;

import co.aikar.commands.PaperCommandManager;
import de.metaphoriker.pathetic.bukkit.PatheticBukkit;
import me.advait.mai.command.HDebugCommand;
import me.advait.mai.command.PatheticGroundTestCommand;
import me.advait.mai.command.PatheticTestCommand;
import me.advait.mai.file.SettingsFile;
import me.advait.mai.listener.ChatListener;
import me.advait.mai.npc.trait.HumanoidTrait;
import me.advait.mai.planner.TestCommands;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.trait.TraitInfo;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;


// Testing new branch momir
public final class Mai extends JavaPlugin {

    private static Mai INSTANCE = null;

    public static Mai getInstance() {
        return INSTANCE;
    }

    public static Logger log() {
        return getInstance().getServer().getLogger();
    }

    private SettingsFile settingsFile;

    @Override
    public void onEnable() {
        // Plugin startup logic
        INSTANCE = this;

        this.settingsFile = new SettingsFile("settings.yml");

        registerCommands();
        initializeCitizens();
        initializePathetic();
        registerListeners();

        getServer().getPluginManager().registerEvents(new ChatListener(), this);

    }

    public void registerCommands() {
        // Initialize ACF
        PaperCommandManager paperCommandManager = new PaperCommandManager(this);

        paperCommandManager.registerCommand(new HDebugCommand());
        getCommand("pathetictest").setExecutor(new PatheticTestCommand());
        getCommand("patheticgroundtest").setExecutor(new PatheticGroundTestCommand());
        getCommand("testcraftingplanner").setExecutor(new TestCommands());
    }

    public void initializeCitizens() {
        CitizensAPI.getTraitFactory().registerTrait(TraitInfo.create(HumanoidTrait.class));
        getLogger().info("Citizens's registered traits: " + CitizensAPI.getTraitFactory().getRegisteredTraits());
        Bukkit.getScheduler().runTaskLater(JavaPlugin.getPlugin(Mai.class), () -> {
            Catalog.getInstance().killAll();
            Catalog.getInstance().registerHumanoid("Mai");
        }, 20);
    }

    public void registerListeners() {
        //getServer().getPluginManager().registerEvents(new NavigationListener(), this);
    }

    public void initializePathetic() {
        // Initialize Pathetic's mapper
        PatheticBukkit.initialize(this);

    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic

        INSTANCE = null;
    }

    public SettingsFile getSettingsFile() {
        return settingsFile;
    }

}
