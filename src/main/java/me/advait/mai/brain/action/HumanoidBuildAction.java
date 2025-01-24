package me.advait.mai.brain.action;

import me.advait.mai.body.Humanoid;
import me.advait.mai.brain.action.event.HumanoidActionEvent;
import me.advait.mai.brain.action.event.HumanoidBuildActionEvent;
import me.advait.mai.brain.action.result.HumanoidActionResult;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.CompletableFuture;

public class HumanoidBuildAction extends HumanoidAction {

    private final Location location;
    private final ItemStack block;

    public HumanoidBuildAction(Humanoid humanoid, Location location, ItemStack block) {
        super(humanoid);
        this.location = location;
        this.block = block;
    }

    @Override
    protected void perform(CompletableFuture<HumanoidActionResult> resultFuture) {

    }

    @Override
    protected HumanoidActionEvent getEvent() {
        return new HumanoidBuildActionEvent(humanoid);
    }

}
