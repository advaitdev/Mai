package me.advait.mai.brain.action.mechanic.movement;

import me.advait.mai.body.Humanoid;
import me.advait.mai.brain.action.mechanic.HumanoidAction;
import me.advait.mai.brain.action.result.HumanoidActionResult;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.concurrent.CompletableFuture;

public class HumanoidJumpAction extends HumanoidAction {

    private static final double VANILLA_JUMP_VELOCITY = 0.42;

    public HumanoidJumpAction(Humanoid humanoid) {
        super(humanoid);
    }

    @Override
    protected void perform(CompletableFuture<HumanoidActionResult> resultFuture) {
        if (humanoid.getEntity() == null) {
            resultFuture.complete(new HumanoidActionResult(false, "Mannequin not spawned."));
            return;
        }
        LivingEntity entity = humanoid.getEntity();

        if (!entity.isOnGround()) {
            resultFuture.complete(new HumanoidActionResult(false, "Cannot jump while airborne."));
            return;
        }

        Vector velocity = entity.getVelocity();
        entity.setVelocity(new Vector(velocity.getX(), VANILLA_JUMP_VELOCITY, velocity.getZ()));
        resultFuture.complete(new HumanoidActionResult(true, "Jumped successfully."));
    }
}
