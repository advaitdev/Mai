package me.advait.mai;

import co.aikar.commands.PaperCommandManager;
import de.bsommerfeld.pathetic.bukkit.PatheticBukkit;
import me.advait.mai.command.HumanoidCommand;
import me.advait.mai.command.debug.GetGitVersionCommand;
import me.advait.mai.command.debug.HDebugCommand;
import me.advait.mai.file.HumanoidsFile;
import me.advait.mai.file.SettingsFile;
import me.advait.mai.gui.GUIListener;
import me.advait.mai.listener.ChatListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

public final class Mai extends JavaPlugin {

    private static Mai INSTANCE = null;

    public static Mai getInstance() {
        return INSTANCE;
    }

    public static Logger log() {
        return getInstance().getServer().getLogger();
    }

    private SettingsFile settingsFile;
    private HumanoidsFile humanoidsFile;
    private PaperCommandManager commandManager;

    @Override
    public void onEnable() {
        // Plugin startup logic
        INSTANCE = this;

        this.settingsFile = new SettingsFile("settings.yml");
        this.humanoidsFile = new HumanoidsFile("humanoids.yml");

        // Set up persistence before loading humanoids
        Catalog.getInstance().setHumanoidsFile(humanoidsFile);

        initializePathetic();
        registerCommands();
        registerListeners();

        // Load humanoids from file after a tick to ensure worlds are loaded
        Bukkit.getScheduler().runTaskLater(this, () -> {
            Catalog.getInstance().loadAll();
        }, 20);
    }

    public void registerCommands() {
        // Initialize ACF
        commandManager = new PaperCommandManager(this);

        // Register tab completions
        commandManager.getCommandCompletions().registerCompletion("humanoids", c ->
                Catalog.getInstance().getAllNames()
        );

        // Register commands
        commandManager.registerCommand(new HDebugCommand());
        commandManager.registerCommand(new GetGitVersionCommand());
        commandManager.registerCommand(new HumanoidCommand());
    }

    public void registerListeners() {
        getServer().getPluginManager().registerEvents(new GUIListener(), this);
        getServer().getPluginManager().registerEvents(new ChatListener(), this);
    }

    public void initializePathetic() {
        // Initialize Pathetic's mapper
        PatheticBukkit.initialize(this);
    }

    @Override
    public void onDisable() {
        // Save all humanoids before shutdown
        Catalog.getInstance().saveAll();
        Catalog.getInstance().killAll();

        INSTANCE = null;
    }

    public SettingsFile getSettingsFile() {
        return settingsFile;
    }

    public HumanoidsFile getHumanoidsFile() {
        return humanoidsFile;
    }
}
