package me.advait.mai.brain.action.mechanic.building;

import me.advait.mai.body.Humanoid;
import me.advait.mai.brain.action.mechanic.HumanoidAction;
import me.advait.mai.brain.action.event.HumanoidActionEvent;
import me.advait.mai.brain.action.event.HumanoidBuildActionEvent;
import me.advait.mai.brain.action.result.HumanoidActionResult;
import de.bsommerfeld.pathetic.bukkit.provider.FailingNavigationPointProvider;
import me.advait.mai.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.CompletableFuture;

public class HumanoidBuildAction extends HumanoidAction {

    private final Location location;
    private final ItemStack block;

    public HumanoidBuildAction(Humanoid humanoid, Location location, ItemStack block) {
        super(humanoid);
        this.location = location;
        this.block = block;
    }

    @Override
    protected void perform(CompletableFuture<HumanoidActionResult> resultFuture) {
        if (location == null) {
            resultFuture.complete(new HumanoidActionResult(false, "Target location is null."));
            return;
        }
        if (!LocationUtil.isBuildable(location)) {
            resultFuture.complete(new HumanoidActionResult(false, "Cannot place a block here."));
            return;
        }
        if (humanoid.getEntity() == null) {
            resultFuture.complete(new HumanoidActionResult(false, "Mannequin not spawned."));
            return;
        }

        LocationUtil.faceLocation(humanoid.getEntity(), location);

        if (!LocationUtil.isBlockTargetable(humanoid.getEntity().getLocation(), location.getBlock())) {
            resultFuture.complete(new HumanoidActionResult(false, "Block is too far away to reach."));
            return;
        }

        ItemStack mainHand = humanoid.getEquipment() != null ? humanoid.getEquipment().getItemInMainHand() : null;
        if (mainHand == null || !mainHand.equals(block)) {
            resultFuture.complete(new HumanoidActionResult(false, "Required block is not in main hand."));
            return;
        }
        if (!block.getType().isBlock()) {
            resultFuture.complete(new HumanoidActionResult(false, "Held item is not a placeable block."));
            return;
        }

        if (!placeBlock(humanoid.getEntity(), location, block)) {
            resultFuture.complete(new HumanoidActionResult(false, "No solid face to place against."));
            return;
        }

        resultFuture.complete(new HumanoidActionResult(true, "Block placed successfully."));
    }

    /**
     * Places one block of {@code held}'s type at {@code location}, requiring a
     * solid neighbour to place against (no floating placements), swinging the
     * arm, decrementing the held stack and writing it back to the main hand,
     * and invalidating the pathfinder's snapshot cache so the bot's own edit is
     * visible to the next replan. Returns false if there's no solid face to
     * build against. Main thread only.
     */
    public static boolean placeBlock(LivingEntity entity, Location location, ItemStack held) {
        // A player's click: against a solid neighbour whose face the eyes
        // can see, in reach, not into a hitbox, respecting the click delay.
        Block target = location.getBlock();
        for (BlockFace face : new BlockFace[]{
                BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH,
                BlockFace.EAST, BlockFace.WEST, BlockFace.UP}) {
            Block against = target.getRelative(face);
            if (!against.getType().isSolid()) continue;
            if (me.advait.mai.body.Hands.placeBlock(entity, against, face.getOppositeFace())) return true;
        }
        return false;
    }

    private static boolean hasSolidNeighbour(Block block) {
        for (BlockFace face : new BlockFace[]{
                BlockFace.DOWN, BlockFace.UP, BlockFace.NORTH,
                BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            if (block.getRelative(face).getType().isSolid()) return true;
        }
        return false;
    }

    @Override
    protected HumanoidActionEvent createEvent() {
        return new HumanoidBuildActionEvent(humanoid, location, block);
    }
}
