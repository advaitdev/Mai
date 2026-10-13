package me.advait.mai.file;

import me.advait.mai.Mai;
import me.advait.mai.body.Humanoid;
import me.advait.mai.file.serialization.HumanoidData;
import me.advait.mai.file.serialization.HumanoidSerializer;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Manages the humanoids.yml file for persistent humanoid storage.
 */
public final class HumanoidsFile extends YMLBase {

    private static final String HUMANOIDS_KEY = "humanoids";

    /** UUIDs whose entries failed to deserialize on the last load. */
    private final java.util.Set<String> unreadable = new java.util.HashSet<>();

    public HumanoidsFile(String fileName) {
        super(Mai.getInstance(), new File(Mai.getInstance().getDataFolder(), fileName), false);
    }

    /**
     * Saves a humanoid to the file.
     *
     * @param humanoid the humanoid to save
     */
    public void saveHumanoid(Humanoid humanoid) {
        if (humanoid == null) return;

        ConfigurationSection humanoidsSection = getOrCreateSection(HUMANOIDS_KEY);
        ConfigurationSection humanoidSection = humanoidsSection.createSection(humanoid.getUuid().toString());
        HumanoidSerializer.serialize(humanoidSection, humanoid);
        save();
    }

    /**
     * Removes a humanoid from the file.
     *
     * @param uuid the UUID of the humanoid to remove
     */
    public void removeHumanoid(UUID uuid) {
        ConfigurationSection humanoidsSection = getConfiguration().getConfigurationSection(HUMANOIDS_KEY);
        if (humanoidsSection != null) {
            humanoidsSection.set(uuid.toString(), null);
            save();
        }
    }

    /**
     * Loads all humanoid data from the file.
     *
     * @return list of HumanoidData objects
     */
    public List<HumanoidData> loadAllHumanoids() {
        List<HumanoidData> result = new ArrayList<>();
        unreadable.clear();

        ConfigurationSection humanoidsSection = getConfiguration().getConfigurationSection(HUMANOIDS_KEY);
        if (humanoidsSection == null) {
            return result;
        }

        for (String uuidString : humanoidsSection.getKeys(false)) {
            ConfigurationSection humanoidSection = humanoidsSection.getConfigurationSection(uuidString);
            if (humanoidSection != null) {
                // One unreadable entry (e.g. item data from an incompatible
                // version) must not stop every other humanoid from loading.
                try {
                    HumanoidData data = HumanoidSerializer.deserialize(humanoidSection);
                    if (data != null) {
                        result.add(data);
                    }
                } catch (RuntimeException | LinkageError e) {
                    unreadable.add(uuidString);
                    me.advait.mai.Mai.getInstance().getLogger().warning(
                            "Skipping humanoid " + uuidString + " - could not deserialize "
                                    + "(its saved data is kept untouched): " + e);
                }
            }
        }

        return result;
    }

    /**
     * Saves all humanoids to the file, replacing existing data.
     *
     * @param humanoids the list of humanoids to save
     */
    public void saveAllHumanoids(List<Humanoid> humanoids) {
        // Entries that failed to load aren't in memory; carry their raw data
        // over so a bad load followed by a save can't erase them.
        java.util.Map<String, ConfigurationSection> kept = new java.util.LinkedHashMap<>();
        ConfigurationSection old = getConfiguration().getConfigurationSection(HUMANOIDS_KEY);
        if (old != null) {
            for (String uuid : unreadable) {
                ConfigurationSection s = old.getConfigurationSection(uuid);
                if (s != null) kept.put(uuid, s);
            }
        }

        // Clear existing data
        getConfiguration().set(HUMANOIDS_KEY, null);

        ConfigurationSection humanoidsSection = getOrCreateSection(HUMANOIDS_KEY);
        kept.forEach(humanoidsSection::set);
        for (Humanoid humanoid : humanoids) {
            ConfigurationSection humanoidSection = humanoidsSection.createSection(humanoid.getUuid().toString());
            HumanoidSerializer.serialize(humanoidSection, humanoid);
        }

        save();
    }

    /**
     * Checks if a humanoid with the given UUID exists in the file.
     *
     * @param uuid the UUID to check
     * @return true if exists
     */
    public boolean hasHumanoid(UUID uuid) {
        ConfigurationSection humanoidsSection = getConfiguration().getConfigurationSection(HUMANOIDS_KEY);
        return humanoidsSection != null && humanoidsSection.contains(uuid.toString());
    }

    /**
     * Gets the number of humanoids stored in the file.
     *
     * @return count of humanoids
     */
    public int getHumanoidCount() {
        ConfigurationSection humanoidsSection = getConfiguration().getConfigurationSection(HUMANOIDS_KEY);
        return humanoidsSection != null ? humanoidsSection.getKeys(false).size() : 0;
    }

    private ConfigurationSection getOrCreateSection(String path) {
        ConfigurationSection section = getConfiguration().getConfigurationSection(path);
        if (section == null) {
            section = getConfiguration().createSection(path);
        }
        return section;
    }
}
