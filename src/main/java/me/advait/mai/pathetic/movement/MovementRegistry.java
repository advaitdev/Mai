package me.advait.mai.pathetic.movement;

import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import me.advait.mai.pathetic.config.MovementConfig;
import me.advait.mai.pathetic.movement.types.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Ordered registry of movement types. Classification tries each type
 * in priority order and returns the first match.
 *
 * Priority: most specific movements first, generic walks last.
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
     * Classifies a transition between two positions.
     * Returns the first matching movement type, or empty if none match.
     */
    public Optional<MovementType> classify(PathPosition current, PathPosition previous,
                                           MaterialProvider materials) {
        for (MovementType type : types) {
            if (type.matches(current, previous, materials)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    /** Returns all registered movement types (read-only). */
    public List<MovementType> getTypes() {
        return types;
    }
}
