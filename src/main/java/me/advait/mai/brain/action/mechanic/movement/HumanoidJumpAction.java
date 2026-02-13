package me.advait.mai.brain.action.mechanic.movement;

import me.advait.mai.Mai;
import me.advait.mai.body.Humanoid;
import me.advait.mai.brain.action.event.HumanoidActionEvent;
import me.advait.mai.brain.action.mechanic.HumanoidAction;
import me.advait.mai.brain.action.result.HumanoidActionMessage;
import me.advait.mai.brain.action.result.HumanoidActionResult;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.concurrent.CompletableFuture;

public class HumanoidJumpAction extends HumanoidAction {

    public HumanoidJumpAction(Humanoid humanoid) {
        super(humanoid);
    }

    @Override
    protected void perform(CompletableFuture<HumanoidActionResult> resultFuture) {
        LivingEntity entity = (LivingEntity) humanoid.getNpc().getEntity();

        // Must be on ground to jump
        if (!entity.isOnGround()) {
            resultFuture.complete(new HumanoidActionResult(false, HumanoidActionMessage.JUMP_FAILURE_NOT_ON_GROUND));
            return;
        }

        // Set upward velocity (vanilla jump height)
        Vector currentVelocity = entity.getVelocity();
        entity.setVelocity(new Vector(currentVelocity.getX(), 0.42, currentVelocity.getZ()));
        resultFuture.complete(new HumanoidActionResult(true, HumanoidActionMessage.JUMP_SUCCESS));
    }

    @Override
    public HumanoidActionEvent getEvent() {
        // TODO: add respective event
        return null;
    }

}