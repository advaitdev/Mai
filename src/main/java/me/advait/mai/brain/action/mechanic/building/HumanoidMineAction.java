package me.advait.mai.brain.action.mechanic.building;

import me.advait.mai.Mai;
import me.advait.mai.body.Humanoid;
import me.advait.mai.brain.action.mechanic.HumanoidAction;
import me.advait.mai.brain.action.event.HumanoidActionEvent;
import me.advait.mai.brain.action.event.HumanoidMineActionEvent;
import me.advait.mai.brain.action.result.HumanoidActionMessage;
import me.advait.mai.brain.action.result.HumanoidActionResult;
import me.advait.mai.npc.MannequinBlockBreaker;
import me.advait.mai.util.InventoryUtil;
import me.advait.mai.util.LocationUtil;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.CompletableFuture;

public class HumanoidMineAction extends HumanoidAction {

    private final Block block;
    private final boolean ignoreRequiredTool;

    public HumanoidMineAction(Humanoid humanoid, Block block, boolean ignoreRequiredTool) {
        super(humanoid);
        this.block = block;
        this.ignoreRequiredTool = ignoreRequiredTool;
    }

    @Override
    protected void perform(CompletableFuture<HumanoidActionResult> resultFuture) {
        if (block == null) {
            resultFuture.complete(new HumanoidActionResult(false, HumanoidActionMessage.MINE_MESSAGE_FAILURE_NULL));
            return;
        }
        if (humanoid.getEntity() == null) {
            resultFuture.complete(new HumanoidActionResult(false, HumanoidActionMessage.NPC_IS_NULL));
            return;
        }

        Inventory inventory = humanoid.getInventory();
        Material material = block.getType();
        int itemSlot = -1;

        LocationUtil.faceLocation(humanoid.getEntity(), block.getLocation());

        if (!block.getType().isSolid() || block.isLiquid() || block.getType().getHardness() < 0) {
            resultFuture.complete(new HumanoidActionResult(false, HumanoidActionMessage.MINE_MESSAGE_FAILURE_UNBREAKABLE));
            return;
        }

        if (!ignoreRequiredTool) {
            if (Tag.MINEABLE_AXE.isTagged(material)) itemSlot = InventoryUtil.getSlotWithAxe(inventory);
            else if (Tag.MINEABLE_PICKAXE.isTagged(material)) itemSlot = InventoryUtil.getSlotWithPickaxe(inventory);
            else if (Tag.MINEABLE_SHOVEL.isTagged(material)) itemSlot = InventoryUtil.getSlotWithShovel(inventory);

            if (itemSlot == -1) {
                resultFuture.complete(new HumanoidActionResult(false, HumanoidActionMessage.MINE_MESSAGE_FAILURE_NO_TOOL));
                return;
            }

            ItemStack tool = humanoid.getInventory().getItem(itemSlot);
            humanoid.setItemInMainHand(itemSlot);

            MannequinBlockBreaker.start(Mai.getInstance(), humanoid.getEntity(), block, tool, resultFuture);
        } else {
            ItemStack tool = humanoid.getEquipment() != null ? humanoid.getEquipment().getItemInMainHand() : null;
            MannequinBlockBreaker.start(Mai.getInstance(), humanoid.getEntity(), block, tool, resultFuture);
        }
    }

    @Override
    protected HumanoidActionEvent getEvent() {
        return new HumanoidMineActionEvent(humanoid, block);
    }

}
