package me.advait.mai.brain.action.mechanic.pvp;

import me.advait.mai.Mai;
import me.advait.mai.body.Humanoid;
import me.advait.mai.brain.action.event.HumanoidActionEvent;
import me.advait.mai.brain.action.event.HumanoidAttackActionEvent;
import me.advait.mai.brain.action.mechanic.HumanoidAction;
import me.advait.mai.brain.action.result.HumanoidActionResult;
import me.advait.mai.util.ItemUtil;
import net.citizensnpcs.util.Util;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.CompletableFuture;

public class HumanoidAttackAction extends HumanoidAction {

    private final LivingEntity target;
    private final double angle;  // direction to attack in relative to target
    private final double power;  // desired cooldown threshold (0.0–1.0)

    private static final int MAX_WAIT_TICKS = 40; // ~2 seconds max wait

    public HumanoidAttackAction(Humanoid humanoid, Player target, double angle, double power) {
        super(humanoid);
        this.target = target;
        this.angle = angle;
        this.power = power;
    }

    @Override
    protected void perform(CompletableFuture<HumanoidActionResult> resultFuture) {
        HumanEntity bot = (HumanEntity) humanoid.getNpc().getEntity();

        waitUntilReady(bot, 0, resultFuture); // start check loop
    }

    private void waitUntilReady(HumanEntity bot, int ticksWaited, CompletableFuture<HumanoidActionResult> resultFuture) {
        float cooldown = bot.getAttackCooldown(); // 0.0 - 1.0

        if (cooldown >= power || ticksWaited >= MAX_WAIT_TICKS) {
            ItemStack weapon = bot.getEquipment().getItemInMainHand();
            Material type = weapon.getType();
            double baseDamage = ItemUtil.getWeaponDamage(weapon);
            double finalDamage = baseDamage * cooldown;

            Util.faceLocation(bot, target.getLocation());
            bot.swingMainHand();
            target.damage(finalDamage, bot);

            resultFuture.complete(new HumanoidActionResult(true,
                    "Attacked %s with %s (%.2f damage, cooldown %.2f)"
                            .formatted(target.getName(), type.name(), finalDamage, cooldown)
            ));

        } else {
            // Try again in 1 tick
            scheduler.runTaskLater(Mai.getInstance(), () ->
                    waitUntilReady(bot, ticksWaited + 1, resultFuture), 1L);
        }
    }

    @Override
    protected HumanoidActionEvent getEvent() {
        return new HumanoidAttackActionEvent(humanoid, target);
    }
}