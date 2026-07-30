package me.advait.mai.pathetic.movement.types;

import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import me.advait.mai.Mai;
import me.advait.mai.body.Humanoid;
import me.advait.mai.brain.action.mechanic.building.MannequinBlockBreaker;
import me.advait.mai.brain.action.result.HumanoidActionResult;
import me.advait.mai.pathetic.BlockClassifier;
import me.advait.mai.pathetic.PathContext;
import me.advait.mai.pathetic.capabilities.HumanoidCapabilities;
import me.advait.mai.pathetic.debug.PathDebugLog;
import me.advait.mai.pathetic.movement.*;
import me.advait.mai.util.InventoryUtil;
import me.advait.mai.util.LocationUtil;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Tunnel straight through a 1-block-forward obstruction (dy=0): the
 * destination column's feet and/or head are solid-but-breakable, with solid
 * ground below to stand on once cleared. The bot mines the obstructing
 * block(s) then walks in.
 */
public record MineThrough() implements MovementType {

    @Override
    public String key() { return "mine_through"; }

    @Override
    public boolean matches(PathPosition current, PathPosition previous, PathContext ctx) {
        int dx = current.getFlooredX() - previous.getFlooredX();
        int dy = current.getFlooredY() - previous.getFlooredY();
        int dz = current.getFlooredZ() - previous.getFlooredZ();

        if (dy != 0) return false;
        if (!((Math.abs(dx) == 1 && dz == 0) || (dx == 0 && Math.abs(dz) == 1))) return false;

        Material below = ctx.materials().getMaterial(current.getFlooredX(), current.getFlooredY() - 1, current.getFlooredZ());
        if (!BlockClassifier.isSolid(below)) return false;   // need a floor to stand on

        Material feet = ctx.materials().getMaterial(current);
        Material head = ctx.materials().getMaterial(current.getFlooredX(), current.getFlooredY() + 1, current.getFlooredZ());

        boolean feetBlocked = BlockClassifier.isSolid(feet);
        boolean headBlocked = BlockClassifier.isSolid(head);
        if (!feetBlocked && !headBlocked) return false;       // WalkFlat already handles this

        // Every solid obstructor must be breakable.
        if (feetBlocked && !BlockClassifier.isBreakable(feet)) return false;
        if (headBlocked && !BlockClassifier.isBreakable(head)) return false;
        return true;
    }

    @Override
    public double computeCost(PathPosition current, PathPosition previous, PathContext ctx) {
        Material below = ctx.materials().getMaterial(current.getFlooredX(), current.getFlooredY() - 1, current.getFlooredZ());
        double cost = ctx.config().getWalkFlat() * WalkFlat.blockCostMultiplier(below, ctx.config());

        Material feet = ctx.materials().getMaterial(current);
        Material head = ctx.materials().getMaterial(current.getFlooredX(), current.getFlooredY() + 1, current.getFlooredZ());
        if (BlockClassifier.isSolid(feet)) cost += ctx.capabilities().breakCost(feet, ctx.config());
        if (BlockClassifier.isSolid(head)) cost += ctx.capabilities().breakCost(head, ctx.config());
        return cost;
    }

    @Override
    public ExecutionHint executionHint(PathPosition current, PathPosition previous) {
        return ExecutionHint.mine();
    }

    @Override
    public boolean isAllowed(HumanoidCapabilities caps) {
        return caps.canMine();
    }

    @Override
    public boolean canReachAsEndpoint(PathPosition position, PathContext ctx) {
        Material below = ctx.materials().getMaterial(position.getFlooredX(), position.getFlooredY() - 1, position.getFlooredZ());
        if (!BlockClassifier.isSolid(below)) return false;
        Material feet = ctx.materials().getMaterial(position);
        Material head = ctx.materials().getMaterial(position.getFlooredX(), position.getFlooredY() + 1, position.getFlooredZ());
        return clearableOrOpen(feet) && clearableOrOpen(head);
    }

    private static boolean clearableOrOpen(Material m) {
        return BlockClassifier.isTraversable(m) || BlockClassifier.isBreakable(m);
    }

    @Override
    public List<PathPosition> toBreak(PathPosition current, PathPosition previous, PathContext ctx) {
        return List.of(current, PathPosition.of(current.getFlooredX(), current.getFlooredY() + 1, current.getFlooredZ()));
    }

    @Override
    public MovementStatus tick(TickContext ctx) {
        int bx = (int) Math.floor(ctx.waypoint.x());
        int by = (int) ctx.waypoint.y();
        int bz = (int) Math.floor(ctx.waypoint.z());

        MovementStatus mining = driveMining(ctx, bx, by, bz);
        if (mining != null) return mining;

        // Column is clear — walk into it.
        boolean sprinting = ctx.speedFactor > 1.0;
        MovementExecutors.groundAccelerate(ctx, ctx.speedFactor, sprinting);
        return MovementExecutors.reachedFully(ctx) ? MovementStatus.SUCCESS : MovementStatus.RUNNING;
    }

    @Override
    public boolean safeToCancel(TickContext ctx) {
        return ctx.subAction == null || ctx.subAction.isDone();
    }

    @Override
    public boolean allowsAutoUnstick() {
        return false;   // mining owns its own timing; a stray jump would break LOS
    }

    // =========================================================================
    // Shared mining drive (reused by MineStepUp)
    // =========================================================================

    /**
     * Drives breaking of any solid obstructors at the feet (by/bz) and head
     * (by+1) of the target column. Returns a status while still working
     * (RUNNING) or on failure (FAILED), or {@code null} once the column is
     * clear so the caller can proceed (walk/step in).
     */
    static MovementStatus driveMining(TickContext ctx, int bx, int by, int bz) {
        // A break is in flight: wait for it.
        if (ctx.subAction != null) {
            if (!ctx.subAction.isDone()) return MovementStatus.RUNNING;
            HumanoidActionResult result = ctx.subAction.getNow(null);
            ctx.subAction = null;
            if (result != null && !result.success()) {
                PathDebugLog.event("MINE_FAILED at (%d,%d,%d): %s", bx, by, bz,
                        result.message());
                return MovementStatus.FAILED;
            }
        }

        Block feet = ctx.entity().getWorld().getBlockAt(bx, by, bz);
        Block head = ctx.entity().getWorld().getBlockAt(bx, by + 1, bz);
        Block obstructor = BlockClassifier.isSolid(feet.getType()) ? feet
                : BlockClassifier.isSolid(head.getType()) ? head : null;

        if (obstructor == null) return null;   // clear

        // Stop drifting while we mine, then start a break.
        ctx.entity().setVelocity(new org.bukkit.util.Vector(0, ctx.entity().getVelocity().getY(), 0));
        if (!startBreak(ctx, obstructor)) return MovementStatus.FAILED;
        return MovementStatus.RUNNING;
    }

    /** Equips the right tool and kicks off a {@link MannequinBlockBreaker}. */
    private static boolean startBreak(TickContext ctx, Block block) {
        Humanoid humanoid = ctx.humanoid;
        LocationUtil.faceLocation(ctx.entity(), block.getLocation());

        Material material = block.getType();
        int toolSlot = -1;
        if (Tag.MINEABLE_AXE.isTagged(material)) toolSlot = InventoryUtil.findTool(humanoid.getInventory(), Tag.ITEMS_AXES);
        else if (Tag.MINEABLE_PICKAXE.isTagged(material)) toolSlot = InventoryUtil.findTool(humanoid.getInventory(), Tag.ITEMS_PICKAXES);
        else if (Tag.MINEABLE_SHOVEL.isTagged(material)) toolSlot = InventoryUtil.findTool(humanoid.getInventory(), Tag.ITEMS_SHOVELS);

        ItemStack tool = null;
        if (toolSlot != -1) {
            humanoid.setItemInMainHand(toolSlot);
            tool = humanoid.getEquipment() != null ? humanoid.getEquipment().getItemInMainHand() : null;
        }

        CompletableFuture<HumanoidActionResult> future = new CompletableFuture<>();
        PathDebugLog.event("MINE_START %s at (%d,%d,%d)", material,
                block.getX(), block.getY(), block.getZ());
        MannequinBlockBreaker.start(Mai.getInstance(), ctx.entity(), block, tool, future);
        ctx.subAction = future;
        return true;
    }
}
