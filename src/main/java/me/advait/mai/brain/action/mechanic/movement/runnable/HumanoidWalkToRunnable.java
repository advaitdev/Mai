package me.advait.mai.brain.action.mechanic.movement.runnable;

import me.advait.mai.Mai;
import me.advait.mai.body.Humanoid;
import me.advait.mai.brain.action.result.HumanoidActionResult;
import me.advait.mai.pathetic.BlockClassifier;
import me.advait.mai.pathetic.PatheticAgent;
import me.advait.mai.pathetic.config.MovementConfig;
import me.advait.mai.pathetic.movement.ExecutionHint;
import me.advait.mai.pathetic.path.AnnotatedPath;
import me.advait.mai.pathetic.path.AnnotatedWaypoint;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tick-by-tick movement executor with locomotion-based dispatch.
 *
 * <h3>Physics model</h3>
 * The Mannequin entity has gravity=true and AI=false. The server applies
 * gravity, vertical drag (0.98), and horizontal friction (block slipperiness * 0.91)
 * every tick AFTER our setVelocity() call. Therefore we must NOT apply drag/friction
 * ourselves — we only add acceleration impulses on top of the already-dragged velocity
 * we read back from the entity each tick.
 *
 * <h3>Sprint-jump boost</h3>
 * Vanilla applies the 0.2 sprint-jump boost BEFORE travel() on the jump tick,
 * and ground friction (0.546) is applied on the jump tick itself (onGround hasn't
 * updated yet). We replicate this by adding boost + jump velocity in one tick.
 */
public class HumanoidWalkToRunnable extends BukkitRunnable {

    // Sprint air acceleration: vanilla 0.02 + sprint bonus 0.006 = 0.026
    private static final double SPRINT_AIR_ACCEL = 0.026;

    private static boolean debugMode = false;

    private final Humanoid humanoid;
    private final Location target;
    private final CompletableFuture<HumanoidActionResult> resultFuture;
    private final MovementConfig config;

    // Path state
    private AnnotatedPath currentPath;
    private int pathIndex = 0;
    private boolean hasEverHadPath = false;

    // Stuck detection
    private int timeStuck = 0;
    private Location previousLocation = null;

    // Path refresh
    private int ticksSincePathUpdate;
    private final AtomicBoolean pathfindingInProgress = new AtomicBoolean(false);

    // Jump state
    private int jumpCooldown = 0;

    // Parkour state machine
    private enum ParkourPhase { NONE, APPROACHING, IN_AIR }
    private ParkourPhase parkourPhase = ParkourPhase.NONE;

    public HumanoidWalkToRunnable(Humanoid humanoid, Location target,
                                  CompletableFuture<HumanoidActionResult> resultFuture) {
        this.humanoid = humanoid;
        this.target = target;
        this.resultFuture = resultFuture;
        this.config = PatheticAgent.getInstance().getConfig();
        this.ticksSincePathUpdate = config.getPathUpdateInterval(); // trigger immediate
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

        // Arrival check
        if (current.distanceSquared(target) < config.getNearTargetDistance() * config.getNearTargetDistance()) {
            complete(true, "Arrived at destination.");
            return;
        }

        // Stuck detection
        if (previousLocation != null) {
            if (previousLocation.getBlockX() == current.getBlockX()
                    && previousLocation.getBlockY() == current.getBlockY()
                    && previousLocation.getBlockZ() == current.getBlockZ()) {
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

        if (jumpCooldown > 0) jumpCooldown--;

        // Path management — request new path frequently
        ticksSincePathUpdate++;
        boolean needPath = currentPath == null
                || pathIndex >= currentPath.size()
                || ticksSincePathUpdate >= config.getPathUpdateInterval();

        if (needPath && !pathfindingInProgress.get()) {
            requestNewPath(current);
        }

        // Move along path
        if (currentPath != null && pathIndex < currentPath.size()) {
            moveToWaypoint(entity, current);
        }

        if (debugMode) showDebugParticles(current);
    }

    private void requestNewPath(Location current) {
        pathfindingInProgress.set(true);
        ticksSincePathUpdate = 0;

        PatheticAgent.getInstance().getAnnotatedPath(current, target)
                .thenAccept(pathOpt -> {
                    pathfindingInProgress.set(false);
                    if (resultFuture.isDone()) return;

                    if (pathOpt.isPresent()) {
                        AnnotatedPath newPath = pathOpt.get();
                        int startIdx = findClosestWaypoint(newPath, current);
                        currentPath = newPath;
                        pathIndex = startIdx;
                        hasEverHadPath = true;
                        parkourPhase = ParkourPhase.NONE;
                    } else if (!hasEverHadPath) {
                        complete(false, "Pathfinding failed.");
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

    private int findClosestWaypoint(AnnotatedPath path, Location current) {
        Vector currentVec = current.toVector();
        double minDist = Double.MAX_VALUE;
        int closest = 0;
        for (int i = 0; i < path.size(); i++) {
            double dist = currentVec.distanceSquared(path.get(i).toVector());
            if (dist < minDist) { minDist = dist; closest = i; }
        }
        return closest;
    }

    // ===================== Movement Dispatch =====================

    private void moveToWaypoint(LivingEntity entity, Location current) {
        AnnotatedWaypoint waypoint = currentPath.get(pathIndex);
        ExecutionHint hint = waypoint.hint();

        // For parkour IN_AIR, don't advance until we land
        if (parkourPhase == ParkourPhase.IN_AIR) {
            executeSprintJump(entity, current, waypoint, hint);
            return;
        }

        // Check if we've reached this waypoint
        double horizDist = horizontalDistance(current, waypoint);
        double vertDist = Math.abs(current.getY() - waypoint.y());

        if (horizDist < config.getWaypointRadius() && vertDist < 1.5) {
            pathIndex++;
            parkourPhase = ParkourPhase.NONE;
            if (pathIndex >= currentPath.size()) return;
            waypoint = currentPath.get(pathIndex);
            hint = waypoint.hint();
        }

        if (hint == null) {
            executeSprint(entity, current, waypoint);
            return;
        }

        switch (hint.locomotion()) {
            case WALK -> executeSprint(entity, current, waypoint); // default to sprint
            case SPRINT -> executeSprint(entity, current, waypoint);
            case SPRINT_JUMP -> executeSprintJump(entity, current, waypoint, hint);
            case SNEAK -> executeSneak(entity, current, waypoint);
            case SWIM -> executeSwim(entity, current, waypoint);
            case CLIMB -> executeClimb(entity, current, waypoint);
        }
    }

    // ===================== Locomotion Implementations =====================

    /**
     * Sprint movement. Server already applied friction to the velocity we read,
     * so we only add our acceleration impulse — no drag multiplication.
     *
     * <p>Ground acceleration uses vanilla normalization:
     * accel = moveSpeed * (0.16277136 / (slip * 0.91)^3)
     * This is the impulse to ADD to already-dragged velocity.
     */
    private void executeSprint(LivingEntity entity, Location current, AnnotatedWaypoint wp) {
        Vector vel = entity.getVelocity();
        double[] dir = directionTo(current, wp);
        boolean onGround = entity.isOnGround();

        double vx, vz;
        if (onGround) {
            double sprintSpeed = config.getWalkAcceleration() * config.getSprintFactor();
            double slip = getSlipperiness(current);
            double inertia = slip * 0.91;
            double accel = sprintSpeed * (0.16277136 / (inertia * inertia * inertia));
            // Server already applied inertia drag to vel — just add our impulse
            vx = vel.getX() + dir[0] * accel;
            vz = vel.getZ() + dir[1] * accel;
        } else {
            vx = vel.getX() + dir[0] * SPRINT_AIR_ACCEL;
            vz = vel.getZ() + dir[1] * SPRINT_AIR_ACCEL;
        }

        double vy = vel.getY();
        if (onGround && jumpCooldown == 0 && shouldJump(current, wp, dir)) {
            vy = config.getJumpVelocity();
            jumpCooldown = 10;
        }

        faceDirection(entity, dir);
        entity.setVelocity(new Vector(vx, vy, vz));
    }

    /**
     * Sprint-jump with parkour state machine.
     *
     * <p>Vanilla sprint-jump sequence:
     * <ol>
     *   <li>Sprint toward edge, building momentum</li>
     *   <li>At edge: set vy=0.42, add 0.2 boost in yaw direction</li>
     *   <li>In air: air acceleration (0.026) toward target, server handles drag</li>
     *   <li>Land: advance to next waypoint</li>
     * </ol>
     */
    private void executeSprintJump(LivingEntity entity, Location current,
                                   AnnotatedWaypoint wp, ExecutionHint hint) {
        Vector vel = entity.getVelocity();
        double[] dir = directionTo(current, wp);
        boolean onGround = entity.isOnGround();

        switch (parkourPhase) {
            case NONE, APPROACHING -> {
                if (!onGround) {
                    // Airborne unexpectedly — apply air control toward target
                    double vx = vel.getX() + dir[0] * SPRINT_AIR_ACCEL;
                    double vz = vel.getZ() + dir[1] * SPRINT_AIR_ACCEL;
                    entity.setVelocity(new Vector(vx, vel.getY(), vz));
                    faceDirection(entity, dir);
                    return;
                }

                // Sprint toward the edge with ground acceleration
                double sprintSpeed = config.getWalkAcceleration() * config.getSprintFactor();
                double slip = getSlipperiness(current);
                double inertia = slip * 0.91;
                double accel = sprintSpeed * (0.16277136 / (inertia * inertia * inertia));
                double vx = vel.getX() + dir[0] * accel;
                double vz = vel.getZ() + dir[1] * accel;

                // Check if near block edge — time to jump
                if (hint != null && hint.edgeJump() && isNearBlockEdge(current, dir)) {
                    // JUMP! Vanilla order: vel.y = 0.42, then add 0.2 sprint boost
                    double vy = config.getJumpVelocity();
                    float yawRad = (float) Math.toRadians(entity.getLocation().getYaw());
                    vx += -Math.sin(yawRad) * config.getSprintJumpBoost();
                    vz += Math.cos(yawRad) * config.getSprintJumpBoost();
                    entity.setVelocity(new Vector(vx, vy, vz));
                    parkourPhase = ParkourPhase.IN_AIR;
                    jumpCooldown = 10;
                } else {
                    entity.setVelocity(new Vector(vx, vel.getY(), vz));
                    parkourPhase = ParkourPhase.APPROACHING;
                }
                faceDirection(entity, dir);
            }
            case IN_AIR -> {
                // Air control: sprint air accel toward landing target
                double vx = vel.getX() + dir[0] * SPRINT_AIR_ACCEL;
                double vz = vel.getZ() + dir[1] * SPRINT_AIR_ACCEL;
                entity.setVelocity(new Vector(vx, vel.getY(), vz));
                faceDirection(entity, dir);

                // Check if we've landed
                if (onGround && vel.getY() <= 0) {
                    parkourPhase = ParkourPhase.NONE;
                    pathIndex++;
                }
            }
        }
    }

    private void executeSneak(LivingEntity entity, Location current, AnnotatedWaypoint wp) {
        Vector vel = entity.getVelocity();
        double[] dir = directionTo(current, wp);
        boolean onGround = entity.isOnGround();

        double vx, vz;
        if (onGround) {
            double sneakSpeed = config.getWalkAcceleration() * config.getSneakFactor();
            double slip = getSlipperiness(current);
            double inertia = slip * 0.91;
            double accel = sneakSpeed * (0.16277136 / (inertia * inertia * inertia));
            vx = vel.getX() + dir[0] * accel;
            vz = vel.getZ() + dir[1] * accel;
        } else {
            vx = vel.getX() + dir[0] * config.getAirAcceleration();
            vz = vel.getZ() + dir[1] * config.getAirAcceleration();
        }

        faceDirection(entity, dir);
        entity.setVelocity(new Vector(vx, vel.getY(), vz));
    }

    private void executeSwim(LivingEntity entity, Location current, AnnotatedWaypoint wp) {
        double[] dir3d = directionTo3D(current, wp);
        Vector vel = entity.getVelocity();

        // Water has its own drag model (0.8 horizontal, 0.8 vertical)
        // Server applies water drag; we add swim impulse
        double swimAccel = 0.04;
        double vx = vel.getX() + dir3d[0] * swimAccel;
        double vy = vel.getY() + dir3d[1] * swimAccel;
        double vz = vel.getZ() + dir3d[2] * swimAccel;

        faceDirection(entity, new double[]{dir3d[0], dir3d[2]});
        entity.setVelocity(new Vector(vx, vy, vz));
    }

    private void executeClimb(LivingEntity entity, Location current, AnnotatedWaypoint wp) {
        double dy = wp.y() - current.getY();
        double[] dir = directionTo(current, wp);

        // Ladder climb: vanilla ~0.12 up, ~0.15 down
        double climbSpeed = dy >= 0 ? 0.12 : -0.15;
        Vector vel = entity.getVelocity();
        double vx = vel.getX() + dir[0] * 0.02;
        double vz = vel.getZ() + dir[1] * 0.02;

        faceDirection(entity, dir);
        entity.setVelocity(new Vector(vx, climbSpeed, vz));
    }

    // ===================== Helpers =====================

    private double getSlipperiness(Location loc) {
        Material below = loc.clone().add(0, -1, 0).getBlock().getType();
        return BlockClassifier.slipperiness(below);
    }

    private boolean shouldJump(Location current, AnnotatedWaypoint wp, double[] dir) {
        // Jump if waypoint is above us
        if (wp.y() > current.getY() + 0.5) return true;

        // Jump if there's a solid block ahead at feet level with space above
        if (current.getWorld() != null) {
            Location ahead = current.clone().add(dir[0] * 0.6, 0, dir[1] * 0.6);
            Material blockAhead = ahead.getBlock().getType();
            Material aboveAhead = ahead.clone().add(0, 1, 0).getBlock().getType();
            if (BlockClassifier.isSolid(blockAhead) && !BlockClassifier.isSolid(aboveAhead)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the entity is near the edge of the current block in the
     * movement direction. The player hitbox is 0.6 wide (0.3 from center),
     * so the optimal jump point is when the center is ~0.2 blocks from the edge.
     *
     * <p>Thresholds: 0.75 / 0.25 — this gives ~0.25 blocks before the edge,
     * close to optimal while leaving a small margin of safety.
     */
    private boolean isNearBlockEdge(Location loc, double[] dir) {
        double posInBlockX = loc.getX() - Math.floor(loc.getX());
        double posInBlockZ = loc.getZ() - Math.floor(loc.getZ());

        if (dir[0] > 0.3 && posInBlockX > 0.75) return true;
        if (dir[0] < -0.3 && posInBlockX < 0.25) return true;
        if (dir[1] > 0.3 && posInBlockZ > 0.75) return true;
        if (dir[1] < -0.3 && posInBlockZ < 0.25) return true;
        return false;
    }

    private static double[] directionTo(Location current, AnnotatedWaypoint wp) {
        double dx = wp.x() - current.getX();
        double dz = wp.z() - current.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len > 0.001) { dx /= len; dz /= len; }
        return new double[]{dx, dz};
    }

    private static double[] directionTo3D(Location current, AnnotatedWaypoint wp) {
        double dx = wp.x() - current.getX();
        double dy = wp.y() - current.getY();
        double dz = wp.z() - current.getZ();
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 0.001) { dx /= len; dy /= len; dz /= len; }
        return new double[]{dx, dy, dz};
    }

    private static double horizontalDistance(Location loc, AnnotatedWaypoint wp) {
        double dx = loc.getX() - wp.x();
        double dz = loc.getZ() - wp.z();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private void faceDirection(LivingEntity entity, double[] dir) {
        if (dir[0] == 0 && dir[1] == 0) return;
        float yaw = (float) Math.toDegrees(Math.atan2(-dir[0], dir[1]));
        entity.setRotation(yaw, 0);
    }

    private void complete(boolean success, String message) {
        if (!resultFuture.isDone()) {
            resultFuture.complete(new HumanoidActionResult(success, message));
        }
        cancel();
    }

    // ===================== Debug =====================

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
}
