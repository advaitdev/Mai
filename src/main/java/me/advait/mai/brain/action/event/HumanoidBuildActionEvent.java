package me.advait.mai.brain.action.event;

import me.advait.mai.body.Humanoid;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

public class HumanoidBuildActionEvent extends HumanoidActionEvent {

    private final Location location;
    private final ItemStack block;

    public HumanoidBuildActionEvent(Humanoid humanoid, Location location, ItemStack block) {
        super(humanoid);
        this.location = location;
        this.block = block;
    }

    public Location getLocation() {
        return location;
    }

    public ItemStack getBlock() {
        return block;
    }

}
