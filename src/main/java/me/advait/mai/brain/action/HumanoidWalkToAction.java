package me.advait.mai.brain.action;

import me.advait.mai.Mai;
import me.advait.mai.body.Humanoid;
import me.advait.mai.brain.action.event.HumanoidActionEvent;
import me.advait.mai.brain.action.event.HumanoidWalkToActionEvent;
import me.advait.mai.brain.action.result.HumanoidActionResult;
import me.advait.mai.brain.action.runnable.HumanoidWalkToRunnable;
import org.bukkit.Location;

import java.util.concurrent.CompletableFuture;

public class HumanoidWalkToAction extends HumanoidAction {

    private final Location destination;

    public HumanoidWalkToAction(Humanoid humanoid, Location destination) {
        super(humanoid);
        this.destination = destination;
    }

    @Override
    protected void perform(CompletableFuture<HumanoidActionResult> resultFuture) {
        var walkToRunnable = new HumanoidWalkToRunnable(humanoid.getNpc(), destination, resultFuture);
        walkToRunnable.runTaskTimer(Mai.getInstance(), 0, 20);
    }

    @Override
    protected HumanoidActionEvent getEvent() {
        return new HumanoidWalkToActionEvent(humanoid, destination);
    }
}
