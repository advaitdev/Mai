package me.advait.mai.brain.action.mechanic.movement.runnable;

import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import me.advait.mai.body.Humanoid;
import me.advait.mai.brain.action.result.HumanoidActionResult;
import me.advait.mai.pathetic.PathContext;
import me.advait.mai.pathetic.PatheticAgent;
import me.advait.mai.pathetic.capabilities.HumanoidCapabilities;
import me.advait.mai.pathetic.config.MovementConfig;
import me.advait.mai.pathetic.debug.PathDebugLog;
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

import de.bsommerfeld.pathetic.bukkit.provider.FailingNavigationPointProvider;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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

    // Replan/stuck thresholds (lookahead depth, periodic replan interval,
    // emergency-jump and stuck-replan tick counts) live in settings.yml under
    // movement.navigation so they can be tuned at runtime; see MovementConfig.
    /** Max waypoints that may complete in a single tick (see run()). */
    private static final int MAX_WAYPOINT_CHAIN = 3;

    private static boolean debugMode = false;

    /** Radius (blocks) around a bot within which a world change forces a replan. */
    private static final double WORLD_CHANGE_RADIUS = 32.0;

    /** Live runnables, so a world-change event can flag the affected bots. */
    private static final Set<HumanoidWalkToRunnable> ACTIVE = ConcurrentHashMap.newKeySet();

    /**
     * Called (main thread) when a block changes in the world. Invalidates the
     * pathfinder's static chunk-snapshot cache so the next A* sees fresh blocks,
     * and flags any nearby in-flight walk to replan immediately — this is what
     * makes pathing adapt to a constantly-changing world.
     */
    public static void onWorldChange(org.bukkit.World world, int x, int y, int z) {
        FailingNavigationPointProvider.invalidateChunk(world.getUID(), x >> 4, z >> 4);
        for (HumanoidWalkToRunnable r : ACTIVE) {
            LivingEntity e = r.humanoid.getEntity();
            if (e == null || !world.equals(e.getWorld())) continue;
            if (e.getLocation().distanceSquared(new Location(world, x, y, z)) <= WORLD_CHANGE_RADIUS * WORLD_CHANGE_RADIUS) {
                r.worldDirty = true;
            }
        }
    }

    /**
     * Key of the movement {@code humanoid}'s live walk is executing (e.g.
     * {@code water_clutch}), or null when it isn't walking a path.
     */
    public static String activeMovement(Humanoid humanoid) {
        for (HumanoidWalkToRunnable r : ACTIVE) {
            if (r.humanoid != humanoid) continue;
            LivingEntity body = humanoid.peekEntity();
            if (r.runJump != null && body != null && r.runJumpOnArc(body)) return "run_jump";
            AnnotatedPath path = r.currentPath;
            if (path == null || r.pathIndex >= path.size()) return null;
            MovementType type = path.get(r.pathIndex).type();
            return type == null ? null : type.key();
        }
        return null;
    }

    private volatile boolean worldDirty = false;
    /** Set by {@link #retarget}; replans toward the new target once safe. */
    private volatile boolean retargeted = false;

    private final Humanoid humanoid;
    private volatile Location target;
    /**
     * 0 = arrive on the exact target block. Above 0 = arrive anywhere within
     * this horizontal distance (follow and chase stop short of the target).
     */
    private final double arrivalRadius;
    private final CompletableFuture<HumanoidActionResult> resultFuture;
    private final MovementConfig config;
    private final TickContext tickCtx = new TickContext();

    private AnnotatedPath currentPath;
    /**
     * A replan's result, waiting for the current movement to reach a point
     * where it is safe to cancel (never mid-jump).
     */
    private volatile AnnotatedPath pendingPath;
    private int pathIndex = 0;
    private boolean hasEverHadPath = false;
    private int ticksSinceReplan = 0;

    private int timeStuck = 0;
    private Location previousLocation = null;

    private final AtomicBoolean pathfindingInProgress = new AtomicBoolean(false);

    public HumanoidWalkToRunnable(Humanoid humanoid, Location target,
                                  CompletableFuture<HumanoidActionResult> resultFuture) {
        this(humanoid, target, 0, resultFuture);
    }

    public HumanoidWalkToRunnable(Humanoid humanoid, Location target, double arrivalRadius,
                                  CompletableFuture<HumanoidActionResult> resultFuture) {
        this.humanoid = humanoid;
        this.target = target;
        this.arrivalRadius = arrivalRadius;
        this.resultFuture = resultFuture;
        this.config = PatheticAgent.getInstance().getConfig();

        tickCtx.humanoid = humanoid;
        tickCtx.config = config;
        tickCtx.finalTarget = target;

        ACTIVE.add(this);
    }

    /**
     * Point an in-flight walk at a new target (a moving player, a fleeing
     * mob). The current path keeps executing until it is safe to swap, then
     * a replan toward the new target runs, so no arc is cut short.
     */
    public void retarget(Location newTarget) {
        Location old = target;
        target = newTarget;
        tickCtx.finalTarget = newTarget;
        if (old == null || old.getWorld() != newTarget.getWorld()
                || old.getBlockX() != newTarget.getBlockX()
                || old.getBlockY() != newTarget.getBlockY()
                || old.getBlockZ() != newTarget.getBlockZ()) {
            retargeted = true;
        }
    }

    public Location getTarget() { return target; }

    /** Ends the walk now (reported as unsuccessful: "Stopped."). */
    public void stop() { complete(false, "Stopped."); }

    public boolean isDone() { return resultFuture.isDone(); }

    public static void setDebugMode(boolean enabled) { debugMode = enabled; }
    public static boolean isDebugMode() { return debugMode; }

    @Override
    public void run() {
        if (resultFuture.isDone()) { ACTIVE.remove(this); cancel(); return; }

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
        // A path planned or adopted mid-jump starts from a cell in the air (a
        // run-jump retargeted at its apex planned a "step down" back to the
        // floor it was already past, and stuck there). Plans wait for footing.
        boolean footing = entity.isOnGround() || entity.isInWater() || entity.isClimbing();
        AnnotatedPath pending = pendingPath;
        if (pending != null && (currentPath == null || (footing && isSafeToSwapPath()))) {
            pendingPath = null;
            adoptPath(pending, current);
        }

        boolean pathNull = currentPath == null;
        boolean lookaheadBroken = !pathNull && !isUpcomingPathValid();
        boolean dirty = !pathNull && !lookaheadBroken && (worldDirty || retargeted);
        boolean periodic = !pathNull && !lookaheadBroken && !dirty && ticksSinceReplan >= config.getPeriodicReplanTicks();
        boolean needsReplan = pathNull || lookaheadBroken || dirty || periodic;

        if (needsReplan && !pathfindingInProgress.get() && pendingPath == null
                && (footing || pathNull) && isSafeToSwapPath()) {
            String reason = pathNull ? "no_path" : lookaheadBroken ? "lookahead_invalid"
                    : retargeted ? "retarget" : dirty ? "world_change" : "periodic";
            worldDirty = false;
            retargeted = false;
            PathDebugLog.event("REPLAN reason=%s bot=(%.2f,%.2f,%.2f)",
                    reason, current.getX(), current.getY(), current.getZ());
            requestNewPath(current);
        }

        // Execute the current waypoint's movement type. When a waypoint
        // completes, fall through and tick the next one in the same tick:
        // otherwise the SUCCESS tick is "lost" — the bot keeps its momentum
        // but nobody decides anything for it, which is exactly the tick a
        // sprinting bot needs to fire its takeoff before leaving the edge.
        // Bounded so a chain of trivially-reached waypoints can't spin.
        if (currentPath != null && pathIndex < currentPath.size()) {
            MovementExecutors.openDoorsAhead(entity, current,
                    MovementExecutors.direction2D(current, currentPath.get(pathIndex)));
        }

        skipStraightWaypoints(entity, current);

        if (driveRunJump(entity, current)) {
            if (debugMode) showDebugParticles(current);
            return;
        }

        for (int chain = 0; chain < MAX_WAYPOINT_CHAIN; chain++) {
            if (currentPath == null || pathIndex >= currentPath.size()) break;
            if (resultFuture.isDone()) break;
            boolean advanced = tickWaypoint();
            if (!advanced) break;
        }

        if (debugMode) showDebugParticles(current);
    }

    // =========================================================================
    // Execution dispatch
    // =========================================================================

    /**
     * Ticks the current waypoint's movement type once. Returns true when the
     * waypoint completed and the path index advanced, so the caller may
     * immediately tick the next waypoint within the same server tick.
     */
    private boolean tickWaypoint() {
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
        // Pure-vertical so lingering horizontal momentum can't fling the
        // bot off a narrow ledge.
        if (tickCtx.ticksSinceProgress >= config.getEmergencyJumpTicks()
                && tickCtx.onGround()
                && tickCtx.jumpCooldown == 0
                && tickCtx.detourTick < tickCtx.totalTicks - 1
                && type.allowsAutoUnstick()) {
            PathDebugLog.event("EMERGENCY_JUMP type=%s stuck=%dt bot=(%.2f,%.2f,%.2f) wp=(%.2f,%.2f,%.2f) dist=%.2f",
                    type.key(), tickCtx.ticksSinceProgress,
                    tickCtx.current.getX(), tickCtx.current.getY(), tickCtx.current.getZ(),
                    waypoint.x(), waypoint.y(), waypoint.z(), distNow);
            MovementExecutors.jump(tickCtx);
        }

        // A survival reflex has the controls this tick (an unplanned fall).
        // Once it lets go, the bot is somewhere the path never went: replan.
        if (me.advait.mai.body.Reflexes.isActive(humanoid)) return false;
        if (me.advait.mai.body.Reflexes.takeRescued(humanoid)) {
            PathDebugLog.event("REFLEX_RECOVERED bot=(%.2f,%.2f,%.2f) → replan",
                    tickCtx.current.getX(), tickCtx.current.getY(), tickCtx.current.getZ());
            resetPathState();
            return false;
        }

        MovementStatus status = type.tick(tickCtx);

        // Per-movement stuck detection: only trigger when it's safe to swap
        // paths (don't abort a bot mid-arc). Clears the path so the next
        // tick's replan check fires immediately.
        if (status == MovementStatus.RUNNING
                && tickCtx.ticksSinceProgress > config.getStuckReplanTicks()
                && type.safeToCancel(tickCtx)) {
            PathDebugLog.event("STUCK_REPLAN type=%s stuck=%dt bot=(%.2f,%.2f,%.2f) wp=(%.2f,%.2f,%.2f)",
                    type.key(), tickCtx.ticksSinceProgress,
                    tickCtx.current.getX(), tickCtx.current.getY(), tickCtx.current.getZ(),
                    waypoint.x(), waypoint.y(), waypoint.z());
            resetPathState();
            return false;
        }

        switch (status) {
            case SUCCESS -> {
                PathDebugLog.event("WAYPOINT_DONE type=%s index=%d/%d wp=(%.2f,%.2f,%.2f)",
                        type.key(), pathIndex, currentPath.size() - 1,
                        waypoint.x(), waypoint.y(), waypoint.z());
                // The next movement starts this same tick from neutral keys:
                // a finished walk's W must not leak into, say, a pillar jump.
                tickCtx.body().release();
                handleWaypointTransition();
                pathIndex++;
                tickCtx.subAction = null;
                tickCtx.phase = 0;
                tickCtx.ticksOnMovement = 0;
                tickCtx.bestHorizDistToWaypoint = Double.MAX_VALUE;
                tickCtx.ticksSinceProgress = 0;
                return true;
            }
            case RUNNING -> tickCtx.ticksOnMovement++;
            case FAILED -> {
                PathDebugLog.event("MOVEMENT_FAILED type=%s wp=(%.2f,%.2f,%.2f)",
                        type.key(), waypoint.x(), waypoint.y(), waypoint.z());
                resetPathState();
            }
            case UNREACHABLE -> complete(false, "Target became unreachable.");
        }
        return false;
    }

    /**
     * Any-angle walking. The planner's path is a chain of 8-way grid steps;
     * while the hitbox has a clear straight line from the bot to the
     * waypoint after the current one (both plain walks on one level), the
     * current one is dropped, so the bot runs straight across open ground
     * and tight round pillars like a player instead of along 45-degree legs.
     * The approach to a jump or step keeps its line: skipping into it is
     * only allowed when it changes the heading by less than 25 degrees.
     */
    private void skipStraightWaypoints(LivingEntity entity, Location current) {
        if (!entity.isOnGround() || entity.isInWater()) return;
        for (int n = 0; n < MAX_SKIPS && currentPath != null && pathIndex + 1 < currentPath.size(); n++) {
            AnnotatedWaypoint wp = currentPath.get(pathIndex), next = currentPath.get(pathIndex + 1);
            if (!MovementExecutors.isLevelWalk(wp) || !MovementExecutors.isLevelWalk(next)) return;
            if (Math.abs(next.y() - wp.y()) > 0.01 || Math.abs(current.getY() - wp.y()) > 0.6) return;
            AnnotatedWaypoint after = pathIndex + 2 < currentPath.size() ? currentPath.get(pathIndex + 2) : null;
            if (after != null && !MovementExecutors.isLevelWalk(after)) {
                double[] planned = MovementExecutors.direction2D(new Location(current.getWorld(), wp.x(), wp.y(), wp.z()), next);
                double[] direct = MovementExecutors.direction2D(current, next);
                if (planned[0] * direct[0] + planned[1] * direct[1] < COS_25) return;
            }
            if (!MovementExecutors.hitboxLineClear(current, next.x(), next.z(), MovementExecutors.SKIP_CLEARANCE)) return;
            PathDebugLog.event("WAYPOINT_SKIP index=%d wp=(%.2f,%.2f,%.2f) to=(%.2f,%.2f,%.2f)",
                    pathIndex, wp.x(), wp.y(), wp.z(), next.x(), next.y(), next.z());
            pathIndex++;
            tickCtx.subAction = null;
            tickCtx.phase = 0;
            tickCtx.ticksOnMovement = 0;
            tickCtx.bestHorizDistToWaypoint = Double.MAX_VALUE;
            tickCtx.ticksSinceProgress = 0;
        }
    }

    /** Ground moves a run-jump may fly over: the route it must land back on is made of these. */
    private static final Set<String> RUN_JUMP_MOVES = Set.of("walk_flat", "walk_diagonal", "step_up", "step_down");
    /** How far ahead along the route a run-jump looks for its landing. */
    private static final double RUN_JUMP_ROUTE = 8.0;

    private me.advait.mai.pathetic.movement.RunJump.Plan runJump;
    private int runJumpStart;
    private Location runJumpFrom;
    /** Velocity at the previous airborne tick of the run-jump (null on its first). */
    private org.bukkit.util.Vector runJumpLastVelocity;

    /** Air physics change horizontal velocity by under 0.06 a tick; more is a hit or a wall. */
    private static final double RUN_JUMP_IMPULSE = 0.15;
    /** How far off the takeoff-landing line the arc may drift. */
    private static final double RUN_JUMP_OFF_LINE = 0.75;

    /**
     * Whether a run-jump in flight is still the jump that was planned: no
     * sudden change of velocity since the last tick and still near its line.
     * While it is, the fall is a planned one (the reflexes leave it alone,
     * even over a void the arc crosses).
     */
    private boolean runJumpOnArc(LivingEntity entity) {
        me.advait.mai.pathetic.movement.RunJump.Plan plan = runJump;
        Location from = runJumpFrom;
        if (plan == null || from == null) return false;
        org.bukkit.util.Vector v = entity.getVelocity(), last = runJumpLastVelocity;
        if (last != null) {
            if (Math.hypot(v.getX() - last.getX(), v.getZ() - last.getZ()) > RUN_JUMP_IMPULSE) return false;
            if (v.getY() > last.getY() + 0.05) return false;
        }
        double lx = plan.landX() - from.getX(), lz = plan.landZ() - from.getZ(), len = Math.hypot(lx, lz);
        if (len < 1e-6) return true;
        Location at = entity.getLocation();
        double off = Math.abs((at.getX() - from.getX()) * lz - (at.getZ() - from.getZ()) * lx) / len;
        return off <= RUN_JUMP_OFF_LINE;
    }

    /**
     * Sprint-jumps along the route like a player holding space (see
     * {@link me.advait.mai.pathetic.movement.RunJump}): on the ground at
     * sprint speed on a plain ground move, take any jump that lands back on
     * the route further along; in the air hold W, sprint and the planned
     * heading; on landing pick up the waypoint the bot is now at. Returns
     * true while it has the controls this tick.
     */
    private boolean driveRunJump(LivingEntity entity, Location current) {
        if (currentPath == null || pathIndex >= currentPath.size()) {
            runJump = null;
            return false;
        }
        boolean footing = entity.isOnGround() || entity.isInWater() || entity.isClimbing();
        if (runJump != null) {
            // Knocked off the arc (a hit, a wall): the jump is over, and the
            // survival reflexes may take the controls (holding W along the old
            // heading carried the bot away from its clutch).
            if (!footing && (!runJumpOnArc(entity) || me.advait.mai.body.Reflexes.isActive(humanoid))) {
                PathDebugLog.event("RUN_JUMP_ABORT bot=(%.2f,%.2f,%.2f)", current.getX(), current.getY(), current.getZ());
                runJump = null;
                return false;
            }
            if (!footing && tickCtx.totalTicks - runJumpStart < 40) {
                runJumpLastVelocity = entity.getVelocity();
                tickCtx.body().look(runJump.yaw(), 0f).keys(1, 0).sprint(true);
                return true;
            }
            landRunJump(current);
            runJump = null;
        }
        if (!config.isSprintJumpFlat() || me.advait.mai.body.Reflexes.isActive(humanoid)) return false;
        if (!entity.isOnGround() || entity.isInWater() || tickCtx.jumpCooldown > 0) return false;
        if (!tickCtx.body().sprintingAtTickStart()) return false;
        AnnotatedWaypoint wp = currentPath.get(pathIndex);
        if (wp.type() == null || !RUN_JUMP_MOVES.contains(wp.type().key())) return false;
        java.util.List<double[]> route = new java.util.ArrayList<>();
        route.add(new double[]{current.getX(), current.getY(), current.getZ()});
        double len = 0;
        // The route runs to the end of the path and the walk stops there
        // (no arrival radius): the jump has to leave room to stop.
        boolean stopAtEnd = arrivalRadius == 0;
        for (int i = pathIndex; i < currentPath.size(); i++) {
            if (len >= RUN_JUMP_ROUTE) { stopAtEnd = false; break; }
            AnnotatedWaypoint w = currentPath.get(i);
            // A waypoint the bot is already past (a landing overshot it) isn't a place to aim for.
            if (i == pathIndex && i + 1 < currentPath.size()) {
                AnnotatedWaypoint n = currentPath.get(i + 1);
                if ((w.x() - current.getX()) * (n.x() - w.x()) + (w.z() - current.getZ()) * (n.z() - w.z()) < 0
                        && Math.abs(w.y() - current.getY()) < 0.6) continue;
            }
            if (w.type() == null || !RUN_JUMP_MOVES.contains(w.type().key())) { stopAtEnd = false; break; }
            double[] last = route.getLast();
            len += Math.hypot(w.x() - last[0], w.z() - last[2]);
            route.add(new double[]{w.x(), w.y(), w.z()});
        }
        // Moving along the route already (toward its point two blocks on, not
        // the current waypoint, which a landing can leave just behind).
        double[] ahead = null;
        double run = 0;
        for (int i = 1; i < route.size() && ahead == null; i++) {
            double[] a = route.get(i - 1), b = route.get(i);
            double seg = Math.hypot(b[0] - a[0], b[2] - a[2]);
            if (run + seg >= 2.0 || i == route.size() - 1) ahead = b;
            run += seg;
        }
        if (ahead == null) return false;
        double ax = ahead[0] - current.getX(), az = ahead[2] - current.getZ(), al = Math.hypot(ax, az);
        org.bukkit.util.Vector v = entity.getVelocity();
        if (al < 1e-3 || (v.getX() * ax + v.getZ() * az) / al < 0.12) return false;
        me.advait.mai.pathetic.movement.RunJump.Plan plan = me.advait.mai.pathetic.movement.RunJump.plan(tickCtx, route, stopAtEnd);
        if (plan == null) {
            if (debugMode) PathDebugLog.event("RUN_JUMP_NO %s", me.advait.mai.pathetic.movement.RunJump.lastReject);
            return false;
        }
        PathDebugLog.event("RUN_JUMP bot=(%.2f,%.2f,%.2f) land=(%.2f,%.2f,%.2f) ticks=%d along=%.2f",
                current.getX(), current.getY(), current.getZ(), plan.landX(), plan.landY(), plan.landZ(),
                plan.ticks(), plan.progress());
        runJump = plan;
        runJumpStart = tickCtx.totalTicks;
        runJumpFrom = current.clone();
        runJumpLastVelocity = null;
        tickCtx.body().look(plan.yaw(), 0f).keys(1, 0).sprint(true);
        MovementExecutors.jump(tickCtx);
        return true;
    }

    /** After a run-jump: continue from the ground move whose segment the bot landed on. */
    private void landRunJump(Location at) {
        int index = pathIndex;
        double best = 1.5;
        for (int j = pathIndex; j < Math.min(currentPath.size(), pathIndex + 12); j++) {
            AnnotatedWaypoint b = currentPath.get(j);
            if (b.type() == null || !RUN_JUMP_MOVES.contains(b.type().key())) break;
            AnnotatedWaypoint a = j > 0 ? currentPath.get(j - 1) : b;
            double sx = b.x() - a.x(), sz = b.z() - a.z(), len2 = sx * sx + sz * sz;
            double t = len2 < 1e-9 ? 1 : Math.max(0, Math.min(1, ((at.getX() - a.x()) * sx + (at.getZ() - a.z()) * sz) / len2));
            double d = Math.hypot(at.getX() - (a.x() + sx * t), at.getZ() - (a.z() + sz * t))
                    + Math.abs(at.getY() - (a.y() + (b.y() - a.y()) * t)) * 2;
            if (d <= best + 1e-6) {
                best = d;
                index = j;
            }
        }
        PathDebugLog.event("RUN_JUMP_LANDED bot=(%.2f,%.2f,%.2f) index=%d->%d",
                at.getX(), at.getY(), at.getZ(), pathIndex, index);
        if (index != pathIndex) {
            pathIndex = index;
            tickCtx.subAction = null;
            tickCtx.phase = 0;
            tickCtx.ticksOnMovement = 0;
        }
        tickCtx.bestHorizDistToWaypoint = Double.MAX_VALUE;
        tickCtx.ticksSinceProgress = 0;
    }

    private static final int MAX_SKIPS = 6;
    private static final double COS_25 = Math.cos(Math.toRadians(25));

    /**
     * Handle the transition between waypoints, on the SUCCESS tick before
     * the next waypoint starts executing.
     *
     * <p><b>Pre-jump for upward transitions</b>: if the next waypoint is a
     * step-up, fire its jump now while the bot is still safely on the old
     * support block. Otherwise physics moves the bot during the transition
     * tick; if the next block column has no support underneath, the bot
     * falls before the next waypoint's tick (with its {@code onGround}
     * check) ever fires a jump. This is the staircase/pillar case where each
     * step needs its own jump and the gap between them is too narrow.
     *
     * <p>Sideways momentum left over from a turn is no longer cancelled
     * here: {@link MovementExecutors#walk} steers against it with the view,
     * the way a player does.
     */
    private void handleWaypointTransition() {
        if (pathIndex + 1 >= currentPath.size()) return;
        AnnotatedWaypoint nextWp = currentPath.get(pathIndex + 1);

        MovementType nextType = nextWp.type();
        if (nextType != null && "step_up".equals(nextType.key())
                && tickCtx.onGround() && tickCtx.jumpCooldown == 0
                && MovementExecutors.needsJumpUp(tickCtx.current, nextWp)
                && MovementExecutors.stepUpJumpNow(tickCtx, nextWp)) {
            PathDebugLog.event("PREJUMP nextType=step_up nextWp=(%.2f,%.2f,%.2f)",
                    nextWp.x(), nextWp.y(), nextWp.z());
            MovementExecutors.jump(tickCtx);
        }
    }

    /**
     * Switches to {@code path}, starting at the first waypoint the bot hasn't
     * reached yet. The path starts where the bot stood when it was requested;
     * by the time it arrives the bot has moved on, and starting at index 1
     * would send it back to that spot (a visible stutter on every replan of a
     * chase). The bot is matched to the nearest of the first few segments;
     * ties go to the later one, so standing on a jump's takeoff picks the jump.
     */
    private void adoptPath(AnnotatedPath path, Location at) {
        int index = Math.min(1, path.size() - 1);
        double best = SPLICE_MAX_DIST;
        for (int j = 1; j < Math.min(path.size(), SPLICE_SEGMENTS + 1); j++) {
            AnnotatedWaypoint a = path.get(j - 1), b = path.get(j);
            double sx = b.x() - a.x(), sz = b.z() - a.z();
            double len2 = sx * sx + sz * sz;
            double t = len2 < 1e-9 ? 1 : Math.max(0, Math.min(1,
                    ((at.getX() - a.x()) * sx + (at.getZ() - a.z()) * sz) / len2));
            double px = a.x() + sx * t, pz = a.z() + sz * t, py = a.y() + (b.y() - a.y()) * t;
            double d = Math.hypot(at.getX() - px, at.getZ() - pz) + Math.abs(at.getY() - py) * 2;
            if (d <= best + 1e-6) { best = d; index = j; }
            // Never skip a block edit (a pillar, a bridge block, a mined
            // block): being near its cell doesn't mean it was done.
            MovementType t2 = b.type();
            if (t2 != null && EDIT_MOVES.contains(t2.key())) break;
        }
        if (index > 1) PathDebugLog.event("PATH_SPLICE start=%d dist=%.2f", index, best);
        // A chase replans every few blocks, and the new path usually goes on
        // with the very movement in progress (the run-up to the same jump).
        // Keep its state then: resetting it restarts the run-up or the jump
        // timing, which cost a few ticks per jump.
        boolean sameMove = false;
        if (currentPath != null && pathIndex < currentPath.size()) {
            AnnotatedWaypoint now = currentPath.get(pathIndex), next = path.get(index);
            sameMove = now.type() != null && next.type() != null && now.type().key().equals(next.type().key())
                    && Math.floor(now.x()) == Math.floor(next.x())
                    && Math.floor(now.y()) == Math.floor(next.y())
                    && Math.floor(now.z()) == Math.floor(next.z());
        }
        currentPath = path;
        pathIndex = index;
        if (sameMove) return;
        tickCtx.subAction = null;
        tickCtx.phase = 0;
        tickCtx.ticksOnMovement = 0;
        tickCtx.bestHorizDistToWaypoint = Double.MAX_VALUE;
        tickCtx.ticksSinceProgress = 0;
    }

    private static final java.util.Set<String> EDIT_MOVES = java.util.Set.of(
            "pillar_up", "bridge_place", "mine_through", "mine_step_up", "water_clutch");
    private static final int SPLICE_SEGMENTS = 8;
    private static final double SPLICE_MAX_DIST = 1.5;

    /** Clear the current path so the next tick triggers a replan. */
    private void resetPathState() {
        pendingPath = null;
        currentPath = null;
        pathIndex = 0;
        tickCtx.subAction = null;
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
        // Slow down to stop on an exact block; a chase (arrival radius) runs in.
        // Horizontal distance: on the last step of a descent the bot is still
        // a block or two above the target when it has to start slowing.
        double distToTarget = Math.hypot(tickCtx.current.getX() - target.getX(), tickCtx.current.getZ() - target.getZ());
        boolean nearDestination = arrivalRadius == 0 && distToTarget < 1.2
                && Math.abs(tickCtx.current.getY() - target.getY()) < 2.5;

        // Step-ups are sprint-jumped like a player does: slowing to a walk
        // before every rise is what made hills crawl.
        if (nearDestination) return 1.0;
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
        if (dot >= 0.5) return false;
        // Players sprint round corners; the overshoot only matters when the
        // ground past the corner (straight on) is missing.
        World world = tickCtx.current.getWorld();
        int bx = (int) Math.floor(current.x() + Math.signum(dx1) * (Math.abs(dx1) >= 0.5 ? 1 : 0));
        int bz = (int) Math.floor(current.z() + Math.signum(dz1) * (Math.abs(dz1) >= 0.5 ? 1 : 0));
        int by = (int) Math.floor(current.y());
        org.bukkit.block.Block beyond = world.getBlockAt(bx, by, bz);
        if (beyond.getType().isSolid()) return false;   // a wall stops the overshoot
        return !world.getBlockAt(bx, by - 1, bz).getType().isSolid();
    }

    /**
     * Cheap lookahead validation: check the next {@code movement.navigation.lookahead_depth}
     * waypoints are still sensible. Returns false if any upcoming waypoint
     * is no longer allowed (capability change) or no longer standable
     * (landing block was removed). That's enough to catch the world-change
     * cases that should trigger a replan.
     *
     * <p>We deliberately do <em>not</em> re-classify the transition
     * between waypoints here. After {@link AnnotatedPath#simplify()} drops
     * collinear intermediates, two adjacent waypoints in {@code currentPath}
     * may span several blocks — re-classifying that span produces a
     * different movement type (e.g. walk→sprint-jump over 3 blocks) and
     * invalidates the path every single tick.
     */
    private boolean isUpcomingPathValid() {
        if (currentPath == null || pathIndex >= currentPath.size()) return false;

        World world = humanoid.getEntity().getLocation().getWorld();
        if (world == null) return false;

        HumanoidCapabilities caps = HumanoidCapabilities.from(humanoid, config);
        MaterialProvider materials = MaterialProvider.fromWorld(world);
        PathContext ctx = new PathContext(caps, config, materials);

        int start = Math.max(1, pathIndex);
        int end = Math.min(pathIndex + config.getLookaheadDepth(), currentPath.size());

        for (int i = start; i < end; i++) {
            AnnotatedWaypoint curr = currentPath.get(i);
            MovementType type = curr.type();
            if (type == null) continue;

            // Capability still permits this type.
            if (!type.isAllowed(caps)) return false;

            // Landing block for this waypoint is still a valid endpoint
            // (wasn't destroyed or newly obstructed).
            PathPosition pos = PathPosition.of(
                    (int) Math.floor(curr.x()), (int) curr.y(), (int) Math.floor(curr.z()));
            if (!type.canReachAsEndpoint(pos, ctx)) return false;
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
        // Must be standing: passing over the target column mid-jump is not
        // arriving (the bot then drifts off and the walk is reported done).
        LivingEntity e = humanoid.getEntity();
        if (e == null || !e.isOnGround()) return false;
        if (arrivalRadius > 0) {
            double dx = current.getX() - target.getX(), dz = current.getZ() - target.getZ();
            return dx * dx + dz * dz <= arrivalRadius * arrivalRadius
                    && Math.abs(current.getY() - target.getY()) < 2.5;
        }
        return current.getBlockX() == target.getBlockX()
                && current.getBlockZ() == target.getBlockZ()
                // Standing on it: a ledge a block up whose edge overhangs the
                // target column is not there (it ended walks a block too high).
                && Math.abs(current.getY() - target.getY()) < 0.9;
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

        final Location goal = target;
        PatheticAgent.getInstance().getAnnotatedPath(humanoid, current, goal)
                .thenAccept(pathOpt -> {
                    pathfindingInProgress.set(false);
                    if (resultFuture.isDone()) return;

                    if (pathOpt.isPresent()) {
                        AnnotatedPath newPath = pathOpt.get();
                        PathDebugLog.event("PATH_RECEIVED waypoints=%d firstType=%s lastType=%s",
                                newPath.size(),
                                newPath.size() > 1 && newPath.get(1).type() != null ? newPath.get(1).type().key() : "?",
                                newPath.size() > 0 && newPath.get(newPath.size() - 1).type() != null ? newPath.get(newPath.size() - 1).type().key() : "?");
                        hasEverHadPath = true;
                        // The bot kept moving while this was computed. With no
                        // path it adopts it now; otherwise the swap waits for
                        // a tick where the running movement is safe to cancel.
                        LivingEntity e = humanoid.getRawMannequin();
                        if (currentPath == null && e != null) adoptPath(newPath, e.getLocation());
                        else pendingPath = newPath;
                    } else if (!hasEverHadPath) {
                        PathDebugLog.event("PATH_REJECTED no valid path to target");
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
        ACTIVE.remove(this);
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
                    case MINE -> Color.MAROON;
                    case BRIDGE -> Color.LIME;
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
