package me.advait.mai.pathetic.movement;

import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import me.advait.mai.pathetic.capabilities.HumanoidCapabilities;
import me.advait.mai.pathetic.config.MovementConfig;

/**
 * Represents a type of movement transition between two path positions.
 * Each implementation knows how to recognize itself, compute its cost,
 * and describe how the executor should physically perform the movement.
 *
 * <p>Movement types are gated by {@link HumanoidCapabilities} — a type
 * only contributes to pathfinding if {@link #isAllowed} returns true for
 * the bot's current capability snapshot. Each type also answers
 * {@link #canReachAsEndpoint} so the registry can decide, without running
 * a full search, whether a given target is even a viable landing spot for
 * <em>any</em> of the bot's current capabilities.
 *
 * <p>Implementations live in the {@code types} sub-package. New movement
 * types can be added by implementing this interface and registering in
 * {@link MovementRegistry}.
 */
public interface MovementType {

    /** Unique identifier used in config and debugging. */
    String key();

    /**
     * Tests whether this movement type matches the transition from prev to current.
     * Called during async pathfinding — must use MaterialProvider, not World.
     */
    boolean matches(PathPosition current, PathPosition previous, MaterialProvider materials);

    /**
     * Computes the full traversal cost for this transition in ticks.
     * Called only after matches() returns true.
     */
    double computeCost(PathPosition current, PathPosition previous,
                       MaterialProvider materials, MovementConfig config);

    /** Describes how the movement executor should perform this transition. */
    ExecutionHint executionHint(PathPosition current, PathPosition previous);

    /**
     * Whether this movement type is usable given the bot's current
     * capabilities. Default is {@code true} — override to require specific
     * capabilities (e.g. a bridge type would return
     * {@code caps.canBridge()}).
     */
    default boolean isAllowed(HumanoidCapabilities caps) {
        return true;
    }

    /**
     * Whether the given position could plausibly be the endpoint of this
     * movement type, given the bot's current capabilities. Used by the
     * registry's feasibility check to reject unreachable targets up front,
     * before pathfinding runs.
     *
     * <p>Unlike {@link #matches}, this only inspects the destination — not
     * the source or the transition path between them. It answers:
     * <em>"could any movement of this type ever land here?"</em>
     *
     * <p>Default returns {@code false} — movement types that aren't
     * responsible for terminating a path (if any) can leave this unchanged.
     * Most types override with a block-property check (e.g. standable,
     * liquid, climbable).
     */
    default boolean canReachAsEndpoint(PathPosition position, HumanoidCapabilities caps,
                                       MaterialProvider materials) {
        return false;
    }
}
