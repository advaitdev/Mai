package me.advait.mai.pathetic.movement.types;

import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import me.advait.mai.body.Humanoid;
import me.advait.mai.brain.action.mechanic.building.HumanoidBuildAction;
import me.advait.mai.pathetic.BlockClassifier;
import me.advait.mai.pathetic.PathContext;
import me.advait.mai.pathetic.capabilities.HumanoidCapabilities;
import me.advait.mai.pathetic.debug.PathDebugLog;
import me.advait.mai.pathetic.movement.*;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Bridge across a 1-block gap on the flat (dy=0): the destination feet/head
 * are clear but there's no floor below it. The bot places a support block
 * under the destination then walks onto it.
 */
public record BridgePlace() implements MovementType {

    @Override
    public String key() { return "bridge_place"; }

    @Override
    public boolean matches(PathPosition current, PathPosition previous, PathContext ctx) {
        int dx = current.getFlooredX() - previous.getFlooredX();
        int dy = current.getFlooredY() - previous.getFlooredY();
        int dz = current.getFlooredZ() - previous.getFlooredZ();

        if (dy != 0) return false;
        if (!((Math.abs(dx) == 1 && dz == 0) || (dx == 0 && Math.abs(dz) == 1))) return false;

        Material feet = ctx.materials().getMaterial(current);
        Material head = ctx.materials().getMaterial(current.getFlooredX(), current.getFlooredY() + 1, current.getFlooredZ());
        if (!BlockClassifier.isTraversable(feet) || !BlockClassifier.isTraversable(head)) return false;

        // The gap: no floor below the destination (WalkFlat would handle a real floor).
        Material below = ctx.materials().getMaterial(current.getFlooredX(), current.getFlooredY() - 1, current.getFlooredZ());
        return !BlockClassifier.isSolid(below);
    }

    @Override
    public double computeCost(PathPosition current, PathPosition previous, PathContext ctx) {
        return ctx.config().getWalkFlat() + ctx.capabilities().placementCost();
    }

    @Override
    public ExecutionHint executionHint(PathPosition current, PathPosition previous) {
        return ExecutionHint.bridge();
    }

    @Override
    public boolean isAllowed(HumanoidCapabilities caps) {
        return caps.canBridge();
    }

    @Override
    public boolean canReachAsEndpoint(PathPosition position, PathContext ctx) {
        // Bridging supports floating/elevated targets — never require a floor.
        Material feet = ctx.materials().getMaterial(position);
        Material head = ctx.materials().getMaterial(position.getFlooredX(), position.getFlooredY() + 1, position.getFlooredZ());
        return BlockClassifier.isTraversable(feet) && BlockClassifier.isTraversable(head);
    }

    @Override
    public List<PathPosition> toPlace(PathPosition current, PathPosition previous, PathContext ctx) {
        return List.of(PathPosition.of(current.getFlooredX(), current.getFlooredY() - 1, current.getFlooredZ()));
    }

    @Override
    public MovementStatus tick(TickContext ctx) {
        int bx = (int) Math.floor(ctx.waypoint.x());
        int by = (int) ctx.waypoint.y();
        int bz = (int) Math.floor(ctx.waypoint.z());

        Material support = ctx.entity().getWorld().getBlockAt(bx, by - 1, bz).getType();
        if (!BlockClassifier.isSolid(support)) {
            // Approach the edge slowly, then place the support before stepping out.
            MovementExecutors.groundAccelerate(ctx, 1.0, false);
            if (MovementExecutors.horizontalDistance(ctx.current, ctx.waypoint) < 1.3) {
                Location target = new Location(ctx.entity().getWorld(), bx + 0.5, by - 1, bz + 0.5);
                if (!placeSupport(ctx.humanoid, target)) {
                    PathDebugLog.event("BRIDGE_FAILED no placeable block/support at (%d,%d,%d)", bx, by - 1, bz);
                    return MovementStatus.FAILED;
                }
                PathDebugLog.event("BRIDGE_PLACED at (%d,%d,%d)", bx, by - 1, bz);
            }
            return MovementStatus.RUNNING;
        }

        // Support is down — walk onto it.
        MovementExecutors.groundAccelerate(ctx, ctx.speedFactor > 1.0 ? ctx.speedFactor : 1.0, false);
        return MovementExecutors.reachedFully(ctx) ? MovementStatus.SUCCESS : MovementStatus.RUNNING;
    }

    @Override
    public boolean allowsAutoUnstick() {
        return false;
    }

    /** Ensures a placeable block is in hand and places it at {@code target}. */
    static boolean placeSupport(Humanoid humanoid, Location target) {
        ItemStack block = ensurePlaceableInHand(humanoid);
        if (block == null) return false;
        return HumanoidBuildAction.placeBlock(humanoid.getEntity(), target, block);
    }

    /**
     * Returns a placeable block in the main hand: the currently-held stack if
     * it's already a block, otherwise the cheapest (lowest placement-value)
     * block from the inventory swapped into hand. Null if none available.
     */
    static ItemStack ensurePlaceableInHand(Humanoid humanoid) {
        if (humanoid.getEquipment() != null) {
            ItemStack main = humanoid.getEquipment().getItemInMainHand();
            if (isPlaceable(main)) return main;
        }
        Inventory inv = humanoid.getInventory();
        ItemStack[] contents = inv.getContents();
        int bestSlot = -1;
        double bestValue = Double.MAX_VALUE;
        for (int i = 0; i < contents.length; i++) {
            if (!isPlaceable(contents[i])) continue;
            double value = BlockClassifier.placementValue(contents[i].getType());
            if (value < bestValue) { bestValue = value; bestSlot = i; }
        }
        if (bestSlot == -1) return null;
        humanoid.setItemInMainHand(bestSlot);
        return humanoid.getEquipment() != null ? humanoid.getEquipment().getItemInMainHand() : null;
    }

    private static boolean isPlaceable(ItemStack item) {
        return item != null && item.getAmount() > 0 && item.getType().isBlock() && !item.getType().isAir();
    }
}
