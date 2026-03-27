package me.advait.mai.pathetic;

import de.bsommerfeld.pathetic.api.pathing.NeighborStrategies;
import de.bsommerfeld.pathetic.api.pathing.Pathfinder;
import de.bsommerfeld.pathetic.api.pathing.PathfindingSearch;
import de.bsommerfeld.pathetic.api.pathing.configuration.PathfinderConfiguration;
import de.bsommerfeld.pathetic.api.pathing.context.EnvironmentContext;
import de.bsommerfeld.pathetic.api.pathing.result.Path;
import de.bsommerfeld.pathetic.api.pathing.result.PathfinderResult;
import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import de.bsommerfeld.pathetic.bukkit.context.BukkitEnvironmentContext;
import de.bsommerfeld.pathetic.bukkit.mapper.BukkitMapper;
import de.bsommerfeld.pathetic.bukkit.provider.LoadingNavigationPointProvider;
import de.bsommerfeld.pathetic.engine.factory.AStarPathfinderFactory;
import de.bsommerfeld.pathetic.engine.result.PathUtils;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Central pathfinding agent using Pathetic A*.
 * Must be used after PatheticBukkit.initialize().
 */
public final class PatheticAgent {

    private static final PatheticAgent INSTANCE = new PatheticAgent();

    private static final int MAX_ITERATIONS = 50_000;
    private static final double SIMPLIFY_EPSILON = 0.5;

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
                            .costProcessor(List.of(
                                    new HumanoidCostProcessor()
                            ))
                            .build();
                    pathfinder = new AStarPathfinderFactory().createPathfinder(config);
                }
            }
        }
        return pathfinder;
    }

    /**
     * Finds a ground path between two locations.
     * The returned path is simplified and has positions centered horizontally
     * with Y at feet level (standing on top of blocks).
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

    /**
     * Post-processes a raw Pathetic path into centered, simplified waypoints.
     * X/Z are centered to block midpoints; Y is floored (feet level, on top of ground).
     */
    public static List<Vector> processPath(Path rawPath) {
        // Center positions: X/Z at block center, Y at feet level
        Path centered = PathUtils.mutatePositions(rawPath, pos ->
                PathPosition.of(pos.getCenteredX(), pos.getFlooredY(), pos.getCenteredZ())
        );

        // Remove redundant waypoints on straight segments
        Path simplified = PathUtils.simplify(centered, SIMPLIFY_EPSILON);

        // Convert to Bukkit vectors
        List<Vector> waypoints = new ArrayList<>();
        simplified.forEach(pp -> waypoints.add(new Vector(pp.getX(), pp.getY(), pp.getZ())));
        return waypoints;
    }
}
