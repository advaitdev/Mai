package me.advait.mai.pathetic;

import de.bsommerfeld.pathetic.api.pathing.processing.ValidationProcessor;
import de.bsommerfeld.pathetic.api.pathing.processing.context.EvaluationContext;
import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import de.bsommerfeld.pathetic.bukkit.context.BukkitEnvironmentContext;
import org.bukkit.World;

/**
 * Hard-rejects any move that doesn't match a valid humanoid movement scenario.
 * Unlike CostProcessor penalties, rejected nodes are completely excluded from
 * the A* search — the pathfinder will never route through them.
 */
public class HumanoidValidationProcessor implements ValidationProcessor {

    @Override
    public boolean isValid(EvaluationContext context) {
        PathPosition current = context.getCurrentPathPosition();
        PathPosition previous = context.getPreviousPathPosition();
        if (previous == null) return true;
        if (!(context.getEnvironmentContext() instanceof BukkitEnvironmentContext bc)) return true;
        World world = bc.getWorld();
        if (world == null) return true;

        for (Scenario scenario : Scenario.values()) {
            if (scenario.matches(current, previous, world)) {
                return true;
            }
        }
        return false;
    }
}
