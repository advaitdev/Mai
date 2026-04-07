package me.advait.mai.pathetic.processor;

import de.bsommerfeld.pathetic.api.pathing.processing.Cost;
import de.bsommerfeld.pathetic.api.pathing.processing.CostProcessor;
import de.bsommerfeld.pathetic.api.pathing.processing.context.EvaluationContext;
import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import me.advait.mai.pathetic.config.MovementConfig;
import me.advait.mai.pathetic.movement.MaterialProvider;
import me.advait.mai.pathetic.movement.MovementRegistry;
import me.advait.mai.pathetic.movement.MovementType;

import java.util.Optional;

/**
 * Computes movement costs using the MovementRegistry.
 * Each transition is classified into a MovementType, which provides
 * its own cost calculation based on block properties and config.
 *
 * <p>Unclassified transitions receive a very high penalty (effectively blocked).
 */
public class HumanoidCostProcessor implements CostProcessor {

    private static final double UNCLASSIFIED_PENALTY = 1000.0;

    private final MovementRegistry registry;
    private final MovementConfig config;

    public HumanoidCostProcessor(MovementRegistry registry, MovementConfig config) {
        this.registry = registry;
        this.config = config;
    }

    @Override
    public Cost calculateCostContribution(EvaluationContext context) {
        PathPosition current = context.getCurrentPathPosition();
        PathPosition previous = context.getPreviousPathPosition();
        if (previous == null) return Cost.ZERO;

        MaterialProvider materials = MaterialProvider.fromNavigationProvider(
                context.getNavigationPointProvider(), context.getEnvironmentContext());

        Optional<MovementType> typeOpt = registry.classify(current, previous, materials);

        if (typeOpt.isEmpty()) {
            return Cost.of(UNCLASSIFIED_PENALTY);
        }

        MovementType type = typeOpt.get();
        double movementCost = type.computeCost(current, previous, materials, config);
        double baseTransition = context.getBaseTransitionCost();

        // Cost processor adds on TOP of the base transition cost,
        // so we subtract base to avoid double-counting distance
        return Cost.of(Math.max(0, movementCost - baseTransition));
    }
}
