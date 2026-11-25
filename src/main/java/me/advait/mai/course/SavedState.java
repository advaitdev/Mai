package me.advait.mai.course;

import me.advait.mai.Mai;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Collection;

/**
 * What a player had before entering a course (where they were, game mode,
 * flight, health, hunger, effects), restored when they leave. The return
 * point and game mode are also written to the player's persistent data, so
 * a crash or restart mid-course still sends them back on their next join.
 */
final class SavedState {

    private static final NamespacedKey KEY = new NamespacedKey("mai", "course_return");

    private final Location location;
    private final GameMode mode;
    private final boolean allowFlight, flying;
    private final double health;
    private final int food;
    private final float saturation;
    private final Collection<PotionEffect> effects;

    SavedState(Player p) {
        location = p.getLocation();
        mode = p.getGameMode();
        allowFlight = p.getAllowFlight();
        flying = p.isFlying();
        health = p.getHealth();
        food = p.getFoodLevel();
        saturation = p.getSaturation();
        effects = new ArrayList<>(p.getActivePotionEffects());
        p.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, encode(location, mode));
    }

    void restore(Player p) {
        p.getPersistentDataContainer().remove(KEY);
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        p.addPotionEffects(effects);
        p.setGameMode(mode);
        p.setAllowFlight(allowFlight);
        p.setFlying(flying && allowFlight);
        p.setHealth(Math.min(health, p.getMaxHealth()));
        p.setFoodLevel(food);
        p.setSaturation(saturation);
        p.setFallDistance(0);
        p.teleport(location);
    }

    /** Everything but the location: for a player something else already moved out of the course. */
    void restoreExceptLocation(Player p) {
        p.getPersistentDataContainer().remove(KEY);
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        p.addPotionEffects(effects);
        p.setGameMode(mode);
        p.setAllowFlight(allowFlight);
        p.setFoodLevel(food);
        p.setSaturation(saturation);
    }

    /** Sends a player who left mid-course (a crash, a restart) back where they came from. */
    static void recover(Player p) {
        String s = p.getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        if (s == null) return;
        p.getPersistentDataContainer().remove(KEY);
        String[] f = s.split(";");
        try {
            World w = Bukkit.getWorld(f[0]);
            if (w == null) w = Bukkit.getWorlds().getFirst();
            Location loc = new Location(w, Double.parseDouble(f[1]), Double.parseDouble(f[2]), Double.parseDouble(f[3]),
                    Float.parseFloat(f[4]), Float.parseFloat(f[5]));
            p.setGameMode(GameMode.valueOf(f[6]));
            p.teleport(loc);
        } catch (RuntimeException e) {
            Mai.getInstance().getLogger().warning("Could not restore " + p.getName() + " after a course: " + e);
        }
    }

    private static String encode(Location l, GameMode mode) {
        return String.join(";", l.getWorld().getName(), Double.toString(l.getX()), Double.toString(l.getY()),
                Double.toString(l.getZ()), Float.toString(l.getYaw()), Float.toString(l.getPitch()), mode.name());
    }
}
