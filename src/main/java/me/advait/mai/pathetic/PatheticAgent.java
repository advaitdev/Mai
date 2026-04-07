package me.advait.mai.pathetic;

import de.bsommerfeld.pathetic.api.pathing.Pathfinder;
import de.bsommerfeld.pathetic.api.pathing.PathfindingSearch;
import de.bsommerfeld.pathetic.api.pathing.configuration.PathfinderConfiguration;
import de.bsommerfeld.pathetic.api.pathing.context.EnvironmentContext;
import de.bsommerfeld.pathetic.api.pathing.heuristic.HeuristicWeights;
import de.bsommerfeld.pathetic.api.pathing.result.Path;
import de.bsommerfeld.pathetic.api.pathing.result.PathfinderResult;
import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import de.bsommerfeld.pathetic.bukkit.context.BukkitEnvironmentContext;
import de.bsommerfeld.pathetic.bukkit.mapper.BukkitMapper;
import de.bsommerfeld.pathetic.bukkit.provider.LoadingNavigationPointProvider;
import de.bsommerfeld.pathetic.engine.factory.AStarPathfinderFactory;
import me.advait.mai.Mai;
import me.advait.mai.pathetic.config.MovementConfig;
import me.advait.mai.pathetic.movement.MovementRegistry;
import me.advait.mai.pathetic.path.AnnotatedPath;
import me.advait.mai.pathetic.path.PathAnnotator;
import me.advait.mai.pathetic.processor.HumanoidCostProcessor;
import me.advait.mai.pathetic.processor.HumanoidValidationProcessor;
import me.advait.mai.pathetic.strategy.HumanoidNeighborStrategy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Central pathfinding agent using Pathetic A*.
 * Must be initialized via {@link #initialize(MovementConfig)} after PatheticBukkit.initialize().
 */
public final class PatheticAgent {

    private static final PatheticAgent INSTANCE = new PatheticAgent();

    private volatile Pathfinder pathfinder;
    private volatile MovementConfig config;
    private volatile MovementRegistry registry;
    private volatile PathAnnotator annotator;

    private PatheticAgent() {}

    public static PatheticAgent getInstance() {
        return INSTANCE;
    }

    /**
     * Initializes the pathfinder with the given configuration.
     * Must be called once on the main thread after PatheticBukkit.initialize().
     */
    public void initialize(MovementConfig config) {
        this.config = config;
        this.registry = new MovementRegistry(config);
        this.annotator = new PathAnnotator(registry);

        PathfinderConfiguration pfConfig = PathfinderConfiguration.builder()
                .provider(new LoadingNavigationPointProvider())
                .async(true)
                .maxIterations(config.getMaxIterations())
                .neighborStrategy(new HumanoidNeighborStrategy(config))
                .validationProcessors(List.of(
                        new HumanoidValidationProcessor()
                ))
                .costProcessor(List.of(
                        new HumanoidCostProcessor(registry, config)
                ))
                .heuristicWeights(HeuristicWeights.create(
                        1.0,  // manhattan
                        1.5,  // octile (favor diagonal awareness)
                        0.5,  // perpendicular (less strict for parkour)
                        0.8   // height
                ))
                .fallback(true)
                .build();

        this.pathfinder = new AStarPathfinderFactory().createPathfinder(pfConfig);
    }

    /**
     * Finds a path and annotates it with movement types for the executor.
     * The annotation runs on the main thread after async pathfinding completes.
     *
     * @return future containing the annotated path, or empty if pathfinding failed
     */
    public CompletableFuture<Optional<AnnotatedPath>> getAnnotatedPath(Location from, Location to) {
        World world = from.getWorld();
        if (world == null || !world.equals(to.getWorld())) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        PathPosition startPos = BukkitMapper.toPathPosition(from);
        PathPosition targetPos = BukkitMapper.toPathPosition(to);
        EnvironmentContext context = new BukkitEnvironmentContext(world);

        PathfindingSearch search = pathfinder.findPath(startPos, targetPos, context);

        CompletableFuture<PathfinderResult> rawFuture = new CompletableFuture<>();
        search.ifPresent(rawFuture::complete);
        search.orElse(rawFuture::complete);
        search.exceptionally(rawFuture::completeExceptionally);

        return rawFuture.thenCompose(result -> {
            if (!result.successful()) {
                return CompletableFuture.completedFuture(Optional.empty());
            }

            Path rawPath = result.getPath();

            // Annotate on main thread (uses World block access)
            CompletableFuture<Optional<AnnotatedPath>> annotatedFuture = new CompletableFuture<>();
            Bukkit.getScheduler().runTask(Mai.getInstance(), () -> {
                try {
                    AnnotatedPath annotated = annotator.annotate(rawPath, world);
                    AnnotatedPath simplified = annotated.simplify();
                    annotatedFuture.complete(Optional.of(simplified));
                } catch (Exception e) {
                    annotatedFuture.completeExceptionally(e);
                }
            });
            return annotatedFuture;
        });
    }

    public MovementConfig getConfig() {
        return config;
    }

    public MovementRegistry getRegistry() {
        return registry;
    }
}
