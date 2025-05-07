package me.advait.mai.util;

import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.ItemStack;

public class ItemUtil {

    /**
     * @param item The weapon in question.
     * @return The attack damage induced by the weapon.
     */
    public static double getWeaponDamage(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return 1.0;

        double base = 1.0;

        if (item.getItemMeta() != null && item.getItemMeta().hasAttributeModifiers()) {
            var modifiers = item.getItemMeta().getAttributeModifiers(Attribute.ATTACK_DAMAGE);
            if (modifiers != null) {
                for (AttributeModifier modifier : modifiers) {
                    base += modifier.getAmount();
                }
            }
        }

        return base;
    }

}
