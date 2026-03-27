package me.advait.mai.util;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class InventoryUtil {

    private InventoryUtil() {}

    /**
     * Finds the first slot containing a tool matching the given tag.
     *
     * @param inventory the inventory to search
     * @param toolTag the material tag to match (e.g. Tag.ITEMS_PICKAXES)
     * @return the slot index, or -1 if not found
     */
    public static int findTool(Inventory inventory, Tag<Material> toolTag) {
        if (inventory == null) return -1;
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && toolTag.isTagged(item.getType())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Swaps two items in an inventory.
     */
    public static void swapItems(Inventory inventory, int slot1, int slot2) {
        ItemStack item1 = inventory.getItem(slot1);
        ItemStack item2 = inventory.getItem(slot2);
        inventory.setItem(slot1, item2);
        inventory.setItem(slot2, item1);
    }
}
