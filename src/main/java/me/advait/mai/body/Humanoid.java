package me.advait.mai.body;

import me.advait.mai.brain.action.HumanoidActionAgent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class Humanoid {

    private final UUID uuid;
    private String name;
    private Mannequin mannequin;
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
        if (mannequin != null) {
            mannequin.setCustomName(name);
        }
    }

    public Mannequin spawn(Location location) {
        if (location.getWorld() == null) throw new IllegalArgumentException("Location must have a world");
        if (this.mannequin != null) this.mannequin.remove();

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

        this.mannequin = m;
        return m;
    }

    public LivingEntity getEntity() {
        return mannequin;
    }

    public Mannequin getMannequin() {
        return mannequin;
    }

    public HumanoidActionAgent getActionAgent() {
        return actionAgent;
    }

    public Inventory getInventory() {
        return inventory;
    }

    public EntityEquipment getEquipment() {
        return mannequin != null ? mannequin.getEquipment() : null;
    }

    public void setItemInMainHand(int itemSlot) {
        if (mannequin == null) return;
        ItemStack fromSlot = inventory.getItem(itemSlot);
        ItemStack currentMain = mannequin.getEquipment().getItemInMainHand();
        mannequin.getEquipment().setItemInMainHand(fromSlot);
        inventory.setItem(itemSlot, currentMain);
    }
}
