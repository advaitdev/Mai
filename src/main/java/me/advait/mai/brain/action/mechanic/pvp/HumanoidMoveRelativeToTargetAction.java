package me.advait.mai.brain.action.mechanic.pvp;

import me.advait.mai.Mai;
import me.advait.mai.body.Humanoid;
import me.advait.mai.brain.action.mechanic.HumanoidAction;
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
            resultFuture.complete(new HumanoidActionResult(false, "Mannequin not spawned."));
            return;
        }
        LivingEntity entity = humanoid.getEntity();
        Location start = entity.getLocation().clone();
        final double squaredDistance = distance * distance;

        Bukkit.getScheduler().runTaskTimer(Mai.getInstance(), task -> {
            if (entity.isDead()) {
                task.cancel();
                resultFuture.complete(new HumanoidActionResult(false, "Entity died during movement."));
                return;
            }

            if (entity.getLocation().distanceSquared(start) >= squaredDistance) {
                task.cancel();
                resultFuture.complete(new HumanoidActionResult(true, "Reached target distance."));
                return;
            }

            MovementUtil.applyWASDMovement(entity, forward, strafe, speed);
        }, 0L, 1L);
    }
}
