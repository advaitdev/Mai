package me.advait.mai.util;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Random;

public final class DurabilityUtil {

    private static final Random RANDOM = new Random();

    /**
     * Decreases the durability of a tool by 1, considering Minecraft's Unbreaking calculations.
     *
     * @param item The item whose durability should be decreased.
     */
    public static void decreaseToolDurability(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) return;

        int currentDamage = damageable.getDamage();
        int maxDurability = item.getType().getMaxDurability();

        // Check if the item has Unbreaking enchantment
        int unbreakingLevel = item.getEnchantmentLevel(Enchantment.UNBREAKING);

        // Perform Minecraft's Unbreaking calculation
        boolean shouldDecreaseDurability = true;
        if (unbreakingLevel > 0) {
            int randomValue = RANDOM.nextInt(unbreakingLevel + 1);
            if (randomValue != 0) {
                shouldDecreaseDurability = false;
            }
        }

        if (shouldDecreaseDurability) {
            currentDamage++;
            if (currentDamage >= maxDurability) {
                item.setAmount(0);
                return;
            }

            damageable.setDamage(currentDamage);
            item.setItemMeta(meta);
        }
    }
}
