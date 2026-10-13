package me.advait.mai.brain.action.mechanic.movement;

import me.advait.mai.Mai;
import me.advait.mai.body.Humanoid;
import me.advait.mai.brain.action.mechanic.HumanoidAction;
import me.advait.mai.brain.action.event.HumanoidActionEvent;
import me.advait.mai.brain.action.event.HumanoidWalkToActionEvent;
import me.advait.mai.brain.action.result.HumanoidActionResult;
import me.advait.mai.brain.action.mechanic.movement.runnable.HumanoidWalkToRunnable;
import org.bukkit.Location;

import java.util.concurrent.CompletableFuture;

public class HumanoidWalkToAction extends HumanoidAction {

    private final Location destination;
    private final double arrivalRadius;

    public HumanoidWalkToAction(Humanoid humanoid, Location destination) {
        this(humanoid, destination, 0);
    }

    /** Arrive anywhere within {@code arrivalRadius} blocks (0 = the exact block). */
    public HumanoidWalkToAction(Humanoid humanoid, Location destination, double arrivalRadius) {
        super(humanoid);
        this.destination = destination;
        this.arrivalRadius = arrivalRadius;
    }

    @Override
    protected void perform(CompletableFuture<HumanoidActionResult> resultFuture) {
        var walkToRunnable = new HumanoidWalkToRunnable(humanoid, destination, arrivalRadius, resultFuture);
        walkToRunnable.runTaskTimer(Mai.getInstance(), 0, 1);
    }

    @Override
    protected HumanoidActionEvent createEvent() {
        return new HumanoidWalkToActionEvent(humanoid, destination);
    }
}
