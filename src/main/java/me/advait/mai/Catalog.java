package me.advait.mai;

import me.advait.mai.body.Humanoid;
import me.advait.mai.file.HumanoidsFile;
import me.advait.mai.file.serialization.HumanoidData;
import me.advait.mai.file.serialization.HumanoidSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.*;

/**
 * Central registry for all humanoid instances.
 * Manages spawning, tracking, and persistence of humanoids.
 */
public class Catalog {

    private static final Catalog INSTANCE = new Catalog();

    public static Catalog getInstance() {
        return INSTANCE;
    }

    private final Map<UUID, Humanoid> humanoids = new LinkedHashMap<>();
    private HumanoidsFile humanoidsFile;

    private Catalog() {}

    /**
     * Sets the humanoids file for persistence.
     * Must be called before loadAll().
     */
    public void setHumanoidsFile(HumanoidsFile file) {
        this.humanoidsFile = file;
    }

    /**
     * Loads all humanoids from the persistence file and spawns them.
     */
    public void loadAll() {
        if (humanoidsFile == null) {
            Mai.getInstance().getLogger().warning("HumanoidsFile not set, cannot load humanoids");
            return;
        }

        List<HumanoidData> dataList = humanoidsFile.loadAllHumanoids();
        for (HumanoidData data : dataList) {
            if (data.getLocation() == null) {
                Mai.getInstance().getLogger().warning("Skipping humanoid " + data.getName() + " - invalid location");
                continue;
            }

            Humanoid humanoid = new Humanoid(data.getUuid(), data.getName());
            humanoid.spawn(data.getLocation());
            HumanoidSerializer.applyData(humanoid, data);
            humanoids.put(humanoid.getUuid(), humanoid);
        }

        Mai.getInstance().getLogger().info("Loaded " + humanoids.size() + " humanoid(s) from file");
    }

    /**
     * Saves all humanoids to the persistence file.
     */
    public void saveAll() {
        if (humanoidsFile == null) {
            Mai.getInstance().getLogger().warning("HumanoidsFile not set, cannot save humanoids");
            return;
        }

        humanoidsFile.saveAllHumanoids(new ArrayList<>(humanoids.values()));
        Mai.getInstance().getLogger().info("Saved " + humanoids.size() + " humanoid(s) to file");
    }

    /**
     * Saves a single humanoid to the persistence file.
     */
    public void save(Humanoid humanoid) {
        if (humanoidsFile == null || humanoid == null) return;
        humanoidsFile.saveHumanoid(humanoid);
    }

    /**
     * Registers a new humanoid with the given name at the specified location.
     *
     * @param name the name for the humanoid
     * @param location the spawn location
     * @return the created humanoid
     */
    public Humanoid register(String name, Location location) {
        Humanoid humanoid = new Humanoid(name);
        humanoid.spawn(location);
        humanoids.put(humanoid.getUuid(), humanoid);
        save(humanoid);
        return humanoid;
    }

    /**
     * Registers an existing humanoid instance.
     *
     * @param humanoid the humanoid to register
     */
    public void register(Humanoid humanoid) {
        if (humanoid == null) return;
        humanoids.put(humanoid.getUuid(), humanoid);
        save(humanoid);
    }

    /**
     * Unregisters and removes a humanoid.
     *
     * @param humanoid the humanoid to remove
     */
    public void unregister(Humanoid humanoid) {
        if (humanoid == null) return;
        unregister(humanoid.getUuid());
    }

    /**
     * Unregisters and removes a humanoid by UUID.
     *
     * @param uuid the UUID of the humanoid to remove
     */
    public void unregister(UUID uuid) {
        Humanoid humanoid = humanoids.remove(uuid);
        if (humanoid != null) {
            if (humanoid.getEntity() != null && humanoid.getEntity().isValid()) {
                humanoid.getEntity().remove();
            }
            if (humanoidsFile != null) {
                humanoidsFile.removeHumanoid(uuid);
            }
        }
    }

    /**
     * Gets a humanoid by UUID.
     *
     * @param uuid the UUID
     * @return the humanoid, or null if not found
     */
    public Humanoid getByUuid(UUID uuid) {
        return humanoids.get(uuid);
    }

    /**
     * Gets a humanoid by name (case-insensitive).
     *
     * @param name the name
     * @return the humanoid, or null if not found
     */
    public Humanoid getByName(String name) {
        for (Humanoid h : humanoids.values()) {
            if (h.getName().equalsIgnoreCase(name)) {
                return h;
            }
        }
        return null;
    }

    /**
     * Gets all humanoid names for tab completion.
     *
     * @return collection of humanoid names
     */
    public Collection<String> getAllNames() {
        List<String> names = new ArrayList<>();
        for (Humanoid h : humanoids.values()) {
            names.add(h.getName());
        }
        return names;
    }

    /**
     * Gets all registered humanoids.
     *
     * @return unmodifiable list of humanoids
     */
    public List<Humanoid> getAllHumanoids() {
        return new ArrayList<>(humanoids.values());
    }

    /**
     * Gets the number of registered humanoids.
     *
     * @return count
     */
    public int getCount() {
        return humanoids.size();
    }

    /**
     * Removes all humanoid entities and clears the registry.
     * Does NOT remove from persistence file.
     */
    public void killAll() {
        for (Humanoid h : humanoids.values()) {
            if (h.getEntity() != null && h.getEntity().isValid()) {
                h.getEntity().remove();
            }
        }
        humanoids.clear();
    }

    /**
     * Checks if a humanoid with the given name already exists.
     *
     * @param name the name to check
     * @return true if exists
     */
    public boolean nameExists(String name) {
        return getByName(name) != null;
    }
}
