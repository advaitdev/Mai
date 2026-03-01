package me.advait.mai.file.serialization;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

/**
 * Serializes and deserializes entity equipment to/from configuration sections.
 */
public final class EquipmentSerializer {

    private EquipmentSerializer() {}

    /**
     * Serializes entity equipment to a configuration section.
     *
     * @param section the section to write to
     * @param equipment the equipment to serialize
     */
    public static void serialize(ConfigurationSection section, EntityEquipment equipment) {
        if (equipment == null) {
            return;
        }

        section.set("helmet", InventorySerializer.serializeItem(equipment.getHelmet()));
        section.set("chestplate", InventorySerializer.serializeItem(equipment.getChestplate()));
        section.set("leggings", InventorySerializer.serializeItem(equipment.getLeggings()));
        section.set("boots", InventorySerializer.serializeItem(equipment.getBoots()));
        section.set("main_hand", InventorySerializer.serializeItem(equipment.getItemInMainHand()));
        section.set("off_hand", InventorySerializer.serializeItem(equipment.getItemInOffHand()));
    }

    /**
     * Deserializes equipment data from a configuration section.
     *
     * @param section the section to read from
     * @return an EquipmentData object containing all equipment pieces
     */
    public static EquipmentData deserialize(ConfigurationSection section) {
        if (section == null) {
            return new EquipmentData();
        }

        return new EquipmentData(
                InventorySerializer.deserializeItem(section.getString("helmet")),
                InventorySerializer.deserializeItem(section.getString("chestplate")),
                InventorySerializer.deserializeItem(section.getString("leggings")),
                InventorySerializer.deserializeItem(section.getString("boots")),
                InventorySerializer.deserializeItem(section.getString("main_hand")),
                InventorySerializer.deserializeItem(section.getString("off_hand"))
        );
    }

    /**
     * Applies deserialized equipment data to an entity's equipment.
     *
     * @param equipment the entity equipment to modify
     * @param data the equipment data to apply
     */
    public static void applyToEquipment(EntityEquipment equipment, EquipmentData data) {
        if (equipment == null || data == null) {
            return;
        }

        equipment.setHelmet(data.helmet());
        equipment.setChestplate(data.chestplate());
        equipment.setLeggings(data.leggings());
        equipment.setBoots(data.boots());
        equipment.setItemInMainHand(data.mainHand());
        equipment.setItemInOffHand(data.offHand());
    }

    /**
     * Record holding all equipment slots.
     */
    public record EquipmentData(
            ItemStack helmet,
            ItemStack chestplate,
            ItemStack leggings,
            ItemStack boots,
            ItemStack mainHand,
            ItemStack offHand
    ) {
        public EquipmentData() {
            this(null, null, null, null, null, null);
        }
    }
}
