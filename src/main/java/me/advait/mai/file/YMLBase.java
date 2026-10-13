package me.advait.mai.file;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * @author IllusionTheDev
 */

public abstract class YMLBase {

    private final boolean existsOnSource;
    private final JavaPlugin plugin;

    private final File file;

    private FileConfiguration configuration;

    public YMLBase(JavaPlugin plugin, File file, boolean existsOnSource) {
        this.plugin = plugin;
        this.file = file;
        this.existsOnSource = existsOnSource;

        this.configuration = loadConfiguration();
        save();
    }

    public void save() {
        try {
            configuration.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Re-reads the file from disk (merging bundled defaults again). */
    public void reload() {
        this.configuration = loadConfiguration();
    }

    private FileConfiguration loadConfiguration() {
        FileConfiguration cfg = new YamlConfiguration();

        if (!file.exists()) {
            file.getParentFile().mkdirs();
            if (existsOnSource)
                plugin.saveResource(file.getName(), false);
            else {
                try {
                    file.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        try {
            cfg.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }

        // Merge any keys the bundled resource has that the on-disk file lacks.
        // Without this, a settings.yml written by an older build silently
        // keeps the plugin on hardcoded fallbacks for every newer key (this
        // is how the whole `movement:` section once went unapplied).
        if (existsOnSource) {
            InputStream in = plugin.getResource(file.getName());
            if (in != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(in, StandardCharsets.UTF_8));
                cfg.setDefaults(defaults);
                cfg.options().copyDefaults(true);
            }
        }

        return cfg;
    }

    public FileConfiguration getConfiguration() {
        return configuration;
    }

}
