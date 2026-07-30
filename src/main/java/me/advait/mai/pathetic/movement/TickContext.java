package me.advait.mai.pathetic.movement;

import me.advait.mai.body.Humanoid;
import me.advait.mai.pathetic.config.MovementConfig;
import me.advait.mai.pathetic.path.AnnotatedWaypoint;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * Mutable per-tick state shared between the runnable and the currently
 * executing {@link MovementType#tick}. The movement type is otherwise
 * stateless; this object is the one place where cross-tick state lives so
 * that types can be implemented as records.
 *
 * <p>Owned by {@code HumanoidWalkToRunnable}, reused tick to tick. Each
 * field is documented with which side (driver or movement type) writes it.
 */
public final class TickContext {

    /** Set by driver each tick. */
    public Humanoid humanoid;
    /** Set by driver each tick. */
    public Location current;
    /** Set by driver when entering a new waypoint. */
    public AnnotatedWaypoint waypoint;
    /** The previous waypoint (for direction lookahead). May be null on the first. */
    public AnnotatedWaypoint previousWaypoint;
    /** The next waypoint (for direction lookahead). May be null on the last. */
    public AnnotatedWaypoint nextWaypoint;
    /** Set once at construction by the driver. */
    public MovementConfig config;

    /** Decremented each tick by the driver; written by movement types when jumping. */
    public int jumpCooldown;

    /** Number of ticks the bot has spent on the current movement. Reset on advance. */
    public int ticksOnMovement;

    /** Total number of ticks since this runnable started. Used by some types for cooldowns. */
    public int totalTicks;

    /**
     * Horizontal speed factor the driver suggests for this tick. 1.0 = walk,
     * {@link MovementConfig#getSprintFactor()} = sprint. Ground-walk types
     * honor this; others (parkour, climb, swim) set their own speed.
     *
     * <p>Set by the runnable each tick based on context awareness:
     * near-destination slows to 1.0, approaching step-ups slows to 1.0,
     * upcoming turns slow to 1.0, open ground uses sprint factor.
     */
    public double speedFactor;

    /**
     * Per-movement-type phase marker, reset to 0 when the driver advances
     * to a new waypoint. Types that have internal state phases (sprint-jump
     * ground→air→landing, fall walking-off→falling) use this as they see
     * fit. 0 is always the initial state.
     */
    public int phase;

    /**
     * Closest horizontal distance to the current waypoint we've observed
     * since entering it. Reset by the driver on waypoint advance.
     */
    public double bestHorizDistToWaypoint = Double.MAX_VALUE;

    /**
     * Ticks since we last made horizontal progress toward the waypoint.
     * If this exceeds a threshold while the movement is still {@code RUNNING}
     * and {@link MovementType#safeToCancel} is true, the driver forces a
     * replan — the pathfinder's model no longer matches the world.
     */
    public int ticksSinceProgress;

    /** The final target location of the entire walk (not the current waypoint). */
    public Location finalTarget;

    /**
     * In-progress sub-action future for mine/place movement types (a block
     * break spans many ticks). Set by the type when it kicks off a break,
     * polled each tick, and cleared by the driver whenever it advances to a
     * new waypoint or resets the path so stale state can't leak across moves.
     */
    public java.util.concurrent.CompletableFuture<me.advait.mai.brain.action.result.HumanoidActionResult> subAction;

    /** Convenience accessor — the entity from {@link #humanoid}. */
    public LivingEntity entity() {
        return humanoid.getEntity();
    }

    /** Convenience accessor — the entity's current velocity. */
    public Vector velocity() {
        return entity().getVelocity();
    }

    /** Convenience — apply a velocity to the entity. */
    public void setVelocity(Vector v) {
        entity().setVelocity(v);
    }

    /** Convenience — entity onGround flag. */
    public boolean onGround() {
        return entity().isOnGround();
    }
}
