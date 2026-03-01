package me.advait.mai.file.serialization;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * POJO holding all serializable data for a Humanoid.
 * This class is used as an intermediate representation for serialization/deserialization.
 * New fields can be added here to expand what gets persisted.
 */
public class HumanoidData {

    private final UUID uuid;
    private final String name;
    private final Location location;
    private final ItemStack[] inventoryContents;
    private final EquipmentSerializer.EquipmentData equipment;

    public HumanoidData(UUID uuid, String name, Location location,
                        ItemStack[] inventoryContents, EquipmentSerializer.EquipmentData equipment) {
        this.uuid = uuid;
        this.name = name;
        this.location = location;
        this.inventoryContents = inventoryContents;
        this.equipment = equipment;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public Location getLocation() {
        return location;
    }

    public ItemStack[] getInventoryContents() {
        return inventoryContents;
    }

    public EquipmentSerializer.EquipmentData getEquipment() {
        return equipment;
    }

    /**
     * Builder for creating HumanoidData instances.
     */
    public static class Builder {
        private UUID uuid;
        private String name;
        private Location location;
        private ItemStack[] inventoryContents = new ItemStack[36];
        private EquipmentSerializer.EquipmentData equipment = new EquipmentSerializer.EquipmentData();

        public Builder uuid(UUID uuid) {
            this.uuid = uuid;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder location(Location location) {
            this.location = location;
            return this;
        }

        public Builder inventoryContents(ItemStack[] contents) {
            this.inventoryContents = contents;
            return this;
        }

        public Builder equipment(EquipmentSerializer.EquipmentData equipment) {
            this.equipment = equipment;
            return this;
        }

        public HumanoidData build() {
            if (uuid == null) {
                uuid = UUID.randomUUID();
            }
            if (name == null) {
                name = "Humanoid";
            }
            return new HumanoidData(uuid, name, location, inventoryContents, equipment);
        }
    }
}
