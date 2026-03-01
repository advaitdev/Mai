package me.advait.mai.file.serialization;

import me.advait.mai.body.Humanoid;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Serializes and deserializes Humanoid entities to/from configuration sections.
 * Uses the modular serializers for each component.
 */
public final class HumanoidSerializer {

    private HumanoidSerializer() {}

    /**
     * Serializes a Humanoid to a configuration section.
     *
     * @param section the section to write to
     * @param humanoid the humanoid to serialize
     */
    public static void serialize(ConfigurationSection section, Humanoid humanoid) {
        if (section == null || humanoid == null) {
            return;
        }

        // Basic info
        section.set("uuid", humanoid.getUuid().toString());
        section.set("name", humanoid.getName());

        // Location
        Location location = humanoid.getMannequin() != null
                ? humanoid.getMannequin().getLocation()
                : null;
        ConfigurationSection locationSection = section.createSection("location");
        LocationSerializer.serialize(locationSection, location);

        // Inventory
        section.set("inventory", InventorySerializer.serialize(humanoid.getInventory()));

        // Equipment
        ConfigurationSection equipmentSection = section.createSection("equipment");
        EquipmentSerializer.serialize(equipmentSection, humanoid.getEquipment());
    }

    /**
     * Deserializes humanoid data from a configuration section.
     *
     * @param section the section to read from
     * @return HumanoidData containing all deserialized information
     */
    public static HumanoidData deserialize(ConfigurationSection section) {
        if (section == null) {
            return null;
        }

        // UUID
        String uuidString = section.getString("uuid");
        UUID uuid = uuidString != null ? UUID.fromString(uuidString) : UUID.randomUUID();

        // Name
        String name = section.getString("name", "Humanoid");

        // Location
        ConfigurationSection locationSection = section.getConfigurationSection("location");
        Location location = LocationSerializer.deserialize(locationSection);

        // Inventory
        String inventoryData = section.getString("inventory", "");
        ItemStack[] inventoryContents = InventorySerializer.deserialize(inventoryData);

        // Equipment
        ConfigurationSection equipmentSection = section.getConfigurationSection("equipment");
        EquipmentSerializer.EquipmentData equipment = EquipmentSerializer.deserialize(equipmentSection);

        return new HumanoidData.Builder()
                .uuid(uuid)
                .name(name)
                .location(location)
                .inventoryContents(inventoryContents)
                .equipment(equipment)
                .build();
    }

    /**
     * Creates a HumanoidData snapshot from a live Humanoid.
     *
     * @param humanoid the humanoid to snapshot
     * @return HumanoidData containing current state
     */
    public static HumanoidData toData(Humanoid humanoid) {
        if (humanoid == null) {
            return null;
        }

        Location location = humanoid.getMannequin() != null
                ? humanoid.getMannequin().getLocation()
                : null;

        EquipmentSerializer.EquipmentData equipment = humanoid.getEquipment() != null
                ? new EquipmentSerializer.EquipmentData(
                        humanoid.getEquipment().getHelmet(),
                        humanoid.getEquipment().getChestplate(),
                        humanoid.getEquipment().getLeggings(),
                        humanoid.getEquipment().getBoots(),
                        humanoid.getEquipment().getItemInMainHand(),
                        humanoid.getEquipment().getItemInOffHand()
                )
                : new EquipmentSerializer.EquipmentData();

        return new HumanoidData.Builder()
                .uuid(humanoid.getUuid())
                .name(humanoid.getName())
                .location(location)
                .inventoryContents(humanoid.getInventory().getContents())
                .equipment(equipment)
                .build();
    }

    /**
     * Applies HumanoidData to a Humanoid instance.
     * Used after spawning to restore inventory and equipment.
     *
     * @param humanoid the humanoid to apply data to
     * @param data the data to apply
     */
    public static void applyData(Humanoid humanoid, HumanoidData data) {
        if (humanoid == null || data == null) {
            return;
        }

        // Apply inventory contents
        if (data.getInventoryContents() != null) {
            humanoid.getInventory().setContents(data.getInventoryContents());
        }

        // Apply equipment (must be done after spawn so mannequin exists)
        if (humanoid.getEquipment() != null && data.getEquipment() != null) {
            EquipmentSerializer.applyToEquipment(humanoid.getEquipment(), data.getEquipment());
        }
    }
}
