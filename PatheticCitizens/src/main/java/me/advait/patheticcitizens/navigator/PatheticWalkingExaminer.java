package me.advait.patheticcitizens.navigator;

import net.citizensnpcs.api.astar.pathfinder.BlockExaminer;
import net.citizensnpcs.api.astar.pathfinder.BlockSource;
import net.citizensnpcs.api.astar.pathfinder.PathPoint;

public class PatheticWalkingExaminer implements BlockExaminer {

    @Override
    public float getCost(BlockSource blockSource, PathPoint pathPoint) {
        return 0;
    }

    @Override
    public PassableState isPassable(BlockSource blockSource, PathPoint pathPoint) {
        return null;
    }

}
