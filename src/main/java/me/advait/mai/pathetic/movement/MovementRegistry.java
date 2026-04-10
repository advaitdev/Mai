package me.advait.mai.pathetic.movement;

import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import me.advait.mai.pathetic.PathContext;
import me.advait.mai.pathetic.config.MovementConfig;
import me.advait.mai.pathetic.movement.types.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Ordered registry of movement types. All queries are capability-aware:
 * the registry skips any type whose
 * {@link MovementType#isAllowed(me.advait.mai.pathetic.capabilities.HumanoidCapabilities)}
 * returns {@code false} for the caller's current capability snapshot.
 *
 * <p>Priority: most specific movements first, generic walks last.
 *
 * <p>Adding a new movement type (e.g. bridge-place) is a three-step
 * change: implement {@link MovementType}, gate it with the right
 * capability flag, and add it here in priority order. No other call
 * sites need to change — cost, validation, and feasibility all flow
 * through this registry.
 */
public final class MovementRegistry {

    private final List<MovementType> types;

    public MovementRegistry(MovementConfig config) {
        List<MovementType> list = new ArrayList<>();
        if (config.isParkourEnabled()) {
            list.add(new SprintJump());
        }
        list.add(new LadderClimb());
        list.add(new Swim());
        list.add(new StepUp());
        list.add(new StepDown());
        if (config.isAllowUnsafeFalls()) {
            list.add(new FallUnsafe(config));
        }
        list.add(new FallSafe());
        list.add(new WalkDiagonal());
        list.add(new WalkFlat());
        this.types = Collections.unmodifiableList(list);
    }

    /**
     * Classifies a transition between two positions against the movement
     * types available under {@code ctx.capabilities()}. Returns the first
     * matching type, or empty if none match or no allowed type accepts
     * the transition.
     */
    public Optional<MovementType> classify(PathPosition current, PathPosition previous, PathContext ctx) {
        for (MovementType type : types) {
            if (!type.isAllowed(ctx.capabilities())) continue;
            if (type.matches(current, previous, ctx)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    /**
     * Whether {@code position} is a plausible endpoint for <em>any</em>
     * movement type allowed under {@code ctx.capabilities()}. Used for
     * fast upfront feasibility rejection before pathfinding runs.
     *
     * <p>This is the union of every allowed type's
     * {@link MovementType#canReachAsEndpoint} answer. Adding a new
     * movement type automatically expands the feasible set.
     */
    public boolean canReachAsEndpoint(PathPosition position, PathContext ctx) {
        for (MovementType type : types) {
            if (!type.isAllowed(ctx.capabilities())) continue;
            if (type.canReachAsEndpoint(position, ctx)) return true;
        }
        return false;
    }

    /** Returns all registered movement types (read-only). */
    public List<MovementType> getTypes() {
        return types;
    }
}
