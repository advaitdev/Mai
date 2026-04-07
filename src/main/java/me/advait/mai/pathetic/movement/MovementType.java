package me.advait.mai.pathetic.movement;

import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import me.advait.mai.pathetic.config.MovementConfig;

/**
 * Represents a type of movement transition between two path positions.
 * Each implementation knows how to recognize itself, compute its cost,
 * and describe how the executor should physically perform the movement.
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
}
