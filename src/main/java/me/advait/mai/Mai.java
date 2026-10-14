package me.advait.mai;

import co.aikar.commands.MessageType;
import co.aikar.commands.PaperCommandManager;
import de.bsommerfeld.pathetic.bukkit.PatheticBukkit;
import me.advait.mai.command.HumanoidCommand;
import me.advait.mai.file.HumanoidsFile;
import me.advait.mai.file.SettingsFile;
import me.advait.mai.gui.GUIListener;
import me.advait.mai.listener.EntityCleanupListener;
import me.advait.mai.listener.WorldChangeListener;
import me.advait.mai.obstacle.ObstacleCommand;
import me.advait.mai.obstacle.ObstacleTester;
import me.advait.mai.pathetic.PatheticAgent;
import me.advait.mai.pathetic.config.MovementConfig;
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

    @Override
    public void onEnable() {
        INSTANCE = this;

        this.settingsFile = new SettingsFile("settings.yml");
        this.humanoidsFile = new HumanoidsFile("humanoids.yml");

        Catalog.getInstance().setHumanoidsFile(humanoidsFile);

        PatheticBukkit.initialize(this);
        MovementConfig movementConfig = new MovementConfig(settingsFile.getConfiguration());
        PatheticAgent.getInstance().initialize(movementConfig);
        ObstacleTester.getInstance().initialize();
        registerCommands();
        registerListeners();
        me.advait.mai.assistant.Assistant.startTicker();

        Bukkit.getScheduler().runTaskLater(this, () -> Catalog.getInstance().loadAll(), 20);
    }

    private void registerCommands() {
        PaperCommandManager commandManager = new PaperCommandManager(this);
        commandManager.enableUnstableAPI("help");
        commandManager.getCommandCompletions().registerCompletion("humanoids", c ->
                Catalog.getInstance().getAllNames()
        );
        commandManager.getCommandCompletions().registerCompletion("obstacles", c ->
                ObstacleTester.getInstance().stems()
        );
        commandManager.getCommandCompletions().registerCompletion("tools", c ->
                me.advait.mai.assistant.Tools.all().stream().map(me.advait.mai.assistant.Tools.Tool::name).toList()
        );
        commandManager.registerCommand(new HumanoidCommand());
        commandManager.registerCommand(new ObstacleCommand());
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new GUIListener(), this);
        getServer().getPluginManager().registerEvents(new EntityCleanupListener(), this);
        getServer().getPluginManager().registerEvents(new WorldChangeListener(), this);
        getServer().getPluginManager().registerEvents(new me.advait.mai.assistant.AssistantChatListener(), this);
        getServer().getPluginManager().registerEvents(new me.advait.mai.assistant.GuardBehavior.DamageListener(), this);
    }

    @Override
    public void onDisable() {
        Catalog.getInstance().saveAll();
        Catalog.getInstance().killAll();
        INSTANCE = null;
    }

    /**
     * Re-reads settings.yml from disk and rebuilds the movement config and
     * pathfinder registry from it. In-flight walks keep the config they
     * started with; every new walk/pathfind uses the reloaded values, so
     * movement weights can be tuned (by a human or, later, a model) without
     * a server restart.
     */
    public void reloadSettings() {
        settingsFile.reload();
        MovementConfig movementConfig = new MovementConfig(settingsFile.getConfiguration());
        PatheticAgent.getInstance().initialize(movementConfig);
        getLogger().info("Settings reloaded; movement config rebuilt.");
    }

    public SettingsFile getSettingsFile() {
        return settingsFile;
    }

    public HumanoidsFile getHumanoidsFile() {
        return humanoidsFile;
    }
}
