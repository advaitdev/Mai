package me.advait.mai.brain.action.mechanic.movement.runnable;

import de.bsommerfeld.pathetic.api.pathing.result.Path;
import de.bsommerfeld.pathetic.bukkit.mapper.BukkitMapper;
import me.advait.mai.Mai;
import me.advait.mai.Settings;
import me.advait.mai.body.Humanoid;
import me.advait.mai.brain.action.result.HumanoidActionMessage;
import me.advait.mai.brain.action.result.HumanoidActionResult;
import me.advait.mai.pathetic.PatheticAgent;
import me.advait.mai.util.LocationUtil;
import me.advait.mai.util.NPCUtil;
import me.advait.mai.util.PatheticUtil;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Moves a humanoid's mannequin to a target by pathfinding and applying setVelocity each tick.
 */
public class HumanoidWalkToRunnable extends BukkitRunnable {

    private static final double WAYPOINT_RADIUS = 0.7;
    private static final double MOVE_SPEED = 0.25;
    private static final int PATH_UPDATE_INTERVAL = 15;

    private final Humanoid humanoid;
    private final Location target;
    private final CompletableFuture<HumanoidActionResult> resultFuture;

    private List<Vector> waypoints = new ArrayList<>();
    private int pathIndex = 0;
    private Path previousPath = null;
    private int timeStuck = 0;
    private Location previousLocation = null;
    private int ticksSincePathUpdate = 0;

    public HumanoidWalkToRunnable(Humanoid humanoid, Location target, CompletableFuture<HumanoidActionResult> resultFuture) {
        this.humanoid = humanoid;
        this.target = target;
        this.resultFuture = resultFuture;
    }

    @Override
    public void run() {
        if (humanoid.getEntity() == null || !humanoid.getEntity().isValid()) {
            resultFuture.complete(new HumanoidActionResult(false, HumanoidActionMessage.NPC_IS_NULL));
            cancel();
            return;
        }

        Location current = humanoid.getEntity().getLocation();

        if (NPCUtil.isEntityNearDestination(current, target)) {
            resultFuture.complete(new HumanoidActionResult(true, HumanoidActionMessage.WALK_TO_MESSAGE_SUCCESS));
            cancel();
            return;
        }

        if (previousLocation != null && timeStuck >= Settings.HUMANOID_PATHFINDING_TIMEOUT
                && previousLocation.getBlockX() == current.getBlockX()
                && previousLocation.getBlockY() == current.getBlockY()
                && previousLocation.getBlockZ() == current.getBlockZ()) {
            resultFuture.complete(new HumanoidActionResult(false, HumanoidActionMessage.WALK_TO_MESSAGE_STUCK));
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

        ticksSincePathUpdate++;
        boolean needPath = waypoints.isEmpty() || pathIndex >= waypoints.size() || ticksSincePathUpdate >= PATH_UPDATE_INTERVAL;

        if (needPath) {
            ticksSincePathUpdate = 0;
            PatheticAgent.getInstance().getGroundPath(current, target)
                    .thenAccept(result -> Mai.getInstance().getServer().getScheduler().runTask(Mai.getInstance(), () -> {
                        if (result.successful()) {
                            Path path = result.getPath();
                            if (previousPath != null && PatheticUtil.isSubpathEquivalent(previousPath, path)) {
                                return;
                            }
                            waypoints = new ArrayList<>();
                            path.forEach(pp -> waypoints.add(BukkitMapper.toVector(pp.toVector())));
                            previousPath = path;
                            pathIndex = 0;
                        } else {
                            resultFuture.complete(new HumanoidActionResult(false, HumanoidActionMessage.WALK_TO_MESSAGE_FAILURE));
                            cancel();
                        }
                    }))
                    .exceptionally(ex -> {
                        Mai.getInstance().getServer().getScheduler().runTask(Mai.getInstance(), () -> {
                            resultFuture.complete(new HumanoidActionResult(false,
                                    HumanoidActionMessage.WALK_TO_MESSAGE_FAILURE + " (" + ex.getMessage() + ")"));
                            cancel();
                        });
                        return null;
                    });
            return;
        }

        if (pathIndex < waypoints.size()) {
            Vector waypoint = waypoints.get(pathIndex);
            Location waypointLoc = current.getWorld() != null
                    ? new Location(current.getWorld(), waypoint.getX(), waypoint.getY(), waypoint.getZ())
                    : null;
            if (waypointLoc != null) {
                double dist = current.distance(waypointLoc);
                if (dist < WAYPOINT_RADIUS) {
                    pathIndex++;
                } else {
                    LocationUtil.faceLocation(humanoid.getEntity(), waypointLoc);
                    Vector dir = waypoint.clone().subtract(current.toVector()).normalize();
                    Vector velocity = dir.multiply(MOVE_SPEED).setY(0);
                    humanoid.getEntity().setVelocity(velocity);
                }
            }
        }
    }

}
