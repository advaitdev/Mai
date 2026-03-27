package me.advait.mai.pathetic;

import de.bsommerfeld.pathetic.api.pathing.Pathfinder;
import de.bsommerfeld.pathetic.api.pathing.PathfindingSearch;
import de.bsommerfeld.pathetic.api.pathing.configuration.PathfinderConfiguration;
import de.bsommerfeld.pathetic.api.pathing.context.EnvironmentContext;
import de.bsommerfeld.pathetic.api.pathing.result.PathfinderResult;
import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import de.bsommerfeld.pathetic.bukkit.context.BukkitEnvironmentContext;
import de.bsommerfeld.pathetic.bukkit.mapper.BukkitMapper;
import de.bsommerfeld.pathetic.bukkit.provider.LoadingNavigationPointProvider;
import de.bsommerfeld.pathetic.engine.factory.AStarPathfinderFactory;
import me.advait.mai.pathetic.HumanoidCostProcessor;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Central pathfinding agent using Pathetic. Must be used after {@link de.bsommerfeld.pathetic.bukkit.PatheticBukkit#initialize(org.bukkit.plugin.java.JavaPlugin)}.
 */
public final class PatheticAgent {

    private static final PatheticAgent INSTANCE = new PatheticAgent();

    private static final int MAX_ITERATIONS = 10_000_000;

    private volatile Pathfinder pathfinder;

    private PatheticAgent() {}

    public static PatheticAgent getInstance() {
        return INSTANCE;
    }

    private Pathfinder getPathfinder() {
        if (pathfinder == null) {
            synchronized (this) {
                if (pathfinder == null) {
                    PathfinderConfiguration config = PathfinderConfiguration.builder()
                            .provider(new LoadingNavigationPointProvider())
                            .async(true)
                            .maxIterations(MAX_ITERATIONS)
                            .costProcessor(List.of(new HumanoidCostProcessor()))
                            .build();
                    pathfinder = new AStarPathfinderFactory().createPathfinder(config);
                }
            }
        }
        return pathfinder;
    }

    /**
     * Finds a ground path between two locations. Callbacks run asynchronously; schedule main-thread work inside the future's handlers if needed.
     */
    public CompletableFuture<PathfinderResult> getGroundPath(Location from, Location to) {
        World world = from.getWorld();
        if (world == null || !from.getWorld().equals(to.getWorld())) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Locations must share a non-null world"));
        }
        PathPosition startPos = BukkitMapper.toPathPosition(from);
        PathPosition targetPos = BukkitMapper.toPathPosition(to);
        EnvironmentContext context = new BukkitEnvironmentContext(world);
        PathfindingSearch search = getPathfinder().findPath(startPos, targetPos, context);

        CompletableFuture<PathfinderResult> future = new CompletableFuture<>();
        search.ifPresent(future::complete);
        search.orElse(future::complete);
        search.exceptionally(future::completeExceptionally);
        return future;
    }
}
