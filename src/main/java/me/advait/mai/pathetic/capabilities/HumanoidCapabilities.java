package me.advait.mai.pathetic.capabilities;

import me.advait.mai.body.Humanoid;
import me.advait.mai.pathetic.config.MovementConfig;
import org.bukkit.Tag;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

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
 * <p>To add a new capability:
 * <ol>
 *   <li>Add a field to this record.</li>
 *   <li>Populate it in {@link #from} by querying the humanoid.</li>
 *   <li>Reference it from the relevant movement type's {@code isAllowed}
 *       or {@code canReachAsEndpoint} method.</li>
 * </ol>
 */
public record HumanoidCapabilities(
        boolean canJump,
        boolean canSprint,
        boolean canClimb,
        boolean canSwim,
        boolean canBridge,
        boolean canMine,
        int maxFallHeight
) {

    /**
     * Captures a live capability snapshot from a humanoid. Must be called
     * on the main thread since it reads from the entity's inventory.
     */
    public static HumanoidCapabilities from(Humanoid humanoid, MovementConfig config) {
        Inventory inv = humanoid.getInventory();
        EntityEquipment eq = humanoid.getEquipment();

        return new HumanoidCapabilities(
                true,                           // canJump — always true in vanilla
                true,                           // canSprint — no hunger tracking for bots yet
                true,                           // canClimb — ladders/vines need no items
                true,                           // canSwim — swimming needs no items
                hasPlaceableBlock(inv, eq),
                hasMiningTool(inv, eq),
                config.getMaxFallHeight()
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
                config.getMaxFallHeight()
        );
    }

    private static boolean hasPlaceableBlock(Inventory inv, EntityEquipment eq) {
        if (eq != null) {
            if (isPlaceableBlock(eq.getItemInMainHand())) return true;
            if (isPlaceableBlock(eq.getItemInOffHand())) return true;
        }
        if (inv != null) {
            for (ItemStack item : inv.getContents()) {
                if (isPlaceableBlock(item)) return true;
            }
        }
        return false;
    }

    private static boolean hasMiningTool(Inventory inv, EntityEquipment eq) {
        if (eq != null) {
            if (isMiningTool(eq.getItemInMainHand())) return true;
            if (isMiningTool(eq.getItemInOffHand())) return true;
        }
        if (inv != null) {
            for (ItemStack item : inv.getContents()) {
                if (isMiningTool(item)) return true;
            }
        }
        return false;
    }

    private static boolean isPlaceableBlock(ItemStack item) {
        return item != null && item.getAmount() > 0 && item.getType().isBlock();
    }

    private static boolean isMiningTool(ItemStack item) {
        if (item == null || item.getAmount() == 0) return false;
        return Tag.ITEMS_PICKAXES.isTagged(item.getType())
                || Tag.ITEMS_AXES.isTagged(item.getType())
                || Tag.ITEMS_SHOVELS.isTagged(item.getType());
    }
}
