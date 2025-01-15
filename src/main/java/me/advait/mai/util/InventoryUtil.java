package me.advait.mai.util;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class InventoryUtil {

    public static int getSlotWithPickaxe(Inventory inventory) {
        if (inventory == null) return -1;

        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);

            if (item != null && isPickaxe(item.getType())) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isPickaxe(Material material) {
        return material == Material.WOODEN_PICKAXE ||
                material == Material.STONE_PICKAXE ||
                material == Material.IRON_PICKAXE ||
                material == Material.GOLDEN_PICKAXE ||
                material == Material.DIAMOND_PICKAXE ||
                material == Material.NETHERITE_PICKAXE;
    }

    public static int getSlotWithAxe(Inventory inventory) {
        if (inventory == null) return -1;

        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);

            if (item != null && isAxe(item.getType())) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isAxe(Material material) {
        return material == Material.WOODEN_AXE ||
                material == Material.STONE_AXE ||
                material == Material.IRON_AXE ||
                material == Material.GOLDEN_AXE ||
                material == Material.DIAMOND_AXE ||
                material == Material.NETHERITE_AXE;
    }

    public static int getSlotWithShovel(Inventory inventory) {
        if (inventory == null) return -1;

        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);

            if (item != null && isShovel(item.getType())) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isShovel(Material material) {
        return material == Material.WOODEN_SHOVEL ||
                material == Material.STONE_SHOVEL ||
                material == Material.IRON_SHOVEL ||
                material == Material.GOLDEN_SHOVEL ||
                material == Material.DIAMOND_SHOVEL ||
                material == Material.NETHERITE_SHOVEL;
    }

}
