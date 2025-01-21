package me.advait.mai.brain.action;

import me.advait.mai.body.Humanoid;
import me.advait.mai.brain.action.event.HumanoidActionEvent;
import me.advait.mai.brain.action.event.HumanoidCraftActionEvent;
import me.advait.mai.brain.action.result.HumanoidActionResult;
import org.bukkit.inventory.Recipe;

import java.util.concurrent.CompletableFuture;

public class HumanoidCraftAction extends HumanoidAction {

    public HumanoidCraftAction(Humanoid humanoid) {
        super(humanoid);
    }

    @Override
    protected void perform(CompletableFuture<HumanoidActionResult> resultFuture) {

    }

    @Override
    protected HumanoidActionEvent getEvent() {
        return new HumanoidCraftActionEvent(humanoid);
    }

}
