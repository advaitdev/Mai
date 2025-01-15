package me.advait.mai.brain.action.event;

import me.advait.mai.body.Humanoid;
import org.bukkit.block.Block;

public class HumanoidMineActionEvent extends HumanoidActionEvent {

    private final Block block;

    public HumanoidMineActionEvent(Humanoid humanoid, Block block) {
        super(humanoid);
        this.block = block;
    }

    public Block getBlockBeingMined() {
        return block;
    }
}
