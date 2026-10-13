package me.advait.mai.util;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

public class ItemUtil {

    /** Vanilla player base values the item modifiers add to. */
    private static final double BASE_ATTACK_DAMAGE = 1.0;
    private static final double BASE_ATTACK_SPEED = 4.0;

    /**
     * @param item The weapon in question.
     * @return The attack damage induced by the weapon (bare hand = 1).
     */
    public static double getWeaponDamage(ItemStack item) {
        return BASE_ATTACK_DAMAGE + modifierSum(item, Attribute.ATTACK_DAMAGE);
    }

    /** Attacks per second at full charge (bare hand = 4, sword = 1.6, axe ~0.8-1). */
    public static double getAttackSpeed(ItemStack item) {
        return Math.max(0.1, BASE_ATTACK_SPEED + modifierSum(item, Attribute.ATTACK_SPEED));
    }

    /** Sharpness bonus at full charge: 0.5 * level + 0.5 (0 without Sharpness). */
    public static double getEnchantDamage(ItemStack item) {
        if (item == null || item.getType().isAir()) return 0;
        int lvl = item.getEnchantmentLevel(Enchantment.SHARPNESS);
        return lvl > 0 ? 0.5 * lvl + 0.5 : 0;
    }

    /**
     * Sums the item's flat ADD_NUMBER modifiers for {@code attribute}. Reads
     * the effective attribute-modifiers component, which includes the item
     * type's defaults (a plain diamond sword's +6 damage lives there, not in
     * its ItemMeta, which is why reading the meta alone returned 1 for every
     * unmodified weapon).
     */
    private static double modifierSum(ItemStack item, Attribute attribute) {
        if (item == null || item.getType().isAir()) return 0;
        ItemAttributeModifiers mods = item.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (mods == null) return 0;
        double total = 0;
        for (ItemAttributeModifiers.Entry e : mods.modifiers()) {
            if (!e.attribute().equals(attribute)) continue;
            AttributeModifier m = e.modifier();
            if (m.getOperation() == AttributeModifier.Operation.ADD_NUMBER) total += m.getAmount();
        }
        return total;
    }

}
