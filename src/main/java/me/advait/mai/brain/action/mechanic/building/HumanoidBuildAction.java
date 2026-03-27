package me.advait.mai.brain.action.mechanic.building;

import me.advait.mai.body.Humanoid;
import me.advait.mai.brain.action.mechanic.HumanoidAction;
import me.advait.mai.brain.action.event.HumanoidActionEvent;
import me.advait.mai.brain.action.event.HumanoidBuildActionEvent;
import me.advait.mai.brain.action.result.HumanoidActionResult;
import me.advait.mai.util.LocationUtil;
import org.bukkit.Location;
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

        humanoid.getEntity().swingMainHand();
        location.getBlock().setType(block.getType());

        block.setAmount(block.getAmount() - 1);
        if (humanoid.getEquipment() != null) {
            humanoid.getEquipment().setItemInMainHand(block);
        }

        resultFuture.complete(new HumanoidActionResult(true, "Block placed successfully."));
    }

    @Override
    protected HumanoidActionEvent createEvent() {
        return new HumanoidBuildActionEvent(humanoid, location, block);
    }
}
