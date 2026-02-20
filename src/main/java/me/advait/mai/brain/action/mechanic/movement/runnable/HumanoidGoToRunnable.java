package me.advait.mai.brain.action.mechanic.movement.runnable;

import me.advait.mai.body.Humanoid;
import me.advait.mai.brain.action.result.HumanoidActionMessage;
import me.advait.mai.brain.action.result.HumanoidActionResult;
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
        if (humanoid.getEntity() == null || !humanoid.getEntity().isValid()) {
            resultFuture.complete(new HumanoidActionResult(false, HumanoidActionMessage.NPC_IS_NULL));
            cancel();
            return;
        }
        // GoTo uses same pathfinding + movement as WalkTo; could delegate to WalkTo logic or share a helper.
        // For now this runnable is a stub; callers may use HumanoidWalkToAction for actual movement.
    }

}
