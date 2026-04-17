package me.advait.mai.pathetic.movement;

import me.advait.mai.pathetic.BlockClassifier;
import me.advait.mai.pathetic.config.MovementConfig;
import me.advait.mai.pathetic.path.AnnotatedWaypoint;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
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

    /**
     * Pure-vertical jump: zeros horizontal velocity, then applies a jump
     * impulse. Used for emergency unsticks where keeping current momentum
     * could fling the bot off a narrow ledge (e.g. a 1-wide pillar).
     */
    public static void jumpVerticalOnly(TickContext ctx) {
        LivingEntity entity = ctx.entity();
        entity.setVelocity(new Vector(0, ctx.config.getJumpVelocity(), 0));
        ctx.jumpCooldown = JUMP_COOLDOWN_TICKS;
    }

    /**
     * Ensure horizontal velocity along {@code dir} is at least
     * {@code targetSpeed}. Only adds velocity — never reduces. Used before
     * firing a jump so the bot doesn't need to spend multiple ground-accel
     * ticks building speed first. Needed for step-ups, where the airborne
     * phase has too little horizontal accel (0.02/tick) to recover from a
     * cold-start takeoff.
     */
    public static void kickToward(TickContext ctx, double[] dir, double targetSpeed) {
        LivingEntity entity = ctx.entity();
        Vector vel = entity.getVelocity();
        double along = vel.getX() * dir[0] + vel.getZ() * dir[1];
        if (along < targetSpeed) {
            double delta = targetSpeed - along;
            entity.setVelocity(new Vector(
                    vel.getX() + dir[0] * delta,
                    vel.getY(),
                    vel.getZ() + dir[1] * delta));
        }
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

    // Player half-width is 0.3; subtract an epsilon so the probe samples
    // the block an overlapping corner would actually land in.
    private static final double HALF_WIDTH = 0.29;

    /** The 4 XZ corners of the player bounding box relative to its center. */
    private static final double[][] CORNER_OFFSETS = {
            {-HALF_WIDTH, -HALF_WIDTH}, {-HALF_WIDTH, HALF_WIDTH},
            { HALF_WIDTH, -HALF_WIDTH}, { HALF_WIDTH,  HALF_WIDTH}
    };

    /**
     * True when any of the 4 bounding-box corners, projected {@code probeDist}
     * forward in the given direction, is inside a solid block at the given
     * y-offset from the entity's feet. This catches partial-overlap cases
     * that a single centerline ray misses — the reason bots clip on block
     * edges when a path turns through a block corner.
     */
    public static boolean solidNearFacing(Location current, double[] dir,
                                          double yOffset, double probeDist) {
        World world = current.getWorld();
        if (world == null) return false;
        for (double[] corner : CORNER_OFFSETS) {
            double px = current.getX() + corner[0] + dir[0] * probeDist;
            double py = current.getY() + yOffset;
            double pz = current.getZ() + corner[1] + dir[1] * probeDist;
            Material mat = world.getBlockAt(
                    (int) Math.floor(px),
                    (int) Math.floor(py),
                    (int) Math.floor(pz)).getType();
            if (BlockClassifier.isSolid(mat)) return true;
        }
        return false;
    }

    /**
     * A 1-block-tall obstacle is directly ahead: solid at feet height,
     * clear one block up (so a jump will land on top), clear two blocks
     * up (so the jump apex doesn't collide). Used by ground-walk types
     * as an emergency auto-jump fallback when the planned waypoint didn't
     * anticipate the obstacle (placed block, corner-clip, etc.).
     */
    public static boolean obstacleAheadNeedsJump(Location current, double[] dir) {
        return solidNearFacing(current, dir, 0.1, 0.5)
                && !solidNearFacing(current, dir, 1.1, 0.5)
                && !solidNearFacing(current, dir, 2.1, 0.5);
    }

    /**
     * The bot is at the edge of its current block in the given direction:
     * on solid ground now, but a step past the leading edge would put it
     * over empty space. Used by sprint-jump to time the takeoff for
     * maximum horizontal range — jumping earlier wastes the block of
     * runway that was supposed to bring the bot to terminal sprint.
     */
    public static boolean atEdgeAhead(Location current, double[] dir) {
        World world = current.getWorld();
        if (world == null) return false;
        // Probe a bit past the player's leading edge (half-width 0.3).
        double probeDist = 0.4;
        double px = current.getX() + dir[0] * probeDist;
        double pz = current.getZ() + dir[1] * probeDist;
        Material belowNext = world.getBlockAt(
                (int) Math.floor(px),
                (int) Math.floor(current.getY() - 0.1),
                (int) Math.floor(pz)).getType();
        return !BlockClassifier.isSolid(belowNext);
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
