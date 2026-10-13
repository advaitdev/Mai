package me.advait.mai.assistant;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.advait.mai.body.Humanoid;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A structured snapshot of what a humanoid knows about itself and its
 * surroundings, as JSON. This is the "observation" half of the
 * observation/action contract: a model reads this, then calls a
 * {@link Tools} tool. Keep field names stable; they are an API.
 */
public final class Observation {

    private static final double NEARBY_RADIUS = 16;
    private static final int MAX_ENTITIES = 12;

    private Observation() {}

    public static JsonObject of(Humanoid h) {
        JsonObject o = new JsonObject();
        o.addProperty("name", h.getName());
        o.addProperty("uuid", h.getUuid().toString());
        LivingEntity e = h.getRawMannequin();
        o.addProperty("spawned", e != null && e.isValid());
        o.addProperty("activity", Assistant.of(h).activity());
        if (e == null || !e.isValid()) return o;

        Location l = e.getLocation();
        o.addProperty("world", l.getWorld().getName());
        o.add("position", pos(l));
        JsonObject facing = new JsonObject();
        facing.addProperty("yaw", round(l.getYaw()));
        facing.addProperty("pitch", round(l.getPitch()));
        o.add("facing", facing);
        o.addProperty("health", round(e.getHealth()));
        var max = e.getAttribute(Attribute.MAX_HEALTH);
        o.addProperty("max_health", max != null ? round(max.getValue()) : 20);
        o.addProperty("on_ground", e.isOnGround());
        o.addProperty("in_water", e.isInWater());
        o.addProperty("standing_on", l.clone().add(0, -0.5000001, 0).getBlock().getType().getKey().getKey());
        o.addProperty("time_of_day", l.getWorld().getTime());
        o.addProperty("raining", l.getWorld().hasStorm());

        ItemStack hand = e.getEquipment() != null ? e.getEquipment().getItemInMainHand() : null;
        o.add("holding", item(hand));
        JsonArray inv = new JsonArray();
        ItemStack[] contents = h.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (it == null || it.getType().isAir()) continue;
            JsonObject j = item(it).getAsJsonObject();
            j.addProperty("slot", i);
            inv.add(j);
        }
        o.add("inventory", inv);

        Block look = e.getTargetBlockExact(5);
        if (look != null) {
            JsonObject lb = new JsonObject();
            lb.addProperty("block", look.getType().getKey().getKey());
            lb.add("position", pos(look.getLocation()));
            o.add("looking_at", lb);
        }

        List<Entity> near = new ArrayList<>(e.getNearbyEntities(NEARBY_RADIUS, NEARBY_RADIUS / 2, NEARBY_RADIUS));
        near.sort(Comparator.comparingDouble(x -> x.getLocation().distanceSquared(l)));
        JsonArray players = new JsonArray();
        JsonArray others = new JsonArray();
        for (Entity n : near) {
            JsonObject j = new JsonObject();
            j.addProperty("name", n.getName());
            j.addProperty("distance", round(n.getLocation().distance(l)));
            j.add("position", pos(n.getLocation()));
            if (n instanceof LivingEntity le) j.addProperty("health", round(le.getHealth()));
            if (n instanceof Player) {
                players.add(j);
            } else if (others.size() < MAX_ENTITIES) {
                j.addProperty("type", n.getType().getKey().getKey());
                j.addProperty("hostile", n instanceof Enemy);
                others.add(j);
            }
        }
        o.add("nearby_players", players);
        o.add("nearby_entities", others);
        return o;
    }

    static JsonObject pos(Location l) {
        JsonObject p = new JsonObject();
        p.addProperty("x", round(l.getX()));
        p.addProperty("y", round(l.getY()));
        p.addProperty("z", round(l.getZ()));
        return p;
    }

    static JsonObject item(ItemStack it) {
        JsonObject j = new JsonObject();
        if (it == null || it.getType().isAir()) {
            j.addProperty("item", "air");
            j.addProperty("count", 0);
            return j;
        }
        j.addProperty("item", it.getType().getKey().getKey());
        j.addProperty("count", it.getAmount());
        return j;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
