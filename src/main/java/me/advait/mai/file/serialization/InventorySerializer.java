package me.advait.mai.file.serialization;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Serializes and deserializes Bukkit inventories to/from Base64 strings.
 */
public final class InventorySerializer {

    // Same format SnakeYAML's Base64Coder.encodeLines produced (76-char lines,
    // LF separators), so data saved by older builds still decodes. Paper
    // 26.x no longer exposes SnakeYAML's internal Base64Coder to plugins.
    private static final Base64.Encoder ENCODER =
            Base64.getMimeEncoder(76, "\n".getBytes(StandardCharsets.US_ASCII));
    private static final Base64.Decoder DECODER = Base64.getMimeDecoder();

    private InventorySerializer() {}

    /**
     * Serializes an inventory's contents to a Base64 string.
     *
     * @param inventory the inventory to serialize
     * @return Base64 encoded string of the inventory contents
     */
    public static String serialize(Inventory inventory) {
        return serializeItemStacks(inventory.getContents());
    }

    /**
     * Serializes an array of ItemStacks to a Base64 string.
     *
     * @param items the items to serialize
     * @return Base64 encoded string
     */
    public static String serializeItemStacks(ItemStack[] items) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {

            dataOutput.writeInt(items.length);
            for (ItemStack item : items) {
                dataOutput.writeObject(item);
            }

            return ENCODER.encodeToString(outputStream.toByteArray()) + "\n";
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize inventory", e);
        }
    }

    /**
     * Deserializes a Base64 string back into an array of ItemStacks.
     *
     * @param data the Base64 encoded string
     * @return array of ItemStacks
     */
    public static ItemStack[] deserialize(String data) {
        if (data == null || data.isEmpty()) {
            return new ItemStack[0];
        }

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(DECODER.decode(data));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {

            int size = dataInput.readInt();
            ItemStack[] items = new ItemStack[size];

            for (int i = 0; i < size; i++) {
                items[i] = (ItemStack) dataInput.readObject();
            }

            return items;
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("Failed to deserialize inventory", e);
        }
    }

    /**
     * Serializes a single ItemStack to Base64.
     *
     * @param item the item to serialize
     * @return Base64 encoded string, or empty string if item is null
     */
    public static String serializeItem(ItemStack item) {
        if (item == null) {
            return "";
        }
        return serializeItemStacks(new ItemStack[]{item});
    }

    /**
     * Deserializes a single ItemStack from Base64.
     *
     * @param data the Base64 encoded string
     * @return the ItemStack, or null if data is empty
     */
    public static ItemStack deserializeItem(String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        ItemStack[] items = deserialize(data);
        return items.length > 0 ? items[0] : null;
    }
}
