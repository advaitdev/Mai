package me.advait.mai.brain.action.mechanic.building;

import me.advait.mai.body.Humanoid;
import me.advait.mai.brain.action.mechanic.HumanoidAction;
import me.advait.mai.brain.action.event.HumanoidActionEvent;
import me.advait.mai.brain.action.event.HumanoidBuildActionEvent;
import me.advait.mai.brain.action.result.HumanoidActionMessage;
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
            resultFuture.complete(new HumanoidActionResult(false, HumanoidActionMessage.BUILD_MESSAGE_FAILURE_NULL));
            return;
        }
        if (!LocationUtil.isBuildable(location)) {
            resultFuture.complete(new HumanoidActionResult(false, HumanoidActionMessage.BUILD_MESSAGE_FAILURE_INVALID_LOCATION));
            return;
        }
        if (humanoid.getEntity() == null) {
            resultFuture.complete(new HumanoidActionResult(false, HumanoidActionMessage.NPC_IS_NULL));
            return;
        }

        LocationUtil.faceLocation(humanoid.getEntity(), location);

        if (!LocationUtil.isBlockTargetable(humanoid.getEntity().getLocation(), location.getBlock())) {
            resultFuture.complete(new HumanoidActionResult(false, HumanoidActionMessage.BUILD_MESSAGE_FAILURE_TOO_FAR));
            return;
        }

        ItemStack mainHand = humanoid.getEquipment() != null ? humanoid.getEquipment().getItemInMainHand() : null;
        if (mainHand == null || !mainHand.equals(block)) {
            resultFuture.complete(new HumanoidActionResult(false, HumanoidActionMessage.BUILD_MESSAGE_FAILURE_NOT_HOLDING_BLOCK));
            return;
        }
        if (!block.getType().isBlock()) {
            resultFuture.complete(new HumanoidActionResult(false, HumanoidActionMessage.BUILD_MESSAGE_FAILURE_NOT_PLACEABLE));
            return;
        }

        humanoid.getEntity().swingMainHand();
        location.getBlock().setType(block.getType());

        block.setAmount(block.getAmount() - 1);
        if (humanoid.getEquipment() != null) {
            humanoid.getEquipment().setItemInMainHand(block);
        }

        resultFuture.complete(new HumanoidActionResult(true, HumanoidActionMessage.BUILD_MESSAGE_SUCCESS));
    }

    @Override
    protected HumanoidActionEvent getEvent() {
        return new HumanoidBuildActionEvent(humanoid, location, block);
    }

}
