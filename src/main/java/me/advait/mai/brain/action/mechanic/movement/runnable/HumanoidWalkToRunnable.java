package me.advait.mai.brain.action.mechanic.movement.runnable;

import de.bsommerfeld.pathetic.api.pathing.result.Path;
import de.bsommerfeld.pathetic.bukkit.mapper.BukkitMapper;
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
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Moves a humanoid's mannequin to a target by pathfinding and applying velocity each tick.
 */
public class HumanoidWalkToRunnable extends BukkitRunnable {

    private static final double WAYPOINT_RADIUS = 0.5;
    private static final double MOVE_SPEED = 0.22;
    private static final double JUMP_VELOCITY = 0.42;
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
                    if (result.successful()) {
                        Path path = result.getPath();
                        List<Vector> newWaypoints = new ArrayList<>();
                        path.forEach(pp -> newWaypoints.add(BukkitMapper.toVector(pp.toVector())));

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

        LocationUtil.faceLocation(humanoid.getEntity(), waypointLoc);

        Vector direction = waypoint.clone().subtract(current.toVector());
        direction.setY(0);
        if (direction.lengthSquared() > 0) {
            direction.normalize();
        }

        Vector velocity = direction.multiply(MOVE_SPEED);
        velocity.setY(humanoid.getEntity().getVelocity().getY());

        boolean onGround = humanoid.getEntity().isOnGround();
        if (onGround && jumpCooldown == 0 && shouldJump(current, direction)) {
            velocity.setY(JUMP_VELOCITY);
            jumpCooldown = 10;
        }

        humanoid.getEntity().setVelocity(velocity);
    }

    private boolean shouldJump(Location current, Vector direction) {
        if (current.getWorld() == null || direction.lengthSquared() == 0) return false;

        Location ahead = current.clone().add(direction.clone().normalize().multiply(0.5));
        Block blockAhead = ahead.getBlock();
        Block blockAboveAhead = ahead.clone().add(0, 1, 0).getBlock();

        if (blockAhead.getType().isSolid() && !blockAboveAhead.getType().isSolid()) {
            return true;
        }

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
