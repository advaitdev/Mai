package me.advait.mai.brain.action.mechanic.movement.runnable;

import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import me.advait.mai.body.Humanoid;
import me.advait.mai.brain.action.result.HumanoidActionResult;
import me.advait.mai.pathetic.PathContext;
import me.advait.mai.pathetic.PatheticAgent;
import me.advait.mai.pathetic.capabilities.HumanoidCapabilities;
import me.advait.mai.pathetic.config.MovementConfig;
import me.advait.mai.pathetic.movement.MaterialProvider;
import me.advait.mai.pathetic.movement.MovementExecutors;
import me.advait.mai.pathetic.movement.MovementRegistry;
import me.advait.mai.pathetic.movement.MovementStatus;
import me.advait.mai.pathetic.movement.MovementType;
import me.advait.mai.pathetic.movement.TickContext;
import me.advait.mai.pathetic.path.AnnotatedPath;
import me.advait.mai.pathetic.path.AnnotatedWaypoint;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Driver for walking a humanoid along a path. All per-tick locomotion
 * physics lives on {@link MovementType#tick}; this class is a thin
 * scheduler that:
 *
 * <ol>
 *   <li>Keeps the current {@link AnnotatedPath} fresh by requesting new
 *       ones asynchronously, gated by {@link MovementType#safeToCancel}
 *       on the current movement so we never swap paths mid-arc.
 *   <li>Computes a context-aware speed factor (sprint on open ground,
 *       walk near turns/obstacles/destination) and drops it into
 *       {@link TickContext#speedFactor} each tick.
 *   <li>Calls {@code type.tick(ctx)} on the current waypoint's movement
 *       type and advances the path index on {@link MovementStatus#SUCCESS}.
 *   <li>Detects getting stuck, entity invalidation, and overall arrival
 *       at the final target.
 * </ol>
 *
 * <p>Adding a new movement type (e.g. bridge-place, mine-through) does
 * not require changes here — the driver is polymorphic over any type
 * that implements {@code tick()}.
 */
public class HumanoidWalkToRunnable extends BukkitRunnable {

    /** How many waypoints ahead to re-classify per tick to detect world changes. */
    private static final int LOOKAHEAD_DEPTH = 10;
    /** Periodic full-replan interval as a safety net (5 seconds). */
    private static final int PERIODIC_REPLAN_TICKS = 100;
    /**
     * Ticks without horizontal progress before the driver fires a blind
     * "emergency jump" to try to free the bot from geometry the obstacle
     * probe didn't detect (corner catches, misaligned waypoints, etc.).
     * Only applies to types where {@link MovementType#allowsAutoUnstick}
     * is true.
     */
    private static final int EMERGENCY_JUMP_THRESHOLD = 8;
    /**
     * Ticks without horizontal progress toward the current waypoint before
     * we assume the movement is stuck (world differs from plan) and force
     * a replan. Short enough to recover quickly, long enough to tolerate
     * mid-jump arcs where horizontal progress temporarily stalls.
     */
    private static final int STUCK_ON_MOVEMENT_TICKS = 20;

    private static boolean debugMode = false;

    private final Humanoid humanoid;
    private final Location target;
    private final CompletableFuture<HumanoidActionResult> resultFuture;
    private final MovementConfig config;
    private final TickContext tickCtx = new TickContext();

    private AnnotatedPath currentPath;
    private int pathIndex = 0;
    private boolean hasEverHadPath = false;
    private int ticksSinceReplan = 0;

    private int timeStuck = 0;
    private Location previousLocation = null;

    private final AtomicBoolean pathfindingInProgress = new AtomicBoolean(false);

    public HumanoidWalkToRunnable(Humanoid humanoid, Location target,
                                  CompletableFuture<HumanoidActionResult> resultFuture) {
        this.humanoid = humanoid;
        this.target = target;
        this.resultFuture = resultFuture;
        this.config = PatheticAgent.getInstance().getConfig();

        tickCtx.humanoid = humanoid;
        tickCtx.config = config;
        tickCtx.finalTarget = target;
    }

    public static void setDebugMode(boolean enabled) { debugMode = enabled; }
    public static boolean isDebugMode() { return debugMode; }

    @Override
    public void run() {
        if (resultFuture.isDone()) { cancel(); return; }

        LivingEntity entity = humanoid.getEntity();
        if (entity == null || !entity.isValid()) {
            complete(false, "Entity is no longer valid.");
            return;
        }

        Location current = entity.getLocation();

        // Exact block arrival — we're done.
        if (isAtTarget(current)) {
            complete(true, "Arrived at destination.");
            return;
        }

        // Stuck detection — same block for too many ticks means the
        // pathfinder and executor disagree about what's possible.
        if (previousLocation != null) {
            if (sameBlock(previousLocation, current)) {
                timeStuck++;
            } else {
                timeStuck = 0;
            }
            if (timeStuck >= config.getStuckTimeout()) {
                complete(false, "Stuck at the same location for too long.");
                return;
            }
        }
        previousLocation = current.clone();

        // Per-tick driver bookkeeping
        if (tickCtx.jumpCooldown > 0) tickCtx.jumpCooldown--;
        tickCtx.totalTicks++;
        tickCtx.current = current;
        ticksSinceReplan++;

        // Decide whether to request a fresh path. Three triggers:
        //   1. We don't have one yet.
        //   2. Lookahead invalidation — a cheap re-classify of the next
        //      few waypoints caught a world change that broke the path.
        //   3. Periodic refresh as a safety net for changes the lookahead
        //      window didn't cover.
        boolean needsReplan = currentPath == null
                || !isUpcomingPathValid()
                || ticksSinceReplan >= PERIODIC_REPLAN_TICKS;

        if (needsReplan && !pathfindingInProgress.get() && isSafeToSwapPath()) {
            requestNewPath(current);
        }

        // Execute the current waypoint's movement type
        if (currentPath != null && pathIndex < currentPath.size()) {
            tickWaypoint();
        }

        if (debugMode) showDebugParticles(current);
    }

    // =========================================================================
    // Execution dispatch
    // =========================================================================

    private void tickWaypoint() {
        AnnotatedWaypoint waypoint = currentPath.get(pathIndex);
        MovementType type = waypoint.type();

        // Fallback: unannotated waypoints (shouldn't happen with a proper
        // annotated path, but be safe). Treat as a plain walk.
        if (type == null) {
            type = FALLBACK_WALK;
        }

        // Populate per-tick state
        tickCtx.waypoint = waypoint;
        tickCtx.previousWaypoint = pathIndex > 0 ? currentPath.get(pathIndex - 1) : null;
        tickCtx.nextWaypoint = pathIndex + 1 < currentPath.size() ? currentPath.get(pathIndex + 1) : null;
        tickCtx.speedFactor = computeSpeedFactor(waypoint);

        // Progress tracking: if horizontal distance to the waypoint stops
        // decreasing, we're either clipping on geometry the path didn't
        // foresee or otherwise out of sync with the world.
        double distNow = MovementExecutors.horizontalDistance(tickCtx.current, waypoint);
        if (distNow < tickCtx.bestHorizDistToWaypoint - 0.05) {
            tickCtx.bestHorizDistToWaypoint = distNow;
            tickCtx.ticksSinceProgress = 0;
        } else {
            tickCtx.ticksSinceProgress++;
        }

        // Emergency unstick: before full replan, just try jumping. Handles
        // corner-catches and edge-clips where the obstacle probe didn't
        // fire because the blocker isn't straight ahead. The jump cooldown
        // naturally rate-limits this to once every ~4 ticks.
        if (tickCtx.ticksSinceProgress >= EMERGENCY_JUMP_THRESHOLD
                && tickCtx.onGround()
                && tickCtx.jumpCooldown == 0
                && type.allowsAutoUnstick()) {
            MovementExecutors.jump(tickCtx, tickCtx.speedFactor > 1.0);
        }

        MovementStatus status = type.tick(tickCtx);

        // Per-movement stuck detection: only trigger when it's safe to swap
        // paths (don't abort a bot mid-arc). Clears the path so the next
        // tick's replan check fires immediately.
        if (status == MovementStatus.RUNNING
                && tickCtx.ticksSinceProgress > STUCK_ON_MOVEMENT_TICKS
                && type.safeToCancel(tickCtx)) {
            resetPathState();
            return;
        }

        switch (status) {
            case SUCCESS -> {
                pathIndex++;
                tickCtx.phase = 0;
                tickCtx.ticksOnMovement = 0;
                tickCtx.bestHorizDistToWaypoint = Double.MAX_VALUE;
                tickCtx.ticksSinceProgress = 0;
            }
            case RUNNING -> tickCtx.ticksOnMovement++;
            case FAILED -> resetPathState();
            case UNREACHABLE -> complete(false, "Target became unreachable.");
        }
    }

    /** Clear the current path so the next tick triggers a replan. */
    private void resetPathState() {
        currentPath = null;
        pathIndex = 0;
        tickCtx.phase = 0;
        tickCtx.ticksOnMovement = 0;
        tickCtx.bestHorizDistToWaypoint = Double.MAX_VALUE;
        tickCtx.ticksSinceProgress = 0;
    }

    /**
     * Decides the horizontal speed factor for this tick. Walking (1.0)
     * near obstacles, turns, or the destination; sprinting on open
     * ground. Ground-walk movement types honor this factor; others
     * (parkour, climb, swim) set their own speed.
     */
    private double computeSpeedFactor(AnnotatedWaypoint wp) {
        double distToTarget = Math.sqrt(tickCtx.current.distanceSquared(target));
        boolean nearDestination = distToTarget < 3.0;

        double horizDist = MovementExecutors.horizontalDistance(tickCtx.current, wp);
        boolean needsJump = wp.y() > tickCtx.current.getY() + 0.3;

        if (nearDestination) return 1.0;
        if (needsJump && horizDist < 2.5) return 1.0;
        if (isUpcomingTurn(wp)) return 1.0;
        return config.getSprintFactor();
    }

    private boolean isUpcomingTurn(AnnotatedWaypoint current) {
        if (pathIndex + 1 >= currentPath.size() || pathIndex == 0) return false;
        AnnotatedWaypoint prev = currentPath.get(pathIndex - 1);
        AnnotatedWaypoint next = currentPath.get(pathIndex + 1);

        double dx1 = current.x() - prev.x(), dz1 = current.z() - prev.z();
        double dx2 = next.x() - current.x(), dz2 = next.z() - current.z();
        double len1 = Math.sqrt(dx1 * dx1 + dz1 * dz1);
        double len2 = Math.sqrt(dx2 * dx2 + dz2 * dz2);
        if (len1 < 0.1 || len2 < 0.1) return false;

        // dot < 0.5 means > 60 degree turn
        double dot = (dx1 * dx2 + dz1 * dz2) / (len1 * len2);
        return dot < 0.5;
    }

    /**
     * Cheap lookahead validation: re-classify the next
     * {@link #LOOKAHEAD_DEPTH} waypoints against the current capabilities
     * and live main-thread world state. Returns false if any upcoming
     * transition no longer matches a movement type (e.g. a block was
     * placed in the path, or the type the path was planned with is no
     * longer applicable). Detecting this early lets us trigger a full
     * replan on the next tick, instead of walking blindly into a wall.
     *
     * <p>This replaces the old "replan every tick" strategy. Full A*
     * only runs when something in the world actually changed relative
     * to what the path expected.
     */
    private boolean isUpcomingPathValid() {
        if (currentPath == null || pathIndex >= currentPath.size()) return false;

        World world = humanoid.getEntity().getLocation().getWorld();
        if (world == null) return false;

        HumanoidCapabilities caps = HumanoidCapabilities.from(humanoid, config);
        MaterialProvider materials = MaterialProvider.fromWorld(world);
        PathContext ctx = new PathContext(caps, config, materials);
        MovementRegistry registry = PatheticAgent.getInstance().getRegistry();

        int start = Math.max(1, pathIndex);
        int end = Math.min(pathIndex + LOOKAHEAD_DEPTH, currentPath.size());

        for (int i = start; i < end; i++) {
            AnnotatedWaypoint prev = currentPath.get(i - 1);
            AnnotatedWaypoint curr = currentPath.get(i);

            PathPosition prevPos = PathPosition.of(
                    (int) Math.floor(prev.x()), (int) prev.y(), (int) Math.floor(prev.z()));
            PathPosition currPos = PathPosition.of(
                    (int) Math.floor(curr.x()), (int) curr.y(), (int) Math.floor(curr.z()));

            Optional<MovementType> match = registry.classify(currPos, prevPos, ctx);
            if (match.isEmpty()) return false;

            // If the path was annotated with a specific type and the current
            // best match is different, the world shifted underneath us.
            if (curr.type() != null && !match.get().key().equals(curr.type().key())) {
                return false;
            }
        }
        return true;
    }

    private boolean isSafeToSwapPath() {
        if (currentPath == null || pathIndex >= currentPath.size()) return true;
        AnnotatedWaypoint wp = currentPath.get(pathIndex);
        MovementType type = wp.type();
        if (type == null) return true;
        // Temporarily point the tick context at this waypoint for the
        // safeToCancel query — types may consult ctx.onGround() etc.
        tickCtx.waypoint = wp;
        return type.safeToCancel(tickCtx);
    }

    private boolean isAtTarget(Location current) {
        return current.getBlockX() == target.getBlockX()
                && current.getBlockZ() == target.getBlockZ()
                && Math.abs(current.getY() - target.getY()) < 2.0;
    }

    private boolean sameBlock(Location a, Location b) {
        return a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    // =========================================================================
    // Path request
    // =========================================================================

    private void requestNewPath(Location current) {
        pathfindingInProgress.set(true);
        ticksSinceReplan = 0;

        PatheticAgent.getInstance().getAnnotatedPath(humanoid, current, target)
                .thenAccept(pathOpt -> {
                    pathfindingInProgress.set(false);
                    if (resultFuture.isDone()) return;

                    if (pathOpt.isPresent()) {
                        AnnotatedPath newPath = pathOpt.get();
                        // Start from index 1 (skip the start node which is our
                        // current position). Avoids closest-waypoint oscillation.
                        currentPath = newPath;
                        pathIndex = Math.min(1, newPath.size() - 1);
                        hasEverHadPath = true;
                        tickCtx.phase = 0;
                        tickCtx.ticksOnMovement = 0;
                        tickCtx.bestHorizDistToWaypoint = Double.MAX_VALUE;
                        tickCtx.ticksSinceProgress = 0;
                    } else if (!hasEverHadPath) {
                        complete(false, "No valid path found to target.");
                    }
                })
                .exceptionally(ex -> {
                    pathfindingInProgress.set(false);
                    if (!hasEverHadPath) {
                        complete(false, "Pathfinding error: " + ex.getMessage());
                    }
                    return null;
                });
    }

    private void complete(boolean success, String message) {
        if (!resultFuture.isDone()) {
            resultFuture.complete(new HumanoidActionResult(success, message));
        }
        cancel();
    }

    // =========================================================================
    // Debug particles
    // =========================================================================

    private void showDebugParticles(Location current) {
        if (current.getWorld() == null) return;

        current.getWorld().spawnParticle(Particle.DUST, current.clone().add(0, 1, 0),
                1, new Particle.DustOptions(Color.GREEN, 1));
        target.getWorld().spawnParticle(Particle.DUST, target.clone().add(0, 1, 0),
                1, new Particle.DustOptions(Color.RED, 1));

        if (currentPath == null) return;
        for (int i = pathIndex; i < Math.min(pathIndex + 10, currentPath.size()); i++) {
            AnnotatedWaypoint wp = currentPath.get(i);
            Location wpLoc = new Location(current.getWorld(), wp.x(), wp.y() + 0.5, wp.z());
            Color color;
            if (wp.hint() != null) {
                color = switch (wp.hint().locomotion()) {
                    case WALK -> Color.AQUA;
                    case SPRINT -> Color.BLUE;
                    case SPRINT_JUMP -> Color.ORANGE;
                    case SNEAK -> Color.GRAY;
                    case SWIM -> Color.TEAL;
                    case CLIMB -> Color.PURPLE;
                };
            } else {
                color = Color.YELLOW;
            }
            if (i == pathIndex) color = Color.WHITE;
            current.getWorld().spawnParticle(Particle.DUST, wpLoc, 1, new Particle.DustOptions(color, 0.7f));
        }
    }

    /** Placeholder for waypoints missing a type annotation. */
    private static final MovementType FALLBACK_WALK = new me.advait.mai.pathetic.movement.types.WalkFlat();
}
