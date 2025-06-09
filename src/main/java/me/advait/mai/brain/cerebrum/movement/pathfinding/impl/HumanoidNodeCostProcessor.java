package me.advait.mai.brain.cerebrum.movement.pathfinding.impl;

import de.bsommerfeld.pathetic.api.pathing.processing.Cost;
import de.bsommerfeld.pathetic.api.pathing.processing.NodeCostProcessor;
import de.bsommerfeld.pathetic.api.pathing.processing.context.NodeEvaluationContext;
import de.bsommerfeld.pathetic.api.provider.NavigationPointProvider;
import de.bsommerfeld.pathetic.bukkit.provider.LoadingNavigationPointProvider;
import de.bsommerfeld.pathetic.engine.pathfinder.AStarPathfinder;

public class HumanoidNodeCostProcessor implements NodeCostProcessor {

    @Override
    public Cost calculateCostContribution(NodeEvaluationContext nodeEvaluationContext) {
        return null;
    }

}
