package me.advait.mai.brain.action.mechanic.pvp;

import me.advait.mai.Mai;
import me.advait.mai.body.Humanoid;
import me.advait.mai.brain.action.event.HumanoidActionEvent;
import me.advait.mai.brain.action.event.HumanoidAttackActionEvent;
import me.advait.mai.brain.action.mechanic.HumanoidAction;
import me.advait.mai.brain.action.result.HumanoidActionResult;
import me.advait.mai.util.ItemUtil;
import me.advait.mai.util.LocationUtil;
import org.bukkit.Material;
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
        if (humanoid.getEntity() == null) {
            resultFuture.complete(new HumanoidActionResult(false, "Mannequin not spawned"));
            return;
        }
        LivingEntity bot = humanoid.getEntity();
        // Mannequin has no attack cooldown; wait a short delay then attack
        scheduler.runTaskLater(Mai.getInstance(), () -> {
            if (!bot.isValid() || target.isDead()) {
                resultFuture.complete(new HumanoidActionResult(false, "Target or entity invalid"));
                return;
            }
            ItemStack weapon = bot.getEquipment() != null ? bot.getEquipment().getItemInMainHand() : null;
            Material type = weapon != null ? weapon.getType() : Material.AIR;
            double baseDamage = weapon != null ? ItemUtil.getWeaponDamage(weapon) : 1.0;
            double finalDamage = baseDamage * power;

            LocationUtil.faceLocation(bot, target.getLocation());
            bot.swingMainHand();
            target.damage(finalDamage, bot);

            resultFuture.complete(new HumanoidActionResult(true,
                    "Attacked %s with %s (%.2f damage)".formatted(target.getName(), type.name(), finalDamage)
            ));
        }, Math.min(MAX_WAIT_TICKS, 10L));
    }

    @Override
    protected HumanoidActionEvent getEvent() {
        return new HumanoidAttackActionEvent(humanoid, target);
    }
}