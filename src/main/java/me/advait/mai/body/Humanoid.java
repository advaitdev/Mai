package me.advait.mai.body;

import me.advait.mai.Mai;
import me.advait.mai.body.fake.FakePlayers;
import me.advait.mai.brain.action.HumanoidActionAgent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * A named bot and the body it inhabits. The body is a server-side fake
 * player ({@link FakePlayers}: real player physics, hunger, attacks,
 * advancements) or, with {@code humanoid.body: mannequin}, a Mannequin mob
 * whose gaps from a player {@link Body} patches. Everything that drives it
 * goes through {@link Body} either way.
 */
public class Humanoid {

    private final UUID uuid;
    private String name;
    private LivingEntity body;
    private Location lastKnownLocation;
    private final Inventory inventory;
    private final HumanoidActionAgent actionAgent;

    public Humanoid(String name) {
        this(UUID.randomUUID(), name);
    }

    public Humanoid(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
        this.inventory = Bukkit.createInventory(null, 36, name + "'s Inventory");
        this.actionAgent = new HumanoidActionAgent();
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        if (body instanceof Player p) {
            p.setDisplayName(name);
            p.setPlayerListName(name);
        } else if (body != null) {
            body.setCustomName(name);
        }
    }

    /** Whether new bodies are fake players (the default) rather than Mannequins. */
    public static boolean playerBodies() {
        return !"mannequin".equalsIgnoreCase(
                Mai.getInstance().getSettingsFile().getConfiguration().getString("humanoid.body", "player"));
    }

    /**
     * A fresh UUID for each fake player body, version 2 so no Mojang account
     * (version 4) can share it. Fresh per spawn: the test harness tells a
     * respawned body from the one that died by its UUID.
     */
    private static UUID playerId() {
        UUID r = UUID.randomUUID();
        return new UUID((r.getMostSignificantBits() & ~0xF000L) | 0x2000L, r.getLeastSignificantBits());
    }

    public LivingEntity spawn(Location location) {
        if (location.getWorld() == null) throw new IllegalArgumentException("Location must have a world");
        despawn();
        LivingEntity e = playerBodies() ? spawnPlayer(location) : spawnMannequin(location);
        // Give it a player's movement attributes (a Mannequin's speed is a mob's 0.7).
        Body.prepare(e);
        this.body = e;
        this.lastKnownLocation = location.clone();
        return e;
    }

    private Player spawnPlayer(Location location) {
        Player p = FakePlayers.spawn(name, playerId(), location);
        p.setGameMode(org.bukkit.GameMode.SURVIVAL);
        return p;
    }

    private Mannequin spawnMannequin(Location location) {
        Mannequin m = (Mannequin) location.getWorld().spawnEntity(location, EntityType.MANNEQUIN);
        m.setCustomName(this.name);
        m.setCustomNameVisible(true);
        m.setDescription(null);
        // Disable mob AI so it doesn't fight our manual movement,
        // but keep gravity and physics enabled for natural falling/collision.
        m.setAI(false);
        m.setGravity(true);
        m.setInvulnerable(false);
        m.setImmovable(false);
        m.setRemoveWhenFarAway(false);
        m.setProfile(ResolvableProfile.resolvableProfile().name(this.name).build());
        return m;
    }

    /** Removes the body from the world (a fake player disconnects). */
    public void despawn() {
        LivingEntity e = body;
        body = null;
        if (e == null) return;
        Body.forget(e);
        if (e instanceof Player p && FakePlayers.isFake(p)) FakePlayers.remove(p);
        else if (e.isValid()) e.remove();
    }

    public LivingEntity getEntity() {
        return getMannequin();
    }

    /**
     * The current body without the respawn {@link #getMannequin} does: may
     * be dead or invalid. For code that must see a death (test harness,
     * reflexes) rather than silently get a fresh body.
     */
    public LivingEntity peekEntity() {
        return body;
    }

    /** The body, respawned at its last position if it died or was removed. */
    public LivingEntity getMannequin() {
        if (body != null && (!body.isValid() || body.isDead()) && lastKnownLocation != null) {
            Mai.getInstance().getLogger().warning("Body of '" + name + "' was gone, respawning...");
            spawn(lastKnownLocation);
        }
        return body;
    }

    public Location getLastKnownLocation() {
        if (body != null && body.isValid()) {
            lastKnownLocation = body.getLocation();
        }
        return lastKnownLocation;
    }

    /**
     * Returns the raw body reference without triggering auto-respawn.
     * Used by cleanup logic to check entity UUIDs.
     */
    public LivingEntity getRawMannequin() {
        return body;
    }

    public HumanoidActionAgent getActionAgent() {
        return actionAgent;
    }

    public Inventory getInventory() {
        return inventory;
    }

    public EntityEquipment getEquipment() {
        return body != null ? body.getEquipment() : null;
    }

    public void setItemInMainHand(int itemSlot) {
        if (body == null) return;
        ItemStack fromSlot = inventory.getItem(itemSlot);
        ItemStack currentMain = body.getEquipment().getItemInMainHand();
        body.getEquipment().setItemInMainHand(fromSlot);
        inventory.setItem(itemSlot, currentMain);
    }
}
