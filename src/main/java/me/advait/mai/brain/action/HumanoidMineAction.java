package me.advait.mai.brain.action;

import me.advait.mai.Mai;
import me.advait.mai.body.Humanoid;
import me.advait.mai.brain.action.event.HumanoidActionEvent;
import me.advait.mai.brain.action.event.HumanoidMineActionEvent;
import me.advait.mai.brain.action.result.HumanoidActionMessage;
import me.advait.mai.brain.action.result.HumanoidActionResult;
import me.advait.mai.util.InventoryUtil;
import me.advait.mai.brain.action.runnable.HumanoidBlockBreakerRunnable;
import net.citizensnpcs.api.npc.BlockBreaker;
import net.citizensnpcs.api.trait.trait.Equipment;
import net.citizensnpcs.util.Util;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;

public class HumanoidMineAction extends HumanoidAction {

    private final Block block;
    private final boolean ignoreRequiredTool;

    /**
     * @param ignoreRequiredTool Decides if the humanoid should continue mining anyway, even without the appropriate tool.
     */

    public HumanoidMineAction(Humanoid humanoid, Block block, boolean ignoreRequiredTool) {
        super(humanoid);
        this.block = block;
        this.ignoreRequiredTool = ignoreRequiredTool;
    }

    @Override
    protected void perform(CompletableFuture<HumanoidActionResult> resultFuture) {
        if (block == null) {
            resultFuture.complete(new HumanoidActionResult(false, HumanoidActionMessage.MINE_MESSAGE_FAILURE_UNBREAKABLE));
            return;
        }

        Inventory inventory = humanoid.getInventory();
        Material material = block.getType();
        int itemSlot = -1;

        Util.faceLocation(humanoid.getNpc().getEntity(), block.getLocation());

        if (!block.isSolid() || block.isLiquid() || block.getType().getHardness() == -1) {
            resultFuture.complete(new HumanoidActionResult(false, HumanoidActionMessage.MINE_MESSAGE_FAILURE_UNBREAKABLE));
            return;
        }

        // "Using tool" code
        if (!ignoreRequiredTool) {
            if (Tag.MINEABLE_AXE.isTagged(material)) itemSlot = InventoryUtil.getSlotWithAxe(inventory);
            else if (Tag.MINEABLE_PICKAXE.isTagged(material)) itemSlot = InventoryUtil.getSlotWithPickaxe(inventory);
            else if (Tag.MINEABLE_SHOVEL.isTagged(material)) itemSlot = InventoryUtil.getSlotWithShovel(inventory);

            if (itemSlot == -1) {
                resultFuture.complete(new HumanoidActionResult(false, HumanoidActionMessage.MINE_MESSAGE_FAILURE_NO_TOOL));
                return;
            }

            ItemStack tool = humanoid.getInventory().getItem(itemSlot);
            var blockBreakerConfig = new BlockBreaker.BlockBreakerConfiguration();
            blockBreakerConfig.item(tool);

            BlockBreaker blockBreaker = humanoid.getNpc().getBlockBreaker(block, blockBreakerConfig);

            if (blockBreaker.shouldExecute()) {
                HumanoidBlockBreakerRunnable run = new HumanoidBlockBreakerRunnable(blockBreaker, humanoid.getNpc(), resultFuture);
                run.runTaskTimer(Mai.getInstance(),0L, 1L);
            }

            // "Not using tool" code
        } else {
            var blockBreakerConfig = new BlockBreaker.BlockBreakerConfiguration();
            blockBreakerConfig.item(humanoid.getEquipment().get(Equipment.EquipmentSlot.HAND));

            BlockBreaker blockBreaker = humanoid.getNpc().getBlockBreaker(block, blockBreakerConfig);

            if (blockBreaker.shouldExecute()) {
                HumanoidBlockBreakerRunnable run = new HumanoidBlockBreakerRunnable(blockBreaker, humanoid.getNpc(), resultFuture);
                run.runTaskTimer(Mai.getInstance(),0L, 1L);
            }
        }
    }


    @Override
    protected HumanoidActionEvent getEvent() {
        return new HumanoidMineActionEvent(humanoid, block);
    }

}
