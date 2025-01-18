package me.advait.mai.file;

import me.advait.mai.Mai;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class SettingsFile extends YMLBase {

    public SettingsFile(String fileName) {
        super(Mai.getInstance(), new File(Mai.getInstance().getDataFolder(), fileName), true);
    }

}
