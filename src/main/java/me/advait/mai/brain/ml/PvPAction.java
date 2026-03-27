package me.advait.mai.brain.ml;

/**
 * A full PvP decision the bot makes in one tick.
 * Bundles movement, jumping, sprinting, blocking, and attacking.
 */
public record PvPAction(
        Type type,
        double forwardSpeed,    // W/S axis: -1.0 = back, +1.0 = forward
        double strafeSpeed,     // A/D axis: -1.0 = right, +1.0 = left
        double movementSpeed,   // scalar speed (0.215 = walk, 0.287 = sprint)
        double movementDistance, // how far to move (blocks)
        boolean sprint,
        boolean jumpBeforeAttack,
        double attackAngle,     // offset angle in degrees
        double minCooldown,     // cooldown % to wait before attacking (0.0-1.0)
        long blockDurationTicks // how long to block (0 = none)
) {
    public enum Type { ATTACK, IDLE }
}
