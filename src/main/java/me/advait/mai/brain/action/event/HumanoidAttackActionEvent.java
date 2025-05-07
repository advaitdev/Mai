package me.advait.mai.brain.action.event;

import me.advait.mai.body.Humanoid;
import org.bukkit.entity.LivingEntity;

public class HumanoidAttackActionEvent extends HumanoidActionEvent {

    private final LivingEntity victim;

    public HumanoidAttackActionEvent(Humanoid humanoid, LivingEntity victim) {
        super(humanoid);
        this.victim = victim;
    }

    public LivingEntity getVictim() {
        return victim;
    }

}
