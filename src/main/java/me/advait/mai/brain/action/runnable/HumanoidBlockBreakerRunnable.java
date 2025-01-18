package me.advait.mai.brain.action.runnable;

import me.advait.mai.brain.action.result.HumanoidActionMessage;
import me.advait.mai.brain.action.result.HumanoidActionResult;
import net.citizensnpcs.api.ai.tree.BehaviorStatus;
import net.citizensnpcs.api.npc.BlockBreaker;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.util.PlayerAnimation;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;

public class HumanoidBlockBreakerRunnable implements Runnable {

    public int taskId;
    private final BlockBreaker breaker;
    private final NPC npc;
    private final CompletableFuture<HumanoidActionResult> resultFuture;

    public HumanoidBlockBreakerRunnable(BlockBreaker breaker, NPC npc, CompletableFuture<HumanoidActionResult> resultFuture) {
        this.breaker = breaker;
        this.npc = npc;
        this.resultFuture = resultFuture;
    }

    @Override
    public void run() {
        if (npc == null || npc.getEntity() == null) return;
        PlayerAnimation.ARM_SWING.play((Player) npc.getEntity());  // Make the NPC's arm swing

        if (breaker.run() != BehaviorStatus.RUNNING) {
            Bukkit.getScheduler().cancelTask(taskId);
            breaker.reset();
            resultFuture.complete(new HumanoidActionResult(true, HumanoidActionMessage.MINE_MESSAGE_SUCCESS));
        }
    }
}