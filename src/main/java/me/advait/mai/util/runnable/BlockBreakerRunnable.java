package me.advait.mai.util.runnable;

import net.citizensnpcs.api.ai.tree.BehaviorStatus;
import net.citizensnpcs.api.npc.BlockBreaker;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.util.PlayerAnimation;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class BlockBreakerRunnable implements Runnable {

    public int taskId;
    private final BlockBreaker breaker;
    private final NPC npc;

    public BlockBreakerRunnable(BlockBreaker breaker, NPC npc) {
        this.breaker = breaker;
        this.npc = npc;
    }

    @Override
    public void run() {
        PlayerAnimation.ARM_SWING.play((Player) npc.getEntity());  // Make the NPC's arm swing

        if (breaker.run() != BehaviorStatus.RUNNING) {
            Bukkit.getScheduler().cancelTask(taskId);
            breaker.reset();
        }
    }
}