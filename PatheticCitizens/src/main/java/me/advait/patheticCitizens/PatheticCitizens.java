package me.advait.patheticCitizens;

import org.bukkit.plugin.java.JavaPlugin;

public final class PatheticCitizens extends JavaPlugin {

    @Override
    public void onEnable() {
        // Plugin startup logic

        getServer().getLogger().info("Hello world!");

    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
