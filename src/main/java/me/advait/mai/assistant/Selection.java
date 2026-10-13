package me.advait.mai.assistant;

import me.advait.mai.Catalog;
import me.advait.mai.body.Humanoid;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which humanoid a command is talking to. An explicit name wins; otherwise
 * the sender's selection ({@code /h select}); otherwise the nearest humanoid
 * to the sending player; otherwise the only humanoid, if there is just one.
 */
public final class Selection {

    private static final Map<UUID, UUID> SELECTED = new ConcurrentHashMap<>();
    private static final UUID CONSOLE = new UUID(0, 0);

    private Selection() {}

    private static UUID key(CommandSender sender) {
        return sender instanceof Player p ? p.getUniqueId() : CONSOLE;
    }

    public static void select(CommandSender sender, Humanoid h) {
        SELECTED.put(key(sender), h.getUuid());
    }

    public static Humanoid selected(CommandSender sender) {
        UUID id = SELECTED.get(key(sender));
        return id == null ? null : Catalog.getInstance().getByUuid(id);
    }

    /** Resolve the humanoid a command means, or throw with a message for the sender. */
    public static Humanoid resolve(CommandSender sender, String name) {
        Catalog catalog = Catalog.getInstance();
        if (name != null && !name.isBlank()) {
            Humanoid h = catalog.getByName(name);
            if (h == null) throw new ToolException("No humanoid named '" + name + "'. Try /h spawn " + name);
            return h;
        }
        Humanoid sel = selected(sender);
        if (sel != null) return sel;

        List<Humanoid> all = catalog.getAllHumanoids();
        if (all.isEmpty()) throw new ToolException("There are no humanoids yet. Spawn one with /h spawn [name]");
        if (sender instanceof Player p) {
            Humanoid best = null;
            double bestD = Double.MAX_VALUE;
            for (Humanoid h : all) {
                LivingEntity e = h.getRawMannequin();
                if (e == null || !e.getWorld().equals(p.getWorld())) continue;
                double d = e.getLocation().distanceSquared(p.getLocation());
                if (d < bestD) { bestD = d; best = h; }
            }
            if (best != null) return best;
        }
        if (all.size() == 1) return all.getFirst();
        throw new ToolException("Which humanoid? Name one, or pick a default with /h select <name>");
    }
}
