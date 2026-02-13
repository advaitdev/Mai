package me.advait.mai.util;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.bukkit.Location;

/**
 * Applies velocity to an entity as if it were pressing WASD, relative to where it's looking.
 * Adjusts for speed/slowness and clamps to vanilla max player speed.
 */
public class MovementUtil {

    private static final double VANILLA_MAX_SPEED = 0.4;

    public static void applyWASDMovement(LivingEntity entity, double w, double a, double baseSpeed) {
        if (w == 0 && a == 0) return;

        Location loc = entity.getLocation();
        Vector forward = loc.getDirection().setY(0).normalize();
        Vector right = forward.clone().rotateAroundY(Math.PI / 2);

        Vector move = forward.multiply(w).add(right.multiply(-a));
        if (move.lengthSquared() == 0) return;

        move.normalize();

        // Adjust for potion effects
        double speedMultiplier = getPotionSpeedMultiplier(entity);

        // Adjust for sneaking
        if (entity.isSneaking()) {
            speedMultiplier *= 0.45; // reduce speed when sneaking
        }

        // Final speed
        double finalSpeed = Math.min(baseSpeed * speedMultiplier, VANILLA_MAX_SPEED);
        move.multiply(finalSpeed);

        entity.setVelocity(move);
    }

    /**
     * Calculates movement multiplier from speed/slowness potions.
     */
    private static double getPotionSpeedMultiplier(LivingEntity entity) {
        double multiplier = 1.0;

        if (entity.hasPotionEffect(PotionEffectType.SPEED)) {
            int amplifier = entity.getPotionEffect(PotionEffectType.SPEED).getAmplifier();
            multiplier += 0.2 * (amplifier + 1); // Speed I = +20%, Speed II = +40%, etc.
        }

        if (entity.hasPotionEffect(PotionEffectType.SLOWNESS)) {
            int amplifier = entity.getPotionEffect(PotionEffectType.SLOWNESS).getAmplifier();
            multiplier -= 0.15 * (amplifier + 1); // Slowness I = -15%, II = -30%, etc.
        }

        return Math.max(multiplier, 0.05); // prevent total stop
    }
}