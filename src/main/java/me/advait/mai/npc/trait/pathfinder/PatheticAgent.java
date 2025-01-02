package me.advait.mai.npc.trait.pathfinder;

import de.metaphoriker.pathetic.api.factory.PathfinderFactory;
import de.metaphoriker.pathetic.api.pathing.Pathfinder;
import de.metaphoriker.pathetic.api.pathing.configuration.PathfinderConfiguration;
import de.metaphoriker.pathetic.api.pathing.filter.filters.PassablePathFilter;
import de.metaphoriker.pathetic.api.pathing.result.Path;
import de.metaphoriker.pathetic.api.pathing.result.PathfinderResult;
import de.metaphoriker.pathetic.api.wrapper.PathPosition;
import de.metaphoriker.pathetic.bukkit.mapper.BukkitMapper;
import de.metaphoriker.pathetic.bukkit.provider.LoadingNavigationPointProvider;
import de.metaphoriker.pathetic.engine.factory.AStarPathfinderFactory;
import me.advait.mai.npc.pathetic.SolidGroundFilter;
import net.citizensnpcs.api.ai.Navigator;
import org.bukkit.Location;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PatheticAgent {

    private static final PatheticAgent INSTANCE = new PatheticAgent();
    private PatheticAgent() {}

    public static PatheticAgent getInstance() {
        return INSTANCE;
    }

    private final PathfinderFactory FACTORY = new AStarPathfinderFactory();
    private final PathfinderConfiguration CONFIG = PathfinderConfiguration.builder()
            .provider(new LoadingNavigationPointProvider())
            .async(true)
            .build();
    private final Pathfinder PATHFINDER = FACTORY.createPathfinder(CONFIG);

    public Pathfinder getPathfinder() {
        return PATHFINDER;
    }

    public CompletionStage<PathfinderResult> getGroundPath(Location origin, Location dest) {
        PathPosition start = BukkitMapper.toPathPosition(origin);
        PathPosition end = BukkitMapper.toPathPosition(dest);

        CompletionStage<PathfinderResult> pathfindingResult = PATHFINDER.findPath(
                start,
                end,
                List.of(new SolidGroundFilter(), new PassablePathFilter())
        );
        return pathfindingResult;
    }

    public boolean canNavigateTo(Location origin, Location dest) {
        AtomicBoolean isPathSuccessful = new AtomicBoolean(false);
        CompletionStage<PathfinderResult> pathfindingResult = getGroundPath(origin, dest);
        pathfindingResult.thenAccept(result -> {
            if (result.successful()) {
                isPathSuccessful.set(true);
            }
        });

        return isPathSuccessful.get();
    }

}
