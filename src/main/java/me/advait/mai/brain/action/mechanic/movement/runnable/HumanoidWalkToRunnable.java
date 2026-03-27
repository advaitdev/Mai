package me.advait.mai.brain.action.mechanic.movement.runnable;

import me.advait.mai.Mai;
import me.advait.mai.Settings;
import me.advait.mai.body.Humanoid;
import me.advait.mai.brain.action.result.HumanoidActionResult;
import me.advait.mai.pathetic.PatheticAgent;
import me.advait.mai.util.LocationUtil;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Moves a humanoid's mannequin to a target using pathfinding with vanilla-like physics.
 *
 * Gravity and collision are handled by the server (Mannequin has gravity=true, AI=false).
 * This runnable only applies horizontal velocity and jump impulses — it never touches Y
 * velocity except when jumping, so gravity accumulates naturally.
 */
public class HumanoidWalkToRunnable extends BukkitRunnable {

    // Vanilla-like movement constants
    private static final double WALK_ACCELERATION = 0.1;   // ground acceleration per tick
    private static final double GROUND_DRAG = 0.546;        // 0.6 (slipperiness) * 0.91 (drag)
    private static final double AIR_ACCELERATION = 0.02;    // air control per tick
    private static final double AIR_DRAG = 0.91;            // horizontal drag in air
    private static final double JUMP_VELOCITY = 0.42;       // vanilla jump Y velocity
    private static final double WAYPOINT_RADIUS = 0.5;
    private static final int PATH_UPDATE_INTERVAL = 40;

    private static boolean debugMode = false;

    private final Humanoid humanoid;
    private final Location target;
    private final CompletableFuture<HumanoidActionResult> resultFuture;

    private List<Vector> waypoints = new ArrayList<>();
    private int pathIndex = 0;
    private int timeStuck = 0;
    private Location previousLocation = null;
    private int ticksSincePathUpdate = PATH_UPDATE_INTERVAL;
    private final AtomicBoolean pathfindingInProgress = new AtomicBoolean(false);
    private int jumpCooldown = 0;

    public HumanoidWalkToRunnable(Humanoid humanoid, Location target, CompletableFuture<HumanoidActionResult> resultFuture) {
        this.humanoid = humanoid;
        this.target = target;
        this.resultFuture = resultFuture;
    }

    public static void setDebugMode(boolean enabled) {
        debugMode = enabled;
    }

    public static boolean isDebugMode() {
        return debugMode;
    }

    @Override
    public void run() {
        // Stop if future was completed externally (e.g. cancelled)
        if (resultFuture.isDone()) {
            cancel();
            return;
        }

        if (humanoid.getEntity() == null || !humanoid.getEntity().isValid()) {
            resultFuture.complete(new HumanoidActionResult(false, "Entity is no longer valid."));
            cancel();
            return;
        }

        Location current = humanoid.getEntity().getLocation();

        if (LocationUtil.isNearDestination(current, target)) {
            resultFuture.complete(new HumanoidActionResult(true, "Arrived at destination."));
            cancel();
            return;
        }

        // Stuck detection
        int timeoutTicks = Settings.HUMANOID_PATHFINDING_TIMEOUT * 20;
        if (previousLocation != null && timeStuck >= timeoutTicks
                && previousLocation.getBlockX() == current.getBlockX()
                && previousLocation.getBlockY() == current.getBlockY()
                && previousLocation.getBlockZ() == current.getBlockZ()) {
            resultFuture.complete(new HumanoidActionResult(false, "Stuck at the same location for too long."));
            cancel();
            return;
        }

        if (previousLocation != null && previousLocation.getBlockX() == current.getBlockX()
                && previousLocation.getBlockZ() == current.getBlockZ()) {
            timeStuck++;
        } else {
            timeStuck = 0;
        }
        previousLocation = current.clone();

        if (jumpCooldown > 0) jumpCooldown--;

        // Path recalculation
        ticksSincePathUpdate++;
        boolean needPath = waypoints.isEmpty() || pathIndex >= waypoints.size() || ticksSincePathUpdate >= PATH_UPDATE_INTERVAL;

        if (needPath && !pathfindingInProgress.get()) {
            requestNewPath(current);
        }

        moveAlongPath(current);

        if (debugMode) {
            showDebugParticles(current);
        }
    }

    private void requestNewPath(Location current) {
        pathfindingInProgress.set(true);
        ticksSincePathUpdate = 0;

        PatheticAgent.getInstance().getGroundPath(current, target)
                .thenAccept(result -> Mai.getInstance().getServer().getScheduler().runTask(Mai.getInstance(), () -> {
                    pathfindingInProgress.set(false);
                    if (resultFuture.isDone()) return;

                    if (result.successful()) {
                        List<Vector> newWaypoints = PatheticAgent.processPath(result.getPath());

                        int newStartIndex = findClosestWaypointIndex(newWaypoints, current);
                        waypoints = newWaypoints;
                        pathIndex = newStartIndex;
                    } else {
                        resultFuture.complete(new HumanoidActionResult(false, "Pathfinding failed."));
                        cancel();
                    }
                }))
                .exceptionally(ex -> {
                    Mai.getInstance().getServer().getScheduler().runTask(Mai.getInstance(), () -> {
                        pathfindingInProgress.set(false);
                        resultFuture.complete(new HumanoidActionResult(false, "Pathfinding error: " + ex.getMessage()));
                        cancel();
                    });
                    return null;
                });
    }

    private int findClosestWaypointIndex(List<Vector> newWaypoints, Location current) {
        if (newWaypoints.isEmpty()) return 0;

        Vector currentVec = current.toVector();
        double minDist = Double.MAX_VALUE;
        int closestIndex = 0;

        for (int i = 0; i < newWaypoints.size(); i++) {
            double dist = newWaypoints.get(i).distanceSquared(currentVec);
            if (dist < minDist) {
                minDist = dist;
                closestIndex = i;
            }
        }

        return closestIndex;
    }

    private void moveAlongPath(Location current) {
        if (pathIndex >= waypoints.size()) return;

        LivingEntity entity = humanoid.getEntity();
        Vector waypoint = waypoints.get(pathIndex);
        Location waypointLoc = new Location(current.getWorld(), waypoint.getX(), waypoint.getY(), waypoint.getZ());

        double horizontalDist = Math.sqrt(
                Math.pow(current.getX() - waypointLoc.getX(), 2) +
                Math.pow(current.getZ() - waypointLoc.getZ(), 2)
        );

        if (horizontalDist < WAYPOINT_RADIUS && Math.abs(current.getY() - waypointLoc.getY()) < 1.5) {
            pathIndex++;
            return;
        }

        // Face the waypoint (uses setRotation, does NOT reset velocity)
        LocationUtil.faceLocation(entity, waypointLoc);

        // Desired horizontal direction
        double dx = waypoint.getX() - current.getX();
        double dz = waypoint.getZ() - current.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len > 0) { dx /= len; dz /= len; }

        boolean onGround = entity.isOnGround();
        Vector currentVelocity = entity.getVelocity();

        // Vanilla-like horizontal movement with acceleration + drag
        double vx, vz;
        if (onGround) {
            vx = currentVelocity.getX() * GROUND_DRAG + dx * WALK_ACCELERATION;
            vz = currentVelocity.getZ() * GROUND_DRAG + dz * WALK_ACCELERATION;
        } else {
            // Reduced air control (vanilla behavior)
            vx = currentVelocity.getX() * AIR_DRAG + dx * AIR_ACCELERATION;
            vz = currentVelocity.getZ() * AIR_DRAG + dz * AIR_ACCELERATION;
        }

        // Y velocity — don't touch it, let the server apply gravity.
        // Only override when jumping.
        double vy = currentVelocity.getY();

        if (onGround && jumpCooldown == 0 && shouldJump(current, dx, dz)) {
            vy = JUMP_VELOCITY;
            jumpCooldown = 10;
        }

        entity.setVelocity(new Vector(vx, vy, vz));
    }

    private boolean shouldJump(Location current, double dirX, double dirZ) {
        if (current.getWorld() == null) return false;

        // Check block ahead at foot level
        Location ahead = current.clone().add(dirX * 0.6, 0, dirZ * 0.6);
        Block blockAhead = ahead.getBlock();
        Block blockAboveAhead = ahead.clone().add(0, 1, 0).getBlock();

        // Jump if solid block at feet but space above
        if (blockAhead.getType().isSolid() && !blockAboveAhead.getType().isSolid()) {
            return true;
        }

        // Jump if next waypoint is higher
        if (pathIndex < waypoints.size()) {
            double yDiff = waypoints.get(pathIndex).getY() - current.getY();
            if (yDiff > 0.5) {
                return true;
            }
        }

        return false;
    }

    private void showDebugParticles(Location current) {
        if (current.getWorld() == null) return;

        current.getWorld().spawnParticle(Particle.DUST, current.clone().add(0, 1, 0),
                1, new Particle.DustOptions(Color.GREEN, 1));

        target.getWorld().spawnParticle(Particle.DUST, target.clone().add(0, 1, 0),
                1, new Particle.DustOptions(Color.RED, 1));

        for (int i = pathIndex; i < Math.min(pathIndex + 10, waypoints.size()); i++) {
            Vector wp = waypoints.get(i);
            Location wpLoc = new Location(current.getWorld(), wp.getX(), wp.getY() + 0.5, wp.getZ());

            Color color = (i == pathIndex) ? Color.YELLOW : Color.AQUA;
            current.getWorld().spawnParticle(Particle.DUST, wpLoc, 1, new Particle.DustOptions(color, 0.7f));
        }
    }
}
