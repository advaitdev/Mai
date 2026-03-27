package me.advait.mai.brain.action.mechanic.building;

import me.advait.mai.Mai;
import me.advait.mai.body.Humanoid;
import me.advait.mai.brain.action.mechanic.HumanoidAction;
import me.advait.mai.brain.action.event.HumanoidActionEvent;
import me.advait.mai.brain.action.event.HumanoidMineActionEvent;
import me.advait.mai.brain.action.result.HumanoidActionResult;
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
            resultFuture.complete(new HumanoidActionResult(false, "Target block is null."));
            return;
        }
        if (humanoid.getEntity() == null) {
            resultFuture.complete(new HumanoidActionResult(false, "Mannequin not spawned."));
            return;
        }

        LocationUtil.faceLocation(humanoid.getEntity(), block.getLocation());

        if (!block.getType().isSolid() || block.isLiquid() || block.getType().getHardness() < 0) {
            resultFuture.complete(new HumanoidActionResult(false, "Block is unbreakable or not solid."));
            return;
        }

        if (!ignoreRequiredTool) {
            Inventory inventory = humanoid.getInventory();
            Material material = block.getType();
            int toolSlot = -1;

            if (Tag.MINEABLE_AXE.isTagged(material)) toolSlot = InventoryUtil.findTool(inventory, Tag.ITEMS_AXES);
            else if (Tag.MINEABLE_PICKAXE.isTagged(material)) toolSlot = InventoryUtil.findTool(inventory, Tag.ITEMS_PICKAXES);
            else if (Tag.MINEABLE_SHOVEL.isTagged(material)) toolSlot = InventoryUtil.findTool(inventory, Tag.ITEMS_SHOVELS);

            if (toolSlot == -1) {
                resultFuture.complete(new HumanoidActionResult(false, "No appropriate tool found."));
                return;
            }

            humanoid.setItemInMainHand(toolSlot);
            ItemStack tool = humanoid.getInventory().getItem(toolSlot);
            MannequinBlockBreaker.start(Mai.getInstance(), humanoid.getEntity(), block, tool, resultFuture);
        } else {
            ItemStack tool = humanoid.getEquipment() != null ? humanoid.getEquipment().getItemInMainHand() : null;
            MannequinBlockBreaker.start(Mai.getInstance(), humanoid.getEntity(), block, tool, resultFuture);
        }
    }

    @Override
    protected HumanoidActionEvent createEvent() {
        return new HumanoidMineActionEvent(humanoid, block);
    }
}
