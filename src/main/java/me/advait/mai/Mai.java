package me.advait.mai;

import co.aikar.commands.MessageType;
import co.aikar.commands.PaperCommandManager;
import de.bsommerfeld.pathetic.bukkit.PatheticBukkit;
import me.advait.mai.command.HumanoidCommand;
import me.advait.mai.file.HumanoidsFile;
import me.advait.mai.file.SettingsFile;
import me.advait.mai.gui.GUIListener;
import me.advait.mai.listener.ChatListener;
import me.advait.mai.listener.EntityCleanupListener;
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
        registerCommands();
        registerListeners();

        Bukkit.getScheduler().runTaskLater(this, () -> Catalog.getInstance().loadAll(), 20);
    }

    private void registerCommands() {
        PaperCommandManager commandManager = new PaperCommandManager(this);
        commandManager.enableUnstableAPI("help");
        commandManager.getCommandCompletions().registerCompletion("humanoids", c ->
                Catalog.getInstance().getAllNames()
        );
        commandManager.registerCommand(new HumanoidCommand());
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new GUIListener(), this);
        getServer().getPluginManager().registerEvents(new ChatListener(), this);
        getServer().getPluginManager().registerEvents(new EntityCleanupListener(), this);
    }

    @Override
    public void onDisable() {
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
