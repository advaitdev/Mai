package me.advait.mai.brain.action.mechanic.pvp;

import me.advait.mai.Mai;
import me.advait.mai.body.Humanoid;
import me.advait.mai.brain.action.event.HumanoidActionEvent;
import me.advait.mai.brain.action.mechanic.HumanoidAction;
import me.advait.mai.brain.action.result.HumanoidActionMessage;
import me.advait.mai.brain.action.result.HumanoidActionResult;
import me.advait.mai.util.MovementUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

import java.util.concurrent.CompletableFuture;

public class HumanoidMoveRelativeToTargetAction extends HumanoidAction {

    private final LivingEntity target;
    private final double forward;
    private final double strafe;
    private final double speed;
    private final double distance;

    public HumanoidMoveRelativeToTargetAction(Humanoid humanoid, LivingEntity target,
                                              double forward, double strafe, double speed,
                                              double distance) {
        super(humanoid);
        this.target = target;
        this.forward = forward;
        this.strafe = strafe;
        this.speed = speed;
        this.distance = distance;
    }

    @Override
    protected void perform(CompletableFuture<HumanoidActionResult> resultFuture) {
        if (humanoid.getEntity() == null) {
            resultFuture.complete(new HumanoidActionResult(false, HumanoidActionMessage.MOVE_RELATIVE_TO_TARGET_FAILURE));
            return;
        }
        LivingEntity entity = humanoid.getEntity();
        Location start = entity.getLocation().clone();

        final double squaredDistance = distance * distance;

        Bukkit.getScheduler().runTaskTimer(Mai.getInstance(), task -> {
            double traveled = entity.getLocation().distanceSquared(start);

            if (entity.isDead()) {
                task.cancel();
                resultFuture.complete(new HumanoidActionResult(false, HumanoidActionMessage.MOVE_RELATIVE_TO_TARGET_FAILURE));
                return;
            }

            if (traveled >= squaredDistance) {
                task.cancel();
                resultFuture.complete(new HumanoidActionResult(true, HumanoidActionMessage.MOVE_RELATIVE_TO_TARGET_SUCCESS));
                return;
            }

            MovementUtil.applyWASDMovement(entity, forward, strafe, speed);
        }, 0L, 1L); // every tick
    }

    @Override
    public HumanoidActionEvent getEvent() {
        // TODO: add respective event
        return null;
    }

}