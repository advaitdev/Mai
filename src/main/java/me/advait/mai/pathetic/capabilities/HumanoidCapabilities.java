package me.advait.mai.pathetic.capabilities;

import me.advait.mai.body.Humanoid;
import me.advait.mai.pathetic.BlockClassifier;
import me.advait.mai.pathetic.config.MovementConfig;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

/**
 * A snapshot of what a humanoid is capable of doing right now, derived
 * from its live inventory and state. Passed into the movement system so
 * every movement type (and the feasibility check) decides what's possible
 * based on real runtime state rather than static assumptions.
 *
 * <p>Capabilities are <b>immutable once captured</b>. A fresh snapshot
 * should be taken for each pathfinding request via {@link #from(Humanoid,
 * MovementConfig)}.
 *
 * <p>For mining/placing pathfinding the snapshot also carries pre-extracted,
 * thread-safe numbers (best-tool dig speed + durability cost per category,
 * cheapest placement cost) so the async cost processor can price mine/place
 * moves without touching the live inventory off the main thread.
 *
 * <p>To add a new capability:
 * <ol>
 *   <li>Add a field to this record.</li>
 *   <li>Populate it in {@link #from} by querying the humanoid.</li>
 *   <li>Reference it from the relevant movement type's {@code isAllowed}
 *       or {@code computeCost} method.</li>
 * </ol>
 */
public record HumanoidCapabilities(
        boolean canJump,
        boolean canSprint,
        boolean canClimb,
        boolean canSwim,
        boolean canBridge,
        boolean canMine,
        int maxFallHeight,
        ToolInfo pickaxe,
        ToolInfo axe,
        ToolInfo shovel,
        double placementCost
) {

    /**
     * Pre-extracted tool numbers for cost calculation.
     *
     * @param speed          vanilla destroy-speed multiplier of the tool tier
     * @param durabilityCost tick-equivalent cost of one use's durability wear
     */
    public record ToolInfo(double speed, double durabilityCost) {}

    /**
     * Captures a live capability snapshot from a humanoid. Must be called
     * on the main thread since it reads from the entity's inventory.
     */
    public static HumanoidCapabilities from(Humanoid humanoid, MovementConfig config) {
        Inventory inv = humanoid.getInventory();
        EntityEquipment eq = humanoid.getEquipment();

        ToolInfo pick = bestTool(inv, eq, Tag.ITEMS_PICKAXES, config);
        ToolInfo axe = bestTool(inv, eq, Tag.ITEMS_AXES, config);
        ToolInfo shovel = bestTool(inv, eq, Tag.ITEMS_SHOVELS, config);
        boolean canMine = pick != null || axe != null || shovel != null;

        double placementCost = cheapestPlacementCost(inv, eq, config);

        return new HumanoidCapabilities(
                true,                           // canJump — always true in vanilla
                true,                           // canSprint — no hunger tracking for bots yet
                true,                           // canClimb — ladders/vines need no items
                true,                           // canSwim — swimming needs no items
                placementCost < config.getCostInf(),
                canMine,
                config.getMaxFallHeight(),
                pick, axe, shovel, placementCost
        );
    }

    /**
     * A conservative default for contexts that don't have a humanoid
     * (e.g. raw pathfinding queries). Assumes only the base walking
     * abilities — no bridging, no mining.
     */
    public static HumanoidCapabilities defaults(MovementConfig config) {
        return new HumanoidCapabilities(
                true, true, true, true,
                false, false,
                config.getMaxFallHeight(),
                null, null, null, config.getCostInf()
        );
    }

    /**
     * Tick cost to break {@code block} with this bot's best applicable tool,
     * including a durability-wear penalty. {@link MovementConfig#getCostInf()}
     * for unbreakable/protected blocks.
     */
    public double breakCost(Material block, MovementConfig config) {
        if (!BlockClassifier.isBreakable(block)) return config.getCostInf();
        float hardness = BlockClassifier.hardness(block);
        if (hardness < 0) return config.getCostInf();
        if (hardness == 0) return 1.0;

        ToolInfo tool = toolFor(block);
        boolean correct = tool != null;
        double speed = correct ? tool.speed() : 1.0;
        double divisor = correct ? config.getMineCorrectToolDivisor() : config.getMineWrongToolDivisor();
        double ticks = hardness * divisor / speed;
        double durCost = correct ? tool.durabilityCost() : 0.0;
        return ticks + durCost;
    }

    /** The tool category this bot would use for {@code block}, or null if none/hand. */
    private ToolInfo toolFor(Material block) {
        if (Tag.MINEABLE_PICKAXE.isTagged(block)) return pickaxe;
        if (Tag.MINEABLE_AXE.isTagged(block)) return axe;
        if (Tag.MINEABLE_SHOVEL.isTagged(block)) return shovel;
        return null;
    }

    // =========================================================================
    // Snapshot extraction (main thread)
    // =========================================================================

    private static ToolInfo bestTool(Inventory inv, EntityEquipment eq, Tag<Material> category, MovementConfig config) {
        ItemStack best = null;
        double bestSpeed = 0;
        for (ItemStack item : allItems(inv, eq)) {
            if (item == null || item.getAmount() == 0) continue;
            if (!category.isTagged(item.getType())) continue;
            double speed = toolSpeed(item.getType());
            if (speed > bestSpeed) { bestSpeed = speed; best = item; }
        }
        if (best == null) return null;
        return new ToolInfo(bestSpeed, durabilityCost(best, config));
    }

    private static double durabilityCost(ItemStack tool, MovementConfig config) {
        double value = toolValue(tool.getType());
        int max = tool.getType().getMaxDurability();
        int remaining = max;
        if (max > 0 && tool.getItemMeta() instanceof Damageable dmg && dmg.hasDamage()) {
            remaining = Math.max(1, max - dmg.getDamage());
        }
        return config.getMineDurabilityWeight() * value / Math.max(1, remaining);
    }

    private static double cheapestPlacementCost(Inventory inv, EntityEquipment eq, MovementConfig config) {
        double best = config.getCostInf();
        for (ItemStack item : allItems(inv, eq)) {
            if (!isPlaceableBlock(item)) continue;
            double scarcity = config.getPlaceValueWeight()
                    * BlockClassifier.placementValue(item.getType())
                    / (Math.log(item.getAmount() + 2) / Math.log(2));
            best = Math.min(best, scarcity);
        }
        if (best >= config.getCostInf()) return config.getCostInf();
        return config.getPlaceBlockCost() + config.getPlaceEquipCost() + best;
    }

    private static Iterable<ItemStack> allItems(Inventory inv, EntityEquipment eq) {
        java.util.List<ItemStack> items = new java.util.ArrayList<>();
        if (eq != null) {
            items.add(eq.getItemInMainHand());
            items.add(eq.getItemInOffHand());
        }
        if (inv != null) {
            for (ItemStack item : inv.getContents()) items.add(item);
        }
        return items;
    }

    private static double toolSpeed(Material tool) {
        String n = tool.name();
        if (n.startsWith("NETHERITE_")) return 9.0;
        if (n.startsWith("DIAMOND_")) return 8.0;
        if (n.startsWith("IRON_")) return 6.0;
        if (n.startsWith("STONE_")) return 4.0;
        if (n.startsWith("GOLDEN_")) return 12.0;
        if (n.startsWith("WOODEN_")) return 2.0;
        return 1.0;
    }

    private static double toolValue(Material tool) {
        String n = tool.name();
        if (n.startsWith("NETHERITE_")) return 120.0;
        if (n.startsWith("DIAMOND_")) return 40.0;
        if (n.startsWith("IRON_")) return 6.0;
        if (n.startsWith("GOLDEN_")) return 10.0;
        if (n.startsWith("STONE_")) return 2.0;
        return 1.0;
    }

    private static boolean isPlaceableBlock(ItemStack item) {
        return item != null && item.getAmount() > 0 && item.getType().isBlock() && !item.getType().isAir();
    }
}
