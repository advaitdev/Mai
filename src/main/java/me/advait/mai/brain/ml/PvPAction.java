package me.advait.mai.brain.ml;

/**
 * Represents a full PvP decision the bot makes in one tick.
 * Bundles movement, jumping, sprinting, blocking, and attacking.
 */
public class PvPAction {

    public enum Type {
        ATTACK,
        IDLE
    }

    private final Type type;

    // WASD movement components
    private final double forwardSpeed;   // W/S axis: -1.0 = back, +1.0 = forward
    private final double strafeSpeed;    // A/D axis: -1.0 = right, +1.0 = left
    private final double movementSpeed;  // scalar speed (e.g. 0.215 = walk, 0.287 = sprint)
    private final double movementDistance; // how far to move using WASD (in blocks)

    // Behavior modifiers
    private final boolean sprint;
    private final boolean jumpBeforeAttack;

    // Attack
    private final double attackAngle;    // offset angle in degrees
    private final double minCooldown;   // cooldown % to wait before attacking (0.0 - 1.0)

    // Blocking
    private final long blockDurationTicks; // how long to block (0 = none)

    public PvPAction(Type type,
                     double forwardSpeed,
                     double strafeSpeed,
                     double movementSpeed,
                     double movementDistance,
                     boolean sprint,
                     boolean jumpBeforeAttack,
                     double attackAngle,
                     double minCooldown,
                     long blockDurationTicks) {

        this.type = type;
        this.forwardSpeed = forwardSpeed;
        this.strafeSpeed = strafeSpeed;
        this.movementSpeed = movementSpeed;
        this.movementDistance = movementDistance;
        this.sprint = sprint;
        this.jumpBeforeAttack = jumpBeforeAttack;
        this.attackAngle = attackAngle;
        this.minCooldown = minCooldown;
        this.blockDurationTicks = blockDurationTicks;
    }

    public Type type() {
        return type;
    }

    public double forwardSpeed() {
        return forwardSpeed;
    }

    public double strafeSpeed() {
        return strafeSpeed;
    }

    public double movementSpeed() {
        return movementSpeed;
    }

    public double movementDistance() {
        return movementDistance;
    }

    public boolean sprint() {
        return sprint;
    }

    public boolean jumpBeforeAttack() {
        return jumpBeforeAttack;
    }

    public double attackAngle() {
        return attackAngle;
    }

    public double minCooldown() {
        return minCooldown;
    }

    public long blockDurationTicks() {
        return blockDurationTicks;
    }

    @Override
    public String toString() {
        return "PvPAction{" +
                "type=" + type +
                ", forward=" + forwardSpeed +
                ", strafe=" + strafeSpeed +
                ", moveSpeed=" + movementSpeed +
                ", moveDistance=" + movementDistance +
                ", sprint=" + sprint +
                ", jump=" + jumpBeforeAttack +
                ", angle=" + attackAngle +
                ", cooldown=" + minCooldown +
                ", blockTicks=" + blockDurationTicks +
                '}';
    }
}