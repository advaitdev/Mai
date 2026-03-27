package me.advait.mai.pathetic;

import de.bsommerfeld.pathetic.api.pathing.processing.Cost;
import de.bsommerfeld.pathetic.api.pathing.processing.CostProcessor;
import de.bsommerfeld.pathetic.api.pathing.processing.context.EvaluationContext;
import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import de.bsommerfeld.pathetic.bukkit.context.BukkitEnvironmentContext;
import org.bukkit.World;

/**
 * Cost processor that applies scenario-based movement costs for humanoid pathfinding.
 */
public class HumanoidCostProcessor implements CostProcessor {

    @Override
    public Cost calculateCostContribution(EvaluationContext context) {
        PathPosition current = context.getCurrentPathPosition();
        PathPosition previous = context.getPreviousPathPosition();
        if (previous == null) return Cost.ZERO;
        if (!(context.getEnvironmentContext() instanceof BukkitEnvironmentContext bukkitContext)) return Cost.ZERO;
        World world = bukkitContext.getWorld();
        if (world == null) return Cost.ZERO;

        for (Scenario scenario : Scenario.values()) {
            if (scenario.matches(current, previous, world)) {
                double fullCost = scenario.computeCost(current, previous, world);
                double base = context.getBaseTransitionCost();
                return Cost.of(Math.max(0, fullCost - base));
            }
        }
        return Cost.ZERO;
    }
}
