package me.advait.mai.brain.action;

import me.advait.mai.Mai;
import me.advait.mai.body.Humanoid;
import me.advait.mai.brain.action.event.HumanoidActionEvent;
import me.advait.mai.brain.action.event.HumanoidMineActionEvent;
import me.advait.mai.brain.action.result.HumanoidActionMessage;
import me.advait.mai.brain.action.result.HumanoidActionResult;
import me.advait.mai.npc.HumanoidUtil;
import me.advait.mai.util.InventoryUtil;
import net.citizensnpcs.api.ai.tree.BehaviorStatus;
import net.citizensnpcs.api.npc.BlockBreaker;
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
    private final boolean forceMine;

    /**
     * @param forceMine Decides if the humanoid should continue mining anyway, even without the appropriate tool.
     */

    public HumanoidMineAction(Humanoid humanoid, Block block, boolean forceMine) {
        super(humanoid);
        this.block = block;
        this.forceMine = forceMine;
    }

    @Override
    protected void perform(CompletableFuture<HumanoidActionResult> resultFuture) {
        Inventory inventory = humanoid.getInventory();
        Material material = block.getType();
        int itemSlot = -1;

        if (!forceMine) {
            if (Tag.MINEABLE_AXE.isTagged(material)) itemSlot = InventoryUtil.getSlotWithAxe(inventory);
            else if (Tag.MINEABLE_PICKAXE.isTagged(material)) itemSlot = InventoryUtil.getSlotWithPickaxe(inventory);
            else if (Tag.MINEABLE_SHOVEL.isTagged(material)) itemSlot = InventoryUtil.getSlotWithShovel(inventory);

            if (itemSlot == -1) {
                resultFuture.complete(new HumanoidActionResult(false, HumanoidActionMessage.MINE_MESSAGE_FAILURE_NO_TOOL));
                return;
            }

            ItemStack tool = humanoid.getInventory().getItem(itemSlot);
            BlockBreaker.BlockBreakerConfiguration blockBreakerConfig = new BlockBreaker.BlockBreakerConfiguration();
            blockBreakerConfig.item(tool);

            Util.faceLocation(humanoid.getNpc().getEntity(), block.getLocation());

            BlockBreaker blockBreaker = humanoid.getNpc().getBlockBreaker(block, blockBreakerConfig);
            // TODO: Make NPC's arm swing
            if (blockBreaker.shouldExecute()) {
                BlockBreakerTaskRunnable run = new BlockBreakerTaskRunnable(blockBreaker);
                run.taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(JavaPlugin.getPlugin(Mai.class), run, 0 ,1);
            }

        } else {

        }

    }

    private static class BlockBreakerTaskRunnable implements Runnable {
        private int taskId;
        private final BlockBreaker breaker;

        public BlockBreakerTaskRunnable(BlockBreaker breaker) {
            this.breaker = breaker;
        }

        @Override
        public void run() {
            if (breaker.run() != BehaviorStatus.RUNNING) {
                Bukkit.getScheduler().cancelTask(taskId);
                breaker.reset();
            }
        }
    }

    @Override
    protected HumanoidActionEvent getEvent() {
        return new HumanoidMineActionEvent(humanoid, block);
    }

}
