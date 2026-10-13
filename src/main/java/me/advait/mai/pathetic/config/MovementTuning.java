package me.advait.mai.pathetic.config;

import me.advait.mai.Mai;
import me.advait.mai.pathetic.PatheticAgent;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Runtime tuning surface for every movement/pathfinding parameter. This is
 * the API a learning loop (or a model choosing a play style) drives: read
 * the current parameter vector, change values, and have the very next path
 * request use them, with no restart.
 *
 * <p>Keys are paths relative to the {@code movement:} section of
 * settings.yml, e.g. {@code costs.walk_flat}, {@code physics.sprint_factor},
 * {@code navigation.periodic_replan_ticks}. Only keys that already exist
 * can be set, and the new value must have the same kind (number or
 * boolean), so a typo can't silently create a dead parameter.
 *
 * <p>Changes apply in memory immediately. {@link #save()} persists them.
 * Walks already in flight keep the config they started with; every new
 * walk or pathfind uses the new one.
 *
 * <p>Profiles: {@code movement_profiles.<name>} in settings.yml maps keys
 * to override values. {@link #applyProfile} sets them all at once, e.g. a
 * "safe" profile with a high fall-damage penalty versus a "speedrun"
 * profile that favours parkour.
 *
 * <p>Main thread only.
 */
public final class MovementTuning {

    private static final String ROOT = "movement";
    private static final String PROFILES = "movement_profiles";

    private MovementTuning() {}

    private static FileConfiguration cfg() {
        return Mai.getInstance().getSettingsFile().getConfiguration();
    }

    /** Every tunable leaf under {@code movement:}, as key to current value. */
    public static Map<String, Object> snapshot() {
        Map<String, Object> out = new LinkedHashMap<>();
        ConfigurationSection root = cfg().getConfigurationSection(ROOT);
        if (root == null) return out;
        for (String key : root.getKeys(true)) {
            Object v = root.get(key);
            if (v instanceof ConfigurationSection) continue;
            out.put(key, v);
        }
        return out;
    }

    /** Current value of one key, or null if it doesn't exist. */
    public static Object get(String key) {
        Object v = cfg().get(ROOT + "." + key);
        return v instanceof ConfigurationSection ? null : v;
    }

    /**
     * Sets one parameter and rebuilds the movement config. Returns null on
     * success, or a human-readable error.
     */
    public static String set(String key, String rawValue) {
        String err = setNoRebuild(key, rawValue);
        if (err == null) rebuild();
        return err;
    }

    private static String setNoRebuild(String key, String rawValue) {
        Object current = get(key);
        if (current == null) return "Unknown movement key: " + key;
        String raw = rawValue.strip();
        if (current instanceof Boolean) {
            String r = raw.toLowerCase(Locale.ROOT);
            if (!r.equals("true") && !r.equals("false")) {
                return key + " is a boolean; got '" + rawValue + "'";
            }
            cfg().set(ROOT + "." + key, Boolean.parseBoolean(r));
            return null;
        }
        if (current instanceof Number) {
            try {
                if (current instanceof Integer || current instanceof Long) {
                    cfg().set(ROOT + "." + key, (int) Math.round(Double.parseDouble(raw)));
                } else {
                    double d = Double.parseDouble(raw);
                    if (Double.isNaN(d) || Double.isInfinite(d)) return key + " must be finite";
                    cfg().set(ROOT + "." + key, d);
                }
                return null;
            } catch (NumberFormatException e) {
                return key + " is a number; got '" + rawValue + "'";
            }
        }
        return key + " is not a tunable scalar";
    }

    /**
     * Applies every override in {@code movement_profiles.<name>}. Returns
     * null on success, or the first error (earlier keys stay applied).
     */
    public static String applyProfile(String name) {
        ConfigurationSection p = cfg().getConfigurationSection(PROFILES + "." + name);
        if (p == null) return "Unknown profile: " + name;
        for (String key : p.getKeys(true)) {
            Object v = p.get(key);
            if (v instanceof ConfigurationSection) continue;
            String err = setNoRebuild(key, String.valueOf(v));
            if (err != null) {
                rebuild();
                return "Profile " + name + ": " + err;
            }
        }
        rebuild();
        return null;
    }

    /** Names of the profiles defined in settings.yml. */
    public static java.util.Set<String> profiles() {
        ConfigurationSection p = cfg().getConfigurationSection(PROFILES);
        return p == null ? java.util.Set.of() : p.getKeys(false);
    }

    /** Persists the in-memory parameter values to settings.yml. */
    public static void save() {
        Mai.getInstance().getSettingsFile().save();
    }

    private static void rebuild() {
        PatheticAgent.getInstance().initialize(new MovementConfig(cfg()));
    }
}
