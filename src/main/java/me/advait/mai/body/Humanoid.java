package me.advait.mai.body;

import me.advait.mai.brain.Brain;
import me.advait.mai.brain.cerebrum.*;
import me.advait.mai.brain.cerebrum.movement.HumanoidMotorCortex;
import me.advait.mai.brain.cerebrum.movement.MotorCortex;
import me.advait.mai.util.InventoryUtil;
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
    private final Brain brain;
    private final BrocasArea brocasArea;
    private final MotorCortex motorCortex;
    private final PrefrontalCortex prefrontalCortex;

    public Humanoid(String name) {
        this(UUID.randomUUID(), name);
    }

    public Humanoid(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
        this.inventory = Bukkit.createInventory(null, 36, name + "'s Inventory");
        this.brocasArea = new HumanoidBrocasArea();
        this.motorCortex = new HumanoidMotorCortex();
        this.prefrontalCortex = new HumanoidPrefrontalCortex();
        this.brain = new Brain(brocasArea, motorCortex, prefrontalCortex);
    }

    /**
     * Returns the unique identifier for this humanoid.
     */
    public UUID getUuid() {
        return uuid;
    }

    /**
     * Returns the name of this humanoid.
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of this humanoid and updates the display name if spawned.
     */
    public void setName(String name) {
        this.name = name;
        if (mannequin != null) {
            mannequin.setCustomName(name);
        }
    }

    /**
     * Spawns the humanoid's mannequin at the given location. Creates the entity if not yet spawned.
     */
    public Mannequin spawn(Location location) {
        if (location.getWorld() == null) throw new IllegalArgumentException("Location must have a world");
        if (this.mannequin != null) {
            this.mannequin.remove();
        }
        Mannequin m = (Mannequin) location.getWorld().spawnEntity(location, EntityType.MANNEQUIN);
        m.setCustomName(this.name);
        m.setCustomNameVisible(true);
        m.setDescription(null);  // Remove "NPC" text below name
        m.setAI(false);
        m.setInvulnerable(true);
        m.setImmovable(false);
        m.setRemoveWhenFarAway(false);

        // Set skin based on player name (will resolve texture from Mojang)
        m.setProfile(ResolvableProfile.resolvableProfile().name(this.name).build());

        this.mannequin = m;
        return m;
    }

    /** Returns the living entity (mannequin) for this humanoid. May be null if not spawned. */
    public LivingEntity getEntity() {
        return mannequin;
    }

    /** Returns the mannequin entity. May be null if not spawned. */
    public Mannequin getMannequin() {
        return mannequin;
    }

    /** @deprecated Use getEntity() or getMannequin(). */
    public Mannequin getNpc() {
        return mannequin;
    }

    public Brain getBrain() {
        return brain;
    }

    public Inventory getInventory() {
        return inventory;
    }

    public EntityEquipment getEquipment() {
        return mannequin != null ? mannequin.getEquipment() : null;
    }

    /**
     * Sets the item in the humanoid's main hand from the given inventory slot.
     * Swaps the current main hand item into that slot.
     */
    public void setItemInMainHand(int itemSlot) {
        if (mannequin == null) return;
        ItemStack fromSlot = inventory.getItem(itemSlot);
        ItemStack currentMain = mannequin.getEquipment().getItemInMainHand();
        mannequin.getEquipment().setItemInMainHand(fromSlot);
        inventory.setItem(itemSlot, currentMain);
    }
}
