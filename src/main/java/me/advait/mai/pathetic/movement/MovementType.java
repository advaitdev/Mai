package me.advait.mai.pathetic.movement;

import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import me.advait.mai.pathetic.PathContext;
import me.advait.mai.pathetic.capabilities.HumanoidCapabilities;

import java.util.List;

/**
 * A type of movement transition between two path positions. Each
 * implementation has three responsibilities:
 *
 * <ol>
 *   <li><b>Classification</b> ({@link #matches}, {@link #computeCost},
 *       {@link #canReachAsEndpoint}) — stateless, used during pathfinding
 *       by the cost/validation processors and the feasibility check.
 *   <li><b>Execution</b> ({@link #tick}, {@link #safeToCancel}) — stateful
 *       per-tick behavior, used by {@code HumanoidWalkToRunnable} when
 *       physically moving along a waypoint. State lives in a shared
 *       {@link TickContext} so the type itself can remain a record.
 *   <li><b>World effects</b> ({@link #toBreak}, {@link #toPlace}) —
 *       declarative lists of blocks a movement depends on. Currently
 *       unused (no mining/bridging types yet) but the shape is there so
 *       future types can plug in without a broader refactor.
 * </ol>
 *
 * <p>Movement types are gated by {@link HumanoidCapabilities} via
 * {@link #isAllowed}. The registry skips disallowed types so capability
 * changes (e.g. losing all blocks) immediately reshape the pathfinding
 * search space without touching this interface.
 */
public interface MovementType {

    // =========================================================================
    // Classification
    // =========================================================================

    /** Unique identifier used in config and debugging. */
    String key();

    /**
     * Tests whether this movement type matches the transition from
     * {@code previous} to {@code current}. Called repeatedly from the
     * pathfinding cost/validation processors, so this must be fast and
     * side-effect free.
     */
    boolean matches(PathPosition current, PathPosition previous, PathContext ctx);

    /**
     * Computes the traversal cost in ticks. Only called after
     * {@link #matches} returns true.
     */
    double computeCost(PathPosition current, PathPosition previous, PathContext ctx);

    /**
     * Describes how the executor should physically perform this transition.
     * Pathetic-side metadata for the annotator — doesn't affect pathfinding.
     */
    ExecutionHint executionHint(PathPosition current, PathPosition previous);

    /**
     * Whether this type is available under the bot's current capabilities.
     * Default: always. Types that need jumping/swimming/etc. override.
     */
    default boolean isAllowed(HumanoidCapabilities caps) {
        return true;
    }

    /**
     * Whether {@code position} could plausibly be the endpoint of this
     * movement type under {@code ctx.capabilities()}. Used by the
     * registry's fast upfront feasibility check to reject unreachable
     * targets before pathfinding runs. Default: false — types that aren't
     * responsible for path termination leave this unchanged.
     */
    default boolean canReachAsEndpoint(PathPosition position, PathContext ctx) {
        return false;
    }

    // =========================================================================
    // Execution
    // =========================================================================

    /**
     * Execute one tick of this movement type toward
     * {@code ctx.waypoint}. The executor reads whatever shared state it
     * needs from the {@link TickContext} (velocity, ground flag, jump
     * cooldown, etc.) and applies an impulse to the entity.
     *
     * <p>Returns a {@link MovementStatus}:
     * <ul>
     *   <li>{@code RUNNING} — keep ticking, stay on this waypoint
     *   <li>{@code SUCCESS} — advance the path index
     *   <li>{@code FAILED} — something went wrong, driver should replan
     *   <li>{@code UNREACHABLE} — give up, this target is gone
     * </ul>
     *
     * <p>Default throws — any type the runnable actually executes must
     * override. Leaving this as a throwing default catches
     * "I forgot to implement tick on type X" immediately, rather than
     * silently skipping waypoints.
     */
    default MovementStatus tick(TickContext ctx) {
        throw new UnsupportedOperationException(
                "MovementType " + key() + " has no tick() implementation");
    }

    /**
     * Whether this movement can be safely interrupted <em>right now</em>
     * so the runnable can swap in a freshly computed path. Default: true.
     *
     * <p>Types with mid-flight state (e.g. sprint-jump between the jump
     * and the landing) should return {@code false} while airborne — a
     * path swap there would strand the bot mid-arc.
     */
    default boolean safeToCancel(TickContext ctx) {
        return true;
    }

    /**
     * Whether the driver may fire a blind "emergency jump" on this type
     * when progress stalls. Default: true — most types benefit from an
     * unstick hop. Types that own their own jump timing and would be
     * disrupted by an unscheduled jump (sprint-jump runway building
     * speed, ladder climbing, swimming) override to false.
     */
    default boolean allowsAutoUnstick() {
        return true;
    }

    // =========================================================================
    // World effects
    // =========================================================================

    /**
     * Block positions the bot must break to traverse this movement.
     * Default: none. Mining-capable types override. The runnable uses this
     * list to prep tools, sequence break actions, and invalidate the
     * path if a required block is suddenly gone.
     */
    default List<PathPosition> toBreak(PathPosition current, PathPosition previous, PathContext ctx) {
        return List.of();
    }

    /**
     * Block positions the bot must place to traverse this movement.
     * Default: none. Bridging-capable types override. The runnable uses
     * this to pre-check inventory for enough blocks and invalidate the
     * path if inventory drains below requirement.
     */
    default List<PathPosition> toPlace(PathPosition current, PathPosition previous, PathContext ctx) {
        return List.of();
    }
}
