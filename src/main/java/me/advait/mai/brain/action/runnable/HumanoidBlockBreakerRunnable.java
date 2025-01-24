package me.advait.mai.brain.action.runnable;

import me.advait.mai.brain.action.result.HumanoidActionMessage;
import me.advait.mai.brain.action.result.HumanoidActionResult;
import me.advait.mai.util.DurabilityUtil;
import me.advait.mai.util.LocationUtil;
import net.citizensnpcs.api.ai.tree.BehaviorStatus;
import net.citizensnpcs.api.npc.BlockBreaker;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.util.PlayerAnimation;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.CompletableFuture;

public class HumanoidBlockBreakerRunnable extends BukkitRunnable {

    private final BlockBreaker breaker;
    private final BlockBreaker.BlockBreakerConfiguration breakerConfig;
    private final Block block;
    private final NPC npc;
    private final CompletableFuture<HumanoidActionResult> resultFuture;

    private ItemStack tool = null;

    // Constructor for "no tool" mode
    public HumanoidBlockBreakerRunnable(BlockBreaker breaker, BlockBreaker.BlockBreakerConfiguration breakerConfig, Block block, NPC npc, CompletableFuture<HumanoidActionResult> resultFuture) {
        this.breaker = breaker;
        this.breakerConfig = breakerConfig;
        this.block = block;
        this.npc = npc;
        this.resultFuture = resultFuture;
    }

    // Constructor for "tool" mode
    public HumanoidBlockBreakerRunnable(BlockBreaker breaker, BlockBreaker.BlockBreakerConfiguration breakerConfig, Block block, NPC npc, CompletableFuture<HumanoidActionResult> resultFuture, ItemStack tool) {
        this(breaker, breakerConfig, block, npc, resultFuture);
        this.tool = tool;
    }

    @Override
    public void run() {
        if (npc == null || npc.getEntity() == null) {
            resultFuture.complete(new HumanoidActionResult(false, HumanoidActionMessage.NPC_IS_NULL));
            cancel();
            return;
        }


        if (!LocationUtil.isBlockTargetable(npc.getStoredLocation(), block)) {
            resultFuture.complete(new HumanoidActionResult(false, HumanoidActionMessage.MINE_MESSAGE_FAILURE_TOO_FAR));
            cancel();
            return;
        }

        if (tool != null && !((Player) npc.getEntity()).getEquipment().getItemInMainHand().equals(tool)) {
            resultFuture.complete(new HumanoidActionResult(false, HumanoidActionMessage.MINE_MESSAGE_FAILURE_TOOL_NOT_HELD));
            cancel();
            return;
        }

        PlayerAnimation.ARM_SWING.play((Player) npc.getEntity());  // Make the NPC's arm swing

        if (breaker.run() != BehaviorStatus.RUNNING) {
            breaker.reset();
            // TODO: fix bug where the itemstack in the main hand isn't updated with the new durability
            if (tool != null) {
                DurabilityUtil.decreaseToolDurability(tool);
                ((Player) npc.getEntity()).getEquipment().setItemInMainHand(tool);
            }
            resultFuture.complete(new HumanoidActionResult(true, HumanoidActionMessage.MINE_MESSAGE_SUCCESS));
            cancel();
        }
    }

}