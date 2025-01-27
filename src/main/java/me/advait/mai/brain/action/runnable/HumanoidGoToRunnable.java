package me.advait.mai.brain.action.runnable;

import me.advait.mai.body.Humanoid;
import me.advait.mai.brain.action.result.HumanoidActionMessage;
import me.advait.mai.brain.action.result.HumanoidActionResult;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.CompletableFuture;

public class HumanoidGoToRunnable extends BukkitRunnable {

    private final Humanoid humanoid;
    private final Location target;
    private final CompletableFuture<HumanoidActionResult> resultFuture;

    public HumanoidGoToRunnable(Humanoid humanoid, Location target, CompletableFuture<HumanoidActionResult> resultFuture) {
        this.humanoid = humanoid;
        this.target = target;
        this.resultFuture = resultFuture;
    }

    @Override
    public void run() {
        NPC npc = humanoid.getNpc();

        if (npc == null || npc.getEntity() == null) {
            resultFuture.complete(new HumanoidActionResult(false, HumanoidActionMessage.NPC_IS_NULL));
            cancel();
            return;
        }



    }

}
