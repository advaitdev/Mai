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
import me.advait.mai.body.Humanoid;
import me.advait.mai.pathetic.capabilities.HumanoidCapabilities;
import me.advait.mai.pathetic.config.MovementConfig;
import me.advait.mai.pathetic.movement.MaterialProvider;
import me.advait.mai.pathetic.movement.MovementRegistry;
import me.advait.mai.pathetic.path.AnnotatedPath;
import me.advait.mai.pathetic.path.AnnotatedWaypoint;
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
 * Central pathfinding agent. A new pathfinder is built per request so the
 * {@link HumanoidCapabilities} snapshot — derived from the humanoid's live
 * inventory at call time — flows into the cost and validation processors.
 *
 * <p>Pathetic's processors are stateful with respect to capabilities, so
 * we cannot reuse a single shared pathfinder across calls. Pathfinder
 * construction is cheap (just field assignment); the heavy state lives in
 * the shared {@link HumanoidNeighborStrategy} and {@link MovementRegistry}.
 *
 * <p>Must be initialized via {@link #initialize(MovementConfig)} after
 * PatheticBukkit.initialize().
 */
public final class PatheticAgent {

    private static final HeuristicWeights HEURISTIC_WEIGHTS = HeuristicWeights.create(
            1.0,  // manhattan
            1.5,  // octile (favor diagonal awareness)
            0.5,  // perpendicular (less strict for parkour)
            0.8   // height
    );

    private static final PatheticAgent INSTANCE = new PatheticAgent();

    private volatile MovementConfig config;
    private volatile MovementRegistry registry;
    private volatile PathAnnotator annotator;
    private volatile HumanoidNeighborStrategy neighborStrategy;
    private volatile AStarPathfinderFactory pathfinderFactory;

    private PatheticAgent() {}

    public static PatheticAgent getInstance() {
        return INSTANCE;
    }

    /**
     * Initializes the agent with the given configuration. Builds the
     * shared pieces (registry, neighbor strategy, factory) — the
     * pathfinder itself is constructed per request.
     */
    public void initialize(MovementConfig config) {
        this.config = config;
        this.registry = new MovementRegistry(config);
        this.annotator = new PathAnnotator(registry);
        this.neighborStrategy = new HumanoidNeighborStrategy(config);
        this.pathfinderFactory = new AStarPathfinderFactory();
    }

    /**
     * Finds and annotates a path from {@code from} to {@code to} using the
     * humanoid's live capabilities. The pipeline is:
     *
     * <ol>
     *   <li>Snapshot capabilities from the humanoid's current inventory.</li>
     *   <li>Run an upfront feasibility check via the registry — if no
     *       allowed movement type could ever land at the target, fail
     *       immediately with an empty result.</li>
     *   <li>Build a per-call pathfinder with capability-bound processors
     *       and {@code fallback=false}, so partial paths are not returned
     *       silently.</li>
     *   <li>Run the search async, then verify on the main thread that the
     *       path's final waypoint actually reaches the target block. If
     *       not, fail.</li>
     *   <li>Annotate the path and return it.</li>
     * </ol>
     *
     * @return future containing the annotated path, or empty if any stage
     *         decided no valid path exists
     */
    public CompletableFuture<Optional<AnnotatedPath>> getAnnotatedPath(Humanoid humanoid,
                                                                       Location from, Location to) {
        World world = from.getWorld();
        if (world == null || !world.equals(to.getWorld())) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        HumanoidCapabilities caps = HumanoidCapabilities.from(humanoid, config);

        PathPosition startPos = BukkitMapper.toPathPosition(from);

        // Step 1: resolve the target to a standable block. When a player
        // stands on the edge of a block, {@code getBlockX/Z()} can point
        // to the air cell next to the block they're physically resting on
        // (their 0.6-wide AABB still overlaps the real block). If the
        // strict target isn't standable, try the 4 corners of the AABB
        // footprint before giving up — otherwise /h gotome fails whenever
        // the player is slightly hanging off an edge.
        MaterialProvider mainThreadMaterials = MaterialProvider.fromWorld(world);
        PathContext feasibilityCtx = new PathContext(caps, config, mainThreadMaterials);
        Optional<PathPosition> resolvedTarget = resolveStandableTarget(to, feasibilityCtx);
        if (resolvedTarget.isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        PathPosition targetPos = resolvedTarget.get();

        // Step 2: build a per-call pathfinder bound to this capability snapshot.
        Pathfinder pathfinder = buildPathfinder(caps);
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
                    AnnotatedPath annotated = annotator.annotate(rawPath, world, caps, config);
                    AnnotatedPath simplified = annotated.simplify();

                    // Step 3: verify the path actually reaches the target
                    // block. With fallback disabled this should always be
                    // true on success, but it's a cheap safety net against
                    // future changes or pathetic returning best-effort.
                    if (!reachesTarget(simplified, targetPos)) {
                        annotatedFuture.complete(Optional.empty());
                        return;
                    }
                    annotatedFuture.complete(Optional.of(simplified));
                } catch (Exception e) {
                    annotatedFuture.completeExceptionally(e);
                }
            });
            return annotatedFuture;
        });
    }

    private Pathfinder buildPathfinder(HumanoidCapabilities caps) {
        PathfinderConfiguration pfConfig = PathfinderConfiguration.builder()
                .provider(new LoadingNavigationPointProvider())
                .async(true)
                .maxIterations(config.getMaxIterations())
                .neighborStrategy(neighborStrategy)
                .validationProcessors(List.of(
                        new HumanoidValidationProcessor(registry, config, caps)
                ))
                .costProcessor(List.of(
                        new HumanoidCostProcessor(registry, config, caps)
                ))
                .heuristicWeights(HEURISTIC_WEIGHTS)
                // No fallback: if pathetic can't reach the target, return
                // an unsuccessful result rather than a partial path. The
                // upfront feasibility check already weeded out impossible
                // targets, so the only way we get here without a complete
                // path is iteration exhaustion or genuine unreachability.
                .fallback(false)
                .build();
        return pathfinderFactory.createPathfinder(pfConfig);
    }

    /**
     * Returns true if the annotated path's final waypoint shares a block
     * column and Y with the resolved target position.
     */
    private static boolean reachesTarget(AnnotatedPath path, PathPosition target) {
        if (path.size() == 0) return false;
        AnnotatedWaypoint last = path.get(path.size() - 1);
        return Math.floor(last.x()) == target.getFlooredX()
                && Math.floor(last.z()) == target.getFlooredZ()
                && (int) last.y() == target.getFlooredY();
    }

    /**
     * Resolves {@code to} to the nearest block a bot can actually stand
     * in. Players standing on a block's edge have a {@link Location}
     * whose {@code getBlockX/Z()} may point at the empty cell next to
     * the block supporting them. This method falls back to the 4 corners
     * of the player's AABB before declaring the target unreachable.
     */
    private Optional<PathPosition> resolveStandableTarget(Location to, PathContext ctx) {
        PathPosition strict = BukkitMapper.toPathPosition(to);
        if (registry.canReachAsEndpoint(strict, ctx)) {
            return Optional.of(strict);
        }

        // Player half-width is 0.3; use a hair less so the probe maps
        // cleanly to the block the near corner is inside.
        double[] offsets = {-0.29, 0.29};
        int by = to.getBlockY();
        for (double dx : offsets) {
            for (double dz : offsets) {
                int bx = (int) Math.floor(to.getX() + dx);
                int bz = (int) Math.floor(to.getZ() + dz);
                PathPosition corner = PathPosition.of(bx, by, bz);
                if (registry.canReachAsEndpoint(corner, ctx)) {
                    return Optional.of(corner);
                }
            }
        }
        return Optional.empty();
    }

    public MovementConfig getConfig() {
        return config;
    }

    public MovementRegistry getRegistry() {
        return registry;
    }
}
