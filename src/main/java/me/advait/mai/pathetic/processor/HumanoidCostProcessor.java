package me.advait.mai.pathetic.processor;

import de.bsommerfeld.pathetic.api.pathing.processing.Cost;
import de.bsommerfeld.pathetic.api.pathing.processing.CostProcessor;
import de.bsommerfeld.pathetic.api.pathing.processing.context.EvaluationContext;
import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import me.advait.mai.pathetic.capabilities.HumanoidCapabilities;
import me.advait.mai.pathetic.config.MovementConfig;
import me.advait.mai.pathetic.movement.MaterialProvider;
import me.advait.mai.pathetic.movement.MovementRegistry;
import me.advait.mai.pathetic.movement.MovementType;

import java.util.Optional;

/**
 * Computes movement costs using the {@link MovementRegistry}, scoped to a
 * specific {@link HumanoidCapabilities} snapshot. Each transition is
 * classified into a {@link MovementType}, which provides its own cost
 * calculation based on block properties and config.
 *
 * <p>Unclassified transitions receive a very high penalty (effectively
 * blocked) — this should never happen in practice because the validation
 * processor uses the same registry and rejects them first.
 *
 * <p>One processor instance is bound to one capability snapshot. A new
 * instance is created per pathfinding request so capabilities reflect the
 * humanoid's live inventory at the moment the search starts.
 */
public class HumanoidCostProcessor implements CostProcessor {

    private static final double UNCLASSIFIED_PENALTY = 1000.0;

    private final MovementRegistry registry;
    private final MovementConfig config;
    private final HumanoidCapabilities capabilities;

    public HumanoidCostProcessor(MovementRegistry registry, MovementConfig config,
                                 HumanoidCapabilities capabilities) {
        this.registry = registry;
        this.config = config;
        this.capabilities = capabilities;
    }

    @Override
    public Cost calculateCostContribution(EvaluationContext context) {
        PathPosition current = context.getCurrentPathPosition();
        PathPosition previous = context.getPreviousPathPosition();
        if (previous == null) return Cost.ZERO;

        MaterialProvider materials = MaterialProvider.fromNavigationProvider(
                context.getNavigationPointProvider(), context.getEnvironmentContext());

        Optional<MovementType> typeOpt = registry.classify(current, previous, materials, capabilities);

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
