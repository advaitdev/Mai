package me.advait.mai.brain.action;

import me.advait.mai.body.Humanoid;
import me.advait.mai.brain.action.event.HumanoidActionEvent;
import me.advait.mai.brain.action.event.HumanoidGoToActionEvent;
import me.advait.mai.brain.action.result.HumanoidActionResult;
import org.bukkit.Location;

import java.util.concurrent.CompletableFuture;

public class HumanoidGoToAction extends HumanoidAction {

    private final Location destination;

    public HumanoidGoToAction(Humanoid humanoid, Location destination) {
        super(humanoid);
        this.destination = destination;
    }

    @Override
    protected void perform(CompletableFuture<HumanoidActionResult> resultFuture) {

    }

    @Override
    protected HumanoidActionEvent getEvent() {
        return new HumanoidGoToActionEvent(humanoid);
    }

}
