package me.advait.mai.pathetic.processor;

import de.bsommerfeld.pathetic.api.pathing.processing.ValidationProcessor;
import de.bsommerfeld.pathetic.api.pathing.processing.context.EvaluationContext;
import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import me.advait.mai.pathetic.capabilities.HumanoidCapabilities;
import me.advait.mai.pathetic.movement.MaterialProvider;
import me.advait.mai.pathetic.movement.MovementRegistry;

/**
 * Validates a transition by asking the {@link MovementRegistry} whether
 * any movement type — under the bot's current capabilities — accepts it.
 * This is the same logic the cost processor uses, so validation and cost
 * agree by construction. Adding a new movement type expands what the
 * validator accepts without touching this class.
 *
 * <p>Like the cost processor, one instance is bound to one capability
 * snapshot and is recreated per pathfinding request.
 */
public class HumanoidValidationProcessor implements ValidationProcessor {

    private final MovementRegistry registry;
    private final HumanoidCapabilities capabilities;

    public HumanoidValidationProcessor(MovementRegistry registry, HumanoidCapabilities capabilities) {
        this.registry = registry;
        this.capabilities = capabilities;
    }

    @Override
    public boolean isValid(EvaluationContext context) {
        PathPosition current = context.getCurrentPathPosition();
        PathPosition previous = context.getPreviousPathPosition();

        // Start node has no incoming transition to validate.
        if (previous == null) return true;

        MaterialProvider materials = MaterialProvider.fromNavigationProvider(
                context.getNavigationPointProvider(), context.getEnvironmentContext());

        return registry.classify(current, previous, materials, capabilities).isPresent();
    }
}
