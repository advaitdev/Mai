package me.advait.mai.brain.action.event;

import me.advait.mai.body.Humanoid;
import me.advait.mai.brain.action.HumanoidActionAgent;
import me.advait.mai.brain.action.HumanoidCraftAction;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class HumanoidCraftActionEvent extends HumanoidActionEvent {

    public HumanoidCraftActionEvent(Humanoid humanoid) {
        super(humanoid);
    }

}
