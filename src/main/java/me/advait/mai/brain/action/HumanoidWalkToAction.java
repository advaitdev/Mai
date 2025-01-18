package me.advait.mai.brain.action;

import me.advait.mai.Mai;
import me.advait.mai.body.Humanoid;
import me.advait.mai.brain.action.event.HumanoidActionEvent;
import me.advait.mai.brain.action.event.HumanoidWalkToActionEvent;
import me.advait.mai.brain.action.result.HumanoidActionMessage;
import me.advait.mai.brain.action.result.HumanoidActionResult;
import me.advait.mai.npc.pathetic.PatheticAgent;
import me.advait.mai.util.NPCUtil;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class HumanoidWalkToAction extends HumanoidAction {

    private final Location destination;

    public HumanoidWalkToAction(Humanoid humanoid, Location destination) {
        super(humanoid);
        this.destination = destination;
    }

    private Location previousLocation = null;
    private int secondsElapsed = 0;

    @Override
    protected void perform(CompletableFuture<HumanoidActionResult> resultFuture) {
        NPC npc = humanoid.getNpc();

        AtomicInteger walkToRunnableID = new AtomicInteger();  // Has to be atomic to be thread-safe

        npc.getNavigator().setTarget(destination);

        walkToRunnableID.set(scheduler.scheduleSyncRepeatingTask(Mai.getInstance(), () -> {

            // This means the NPC is stuck
            if (previousLocation != null && previousLocation.equals(npc.getStoredLocation()) && secondsElapsed >= 10) {
                resultFuture.complete(new HumanoidActionResult(false, HumanoidActionMessage.WALK_TO_MESSAGE_STUCK));
                scheduler.cancelTask(walkToRunnableID.get());
            }

            //if (NPCUtil.isNPCNearDestination(npc, destination)) {
            if (NPCUtil.isNPCNearDestination(npc, destination)) {
                resultFuture.complete(new HumanoidActionResult(true, HumanoidActionMessage.WALK_TO_MESSAGE_SUCCESS));
                scheduler.cancelTask(walkToRunnableID.get());
            }

            var canNavigateResult = PatheticAgent.getInstance().canNavigateToViaGround(npc.getStoredLocation(), destination);
            canNavigateResult.thenAccept(canNavigate -> {
                if (!canNavigate) {
                    resultFuture.complete(new HumanoidActionResult(false, HumanoidActionMessage.WALK_TO_MESSAGE_FAILURE));
                    scheduler.cancelTask(walkToRunnableID.get());
                }
            });

            previousLocation = npc.getStoredLocation();
            secondsElapsed++;

        }, 0, 20));
    }

    @Override
    protected HumanoidActionEvent getEvent() {
        return new HumanoidWalkToActionEvent(humanoid, destination);
    }
}
