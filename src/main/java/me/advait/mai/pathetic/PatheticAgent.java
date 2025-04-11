package me.advait.mai.pathetic;

import com.google.common.annotations.Beta;
import de.metaphoriker.pathetic.api.factory.PathfinderFactory;
import de.metaphoriker.pathetic.api.pathing.Pathfinder;
import de.metaphoriker.pathetic.api.pathing.configuration.PathfinderConfiguration;
import de.metaphoriker.pathetic.api.pathing.filter.PathFilterStage;
import de.metaphoriker.pathetic.api.pathing.filter.filters.PassablePathFilter;
import de.metaphoriker.pathetic.api.pathing.result.PathfinderResult;
import de.metaphoriker.pathetic.api.wrapper.PathPosition;
import de.metaphoriker.pathetic.bukkit.mapper.BukkitMapper;
import de.metaphoriker.pathetic.bukkit.provider.LoadingNavigationPointProvider;
import de.metaphoriker.pathetic.engine.Offset;
import de.metaphoriker.pathetic.engine.factory.AStarPathfinderFactory;
import me.advait.mai.brain.cerebrum.movement.pathfinding.HumanoidPathfinder;
import me.advait.mai.monitor.Monitor;
import org.bukkit.Location;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class PatheticAgent {

    private static final PatheticAgent INSTANCE = new PatheticAgent();
    private PatheticAgent() {}

    public static PatheticAgent getInstance() {
        return INSTANCE;
    }

    private final PathfinderFactory FACTORY = new AStarPathfinderFactory();
    private final PathfinderConfiguration CONFIG = PathfinderConfiguration.builder()
            .provider(new LoadingNavigationPointProvider())
            //.async(true)
            .build();
    private final Pathfinder PATHFINDER = FACTORY.createPathfinder(CONFIG);

    public Pathfinder getPathfinder() {
        return PATHFINDER;
    }


    /**
     * Gets a possible path from one point to another via <strong>solid ground only</strong>.
     *
     * @param origin The starting point of the path.
     * @param dest The ending point of the path.
     */
    public CompletionStage<PathfinderResult> getGroundPath(Location origin, Location dest) {
        PathPosition start = BukkitMapper.toPathPosition(origin);
        PathPosition end = BukkitMapper.toPathPosition(dest);

        CompletionStage<PathfinderResult> pathfindingResult = PATHFINDER.findPath(
                start,
                end,
                List.of(new SolidGroundFilter(), new PassablePathFilter(), new WalkablePathFilter())
        );
        return pathfindingResult;
    }

    @Beta
    public CompletionStage<PathfinderResult> getHumanoidPath(Location origin, Location dest) {
        HumanoidPathfinder humanoidPathfinder = new HumanoidPathfinder(CONFIG.getProvider(), CONFIG);

        PathPosition start = BukkitMapper.toPathPosition(origin);
        PathPosition end = BukkitMapper.toPathPosition(dest);
        CompletionStage<PathfinderResult> pathfindingResult = humanoidPathfinder.findPath(
                start,
                end,
                List.of()
        );
        return pathfindingResult;
    }

    /**
     * Gets a possible path from one point to another, including via <strong>non-solid ground only</strong>.
     *
     * @param origin The starting point of the path.
     * @param dest The ending point of the path.
     */
    public CompletionStage<PathfinderResult> getBridgingPath(Location origin, Location dest) {
        PathPosition start = BukkitMapper.toPathPosition(origin);
        PathPosition end = BukkitMapper.toPathPosition(dest);

        CompletionStage<PathfinderResult> pathfindingResult = PATHFINDER.findPath(
                start,
                end,
                List.of(new NavigationRealismFilter())
        );
        return pathfindingResult;
    }

    public CompletionStage<PathfinderResult> getNPCPath(Location origin, Location dest) {
        PathPosition start = BukkitMapper.toPathPosition(origin);
        PathPosition end = BukkitMapper.toPathPosition(dest);

        CompletionStage<PathfinderResult> pathfindingResult = PATHFINDER.findPath(
                start,
                end,
                List.of(),  // TODO: fix stages not working properly
                List.of(new PathFilterStage(new WalkablePathFilter()),
                        new PathFilterStage(new NavigationRealismFilter()))

        );
        return pathfindingResult;
    }

    public CompletableFuture<Boolean> canNavigateToViaGround(Location origin, Location dest) {
        CompletableFuture<Boolean> canNavigateResult = new CompletableFuture<>();

        CompletionStage<PathfinderResult> pathfindingResult = getGroundPath(origin, dest);
        pathfindingResult.thenAccept(result -> {
            if (result.successful()) {
               canNavigateResult.complete(true);
            } else canNavigateResult.complete(false);
        }).exceptionally(ex -> {
            Monitor.logError("Could not determine a pathfinding result: " + ex.getMessage());
            canNavigateResult.complete(false);
            return null;
        });

        return canNavigateResult;
    }

}
