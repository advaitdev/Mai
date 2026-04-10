package me.advait.mai.pathetic.movement;

import me.advait.mai.pathetic.BlockClassifier;
import me.advait.mai.pathetic.config.MovementConfig;
import me.advait.mai.pathetic.path.AnnotatedWaypoint;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * Stateless tick-level math shared by movement type {@code tick()}
 * implementations. The runnable used to own all of this; pulling it out
 * here lets each {@link MovementType} compose its execution from a few
 * primitives without duplicating physics constants.
 *
 * <p>All methods read the entity from {@link TickContext} and either
 * compute pure values or apply velocity to the entity in place.
 */
public final class MovementExecutors {

    /** Air acceleration when sprinting (matches vanilla sprint air drift). */
    public static final double SPRINT_AIR_ACCEL = 0.026;
    /** Air acceleration when walking. */
    public static final double WALK_AIR_ACCEL = 0.02;
    /** Cooldown ticks the runnable holds after a jump impulse. */
    public static final int JUMP_COOLDOWN_TICKS = 4;

    private MovementExecutors() {}

    // =========================================================================
    // Locomotion impulses
    // =========================================================================

    /**
     * Apply ground-or-air horizontal acceleration toward the current
     * waypoint at the given speed factor. Honors slipperiness on the
     * block under the entity. Vertical velocity is left untouched.
     */
    public static void groundAccelerate(TickContext ctx, double speedFactor, boolean sprinting) {
        LivingEntity entity = ctx.entity();
        Vector vel = ctx.velocity();
        double[] dir = direction2D(ctx.current, ctx.waypoint);
        boolean onGround = ctx.onGround();

        double vx, vz;
        if (onGround) {
            double slip = slipperinessBelow(ctx.current);
            double inertia = slip * 0.91;
            double accel = ctx.config.getWalkAcceleration() * speedFactor
                    * (0.16277136 / (inertia * inertia * inertia));
            vx = vel.getX() + dir[0] * accel;
            vz = vel.getZ() + dir[1] * accel;
        } else {
            double airAccel = sprinting ? SPRINT_AIR_ACCEL : WALK_AIR_ACCEL;
            vx = vel.getX() + dir[0] * airAccel;
            vz = vel.getZ() + dir[1] * airAccel;
        }

        face(entity, dir);
        entity.setVelocity(new Vector(vx, vel.getY(), vz));
    }

    /**
     * Apply mid-air horizontal acceleration toward the waypoint. Used by
     * parkour and fall types after the bot has left the ground.
     */
    public static void airSteer(TickContext ctx, double accel) {
        LivingEntity entity = ctx.entity();
        Vector vel = ctx.velocity();
        double[] dir = direction2D(ctx.current, ctx.waypoint);
        double vx = vel.getX() + dir[0] * accel;
        double vz = vel.getZ() + dir[1] * accel;
        face(entity, dir);
        entity.setVelocity(new Vector(vx, vel.getY(), vz));
    }

    /**
     * Apply a jump impulse: vertical velocity to the configured jump
     * velocity, optionally adding the sprint-jump horizontal boost in the
     * facing direction. Sets the runnable's jump cooldown.
     */
    public static void jump(TickContext ctx, boolean sprintBoost) {
        LivingEntity entity = ctx.entity();
        Vector vel = ctx.velocity();
        double vx = vel.getX();
        double vz = vel.getZ();

        if (sprintBoost) {
            float yawRad = (float) Math.toRadians(entity.getLocation().getYaw());
            vx += -Math.sin(yawRad) * ctx.config.getSprintJumpBoost();
            vz += Math.cos(yawRad) * ctx.config.getSprintJumpBoost();
        }

        entity.setVelocity(new Vector(vx, ctx.config.getJumpVelocity(), vz));
        ctx.jumpCooldown = JUMP_COOLDOWN_TICKS;
    }

    // =========================================================================
    // Geometry and arrival checks
    // =========================================================================

    public static double[] direction2D(Location from, AnnotatedWaypoint to) {
        double dx = to.x() - from.getX();
        double dz = to.z() - from.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len > 0.001) { dx /= len; dz /= len; }
        return new double[]{dx, dz};
    }

    public static double[] direction3D(Location from, AnnotatedWaypoint to) {
        double dx = to.x() - from.getX();
        double dy = to.y() - from.getY();
        double dz = to.z() - from.getZ();
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 0.001) { dx /= len; dy /= len; dz /= len; }
        return new double[]{dx, dy, dz};
    }

    public static double horizontalDistance(Location from, AnnotatedWaypoint to) {
        double dx = from.getX() - to.x();
        double dz = from.getZ() - to.z();
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static double horizontalSpeed(Vector vel) {
        return Math.sqrt(vel.getX() * vel.getX() + vel.getZ() * vel.getZ());
    }

    /**
     * Whether the entity has horizontally reached the waypoint within the
     * configured radius. Vertical position is ignored.
     */
    public static boolean reachedHorizontally(TickContext ctx) {
        return horizontalDistance(ctx.current, ctx.waypoint) < ctx.config.getWaypointRadius();
    }

    /**
     * Whether the entity has reached the waypoint in both X/Z (within
     * radius) and Y (within ~1 block). The standard arrival check for
     * ground-walk movements.
     */
    public static boolean reachedFully(TickContext ctx) {
        return reachedHorizontally(ctx)
                && Math.abs(ctx.current.getY() - ctx.waypoint.y()) < 1.5;
    }

    // =========================================================================
    // Block lookups
    // =========================================================================

    public static double slipperinessBelow(Location loc) {
        Material below = loc.clone().add(0, -1, 0).getBlock().getType();
        return BlockClassifier.slipperiness(below);
    }

    /**
     * True if there's a solid block in the bot's facing direction with
     * open space above — i.e. an obstacle the bot needs to step up over.
     */
    public static boolean stepUpBlockedAhead(Location current, double[] dir) {
        if (current.getWorld() == null) return false;
        Location ahead = current.clone().add(dir[0] * 0.5, 0, dir[1] * 0.5);
        Material blockAhead = ahead.getBlock().getType();
        if (!BlockClassifier.isSolid(blockAhead)) return false;
        Material above = ahead.clone().add(0, 1, 0).getBlock().getType();
        return !BlockClassifier.isSolid(above);
    }

    // =========================================================================
    // Rotation
    // =========================================================================

    public static void face(LivingEntity entity, double[] dir2D) {
        if (dir2D[0] == 0 && dir2D[1] == 0) return;
        float yaw = (float) Math.toDegrees(Math.atan2(-dir2D[0], dir2D[1]));
        entity.setRotation(yaw, 0);
    }
}
