package me.advait.mai;

import me.advait.mai.file.SettingsFile;
import org.bukkit.configuration.file.FileConfiguration;

public final class Settings {

    private static final FileConfiguration settingsFile = Mai.getInstance().getSettingsFile().getConfiguration();

    public static final int HUMANOID_NEAR_TARGET_DISTANCE = settingsFile.getInt("HUMANOID_NEAR_TARGET_DISTANCE");
    public static final int HUMANOID_PATHFINDING_TIMEOUT = settingsFile.getInt("HUMANOID_PATHFINDING_TIMEOUT");

}
