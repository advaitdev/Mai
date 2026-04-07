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
import me.advait.mai.util.LocationUtil;
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
 * <p>Reads an {@link AnnotatedPath} and dispatches movement per-waypoint
 * based on the {@link ExecutionHint}: walk, sprint, sprint-jump, sneak, swim, or climb.
 *
 * <p>Physics are vanilla-accurate: ground acceleration with slipperiness normalization,
 * air drag 0.91, jump velocity 0.42, sprint-jump boost 0.2 in facing direction.
 * Gravity and collision are handled by the server (Mannequin has gravity=true).
 */
public class HumanoidWalkToRunnable extends BukkitRunnable {

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
        this.ticksSincePathUpdate = config.getPathUpdateInterval(); // trigger immediate path request
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

        // Path management
        ticksSincePathUpdate++;
        boolean needPath = currentPath == null || pathIndex >= currentPath.size()
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
                    // else: recalculation failed but we have an old path — keep following it
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
            AnnotatedWaypoint wp = path.get(i);
            double dist = currentVec.distanceSquared(wp.toVector());
            if (dist < minDist) {
                minDist = dist;
                closest = i;
            }
        }
        return closest;
    }

    // ===================== Movement Dispatch =====================

    private void moveToWaypoint(LivingEntity entity, Location current) {
        AnnotatedWaypoint waypoint = currentPath.get(pathIndex);
        ExecutionHint hint = waypoint.hint();

        // Check if we've reached this waypoint
        double horizDist = horizontalDistance(current, waypoint);
        double vertDist = Math.abs(current.getY() - waypoint.y());

        // For parkour IN_AIR, don't advance until we land
        if (parkourPhase == ParkourPhase.IN_AIR) {
            executeSprintJump(entity, current, waypoint, hint);
            return;
        }

        if (horizDist < config.getWaypointRadius() && vertDist < 1.5) {
            pathIndex++;
            parkourPhase = ParkourPhase.NONE;
            if (pathIndex >= currentPath.size()) return;
            waypoint = currentPath.get(pathIndex);
            hint = waypoint.hint();
        }

        if (hint == null) {
            // First waypoint or no type — walk toward it
            executeWalk(entity, current, waypoint);
            return;
        }

        switch (hint.locomotion()) {
            case WALK -> executeWalk(entity, current, waypoint);
            case SPRINT -> executeSprint(entity, current, waypoint);
            case SPRINT_JUMP -> executeSprintJump(entity, current, waypoint, hint);
            case SNEAK -> executeSneak(entity, current, waypoint);
            case SWIM -> executeSwim(entity, current, waypoint);
            case CLIMB -> executeClimb(entity, current, waypoint);
        }
    }

    // ===================== Locomotion Implementations =====================

    private void executeWalk(LivingEntity entity, Location current, AnnotatedWaypoint wp) {
        Vector vel = entity.getVelocity();
        double[] dir = directionTo(current, wp);
        boolean onGround = entity.isOnGround();

        double vx, vz;
        if (onGround) {
            double slip = getSlipperiness(current);
            double momentum = slip * 0.91;
            double accel = config.getWalkAcceleration() * accelerationFactor(slip);
            vx = vel.getX() * momentum + dir[0] * accel;
            vz = vel.getZ() * momentum + dir[1] * accel;
        } else {
            vx = vel.getX() * config.getAirDrag() + dir[0] * config.getAirAcceleration();
            vz = vel.getZ() * config.getAirDrag() + dir[1] * config.getAirAcceleration();
        }

        double vy = vel.getY();
        // Jump if waypoint is above us and we're on ground
        if (onGround && jumpCooldown == 0 && shouldJump(current, wp, dir)) {
            vy = config.getJumpVelocity();
            jumpCooldown = 10;
        }

        faceDirection(entity, dir);
        entity.setVelocity(new Vector(vx, vy, vz));
    }

    private void executeSprint(LivingEntity entity, Location current, AnnotatedWaypoint wp) {
        Vector vel = entity.getVelocity();
        double[] dir = directionTo(current, wp);
        boolean onGround = entity.isOnGround();

        double vx, vz;
        if (onGround) {
            double slip = getSlipperiness(current);
            double momentum = slip * 0.91;
            double sprintAccel = config.getWalkAcceleration() * config.getSprintFactor();
            double accel = sprintAccel * accelerationFactor(slip);
            vx = vel.getX() * momentum + dir[0] * accel;
            vz = vel.getZ() * momentum + dir[1] * accel;
        } else {
            vx = vel.getX() * config.getAirDrag() + dir[0] * config.getAirAcceleration();
            vz = vel.getZ() * config.getAirDrag() + dir[1] * config.getAirAcceleration();
        }

        double vy = vel.getY();
        if (onGround && jumpCooldown == 0 && shouldJump(current, wp, dir)) {
            vy = config.getJumpVelocity();
            jumpCooldown = 10;
        }

        faceDirection(entity, dir);
        entity.setVelocity(new Vector(vx, vy, vz));
    }

    private void executeSprintJump(LivingEntity entity, Location current,
                                   AnnotatedWaypoint wp, ExecutionHint hint) {
        Vector vel = entity.getVelocity();
        double[] dir = directionTo(current, wp);
        boolean onGround = entity.isOnGround();

        switch (parkourPhase) {
            case NONE, APPROACHING -> {
                if (!onGround) {
                    // Wait until we're on ground to start approach
                    parkourPhase = ParkourPhase.NONE;
                    executeWalk(entity, current, wp);
                    return;
                }
                // Sprint toward the edge
                double slip = getSlipperiness(current);
                double momentum = slip * 0.91;
                double sprintAccel = config.getWalkAcceleration() * config.getSprintFactor();
                double accel = sprintAccel * accelerationFactor(slip);
                double vx = vel.getX() * momentum + dir[0] * accel;
                double vz = vel.getZ() * momentum + dir[1] * accel;

                if (hint.edgeJump() && isNearBlockEdge(current, dir)) {
                    // Jump! Apply jump velocity + sprint-jump boost
                    double vy = config.getJumpVelocity();
                    float yawRad = (float) Math.toRadians(entity.getLocation().getYaw());
                    vx += -Math.sin(yawRad) * config.getSprintJumpBoost();
                    vz += Math.cos(yawRad) * config.getSprintJumpBoost();
                    entity.setVelocity(new Vector(vx, vy, vz));
                    parkourPhase = ParkourPhase.IN_AIR;
                } else {
                    entity.setVelocity(new Vector(vx, vel.getY(), vz));
                    parkourPhase = ParkourPhase.APPROACHING;
                }
                faceDirection(entity, dir);
            }
            case IN_AIR -> {
                // Air control toward landing
                double vx = vel.getX() * config.getAirDrag() + dir[0] * config.getAirAcceleration();
                double vz = vel.getZ() * config.getAirDrag() + dir[1] * config.getAirAcceleration();
                entity.setVelocity(new Vector(vx, vel.getY(), vz));
                faceDirection(entity, dir);

                // Check if we've landed
                if (onGround) {
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
            double slip = getSlipperiness(current);
            double momentum = slip * 0.91;
            double sneakAccel = config.getWalkAcceleration() * config.getSneakFactor();
            double accel = sneakAccel * accelerationFactor(slip);
            vx = vel.getX() * momentum + dir[0] * accel;
            vz = vel.getZ() * momentum + dir[1] * accel;
        } else {
            vx = vel.getX() * config.getAirDrag() + dir[0] * config.getAirAcceleration();
            vz = vel.getZ() * config.getAirDrag() + dir[1] * config.getAirAcceleration();
        }

        faceDirection(entity, dir);
        entity.setVelocity(new Vector(vx, vel.getY(), vz));
    }

    private void executeSwim(LivingEntity entity, Location current, AnnotatedWaypoint wp) {
        double[] dir3d = directionTo3D(current, wp);
        Vector vel = entity.getVelocity();

        // Swimming uses reduced drag and acceleration
        double swimAccel = config.getAirAcceleration() * 2; // slightly better control in water
        double swimDrag = 0.8;

        double vx = vel.getX() * swimDrag + dir3d[0] * swimAccel;
        double vy = vel.getY() * swimDrag + dir3d[1] * swimAccel;
        double vz = vel.getZ() * swimDrag + dir3d[2] * swimAccel;

        faceDirection(entity, new double[]{dir3d[0], dir3d[2]});
        entity.setVelocity(new Vector(vx, vy, vz));
    }

    private void executeClimb(LivingEntity entity, Location current, AnnotatedWaypoint wp) {
        double dy = wp.y() - current.getY();
        double[] dir = directionTo(current, wp);

        // Ladder climb speed: ~0.12 blocks/tick up, ~0.15 down
        double climbSpeed = dy >= 0 ? 0.12 : -0.15;

        // Small horizontal movement toward the ladder/waypoint
        double horizAccel = 0.02;
        Vector vel = entity.getVelocity();
        double vx = vel.getX() * 0.5 + dir[0] * horizAccel;
        double vz = vel.getZ() * 0.5 + dir[1] * horizAccel;

        faceDirection(entity, dir);
        entity.setVelocity(new Vector(vx, climbSpeed, vz));
    }

    // ===================== Helpers =====================

    /**
     * Vanilla acceleration normalization: accel = (0.6^3 / slip^3).
     * On normal blocks (slip=0.6), this is 1.0.
     * On ice (slip=0.98), this is much less (slower acceleration but higher terminal speed).
     */
    private double accelerationFactor(double slipperiness) {
        double defaultSlip = config.getDefaultSlipperiness();
        double def3 = defaultSlip * defaultSlip * defaultSlip;
        double slip3 = slipperiness * slipperiness * slipperiness;
        return def3 / slip3;
    }

    private double getSlipperiness(Location loc) {
        Material below = loc.clone().add(0, -1, 0).getBlock().getType();
        return BlockClassifier.slipperiness(below);
    }

    private boolean shouldJump(Location current, AnnotatedWaypoint wp, double[] dir) {
        // Jump if waypoint is above us
        if (wp.y() > current.getY() + 0.5) return true;

        // Jump if there's a solid block ahead at feet level
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

    private boolean isNearBlockEdge(Location loc, double[] dir) {
        double posInBlockX = loc.getX() - Math.floor(loc.getX());
        double posInBlockZ = loc.getZ() - Math.floor(loc.getZ());

        if (dir[0] > 0.5 && posInBlockX > 0.65) return true;
        if (dir[0] < -0.5 && posInBlockX < 0.35) return true;
        if (dir[1] > 0.5 && posInBlockZ > 0.65) return true;
        if (dir[1] < -0.5 && posInBlockZ < 0.35) return true;
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

        // Green at entity
        current.getWorld().spawnParticle(Particle.DUST, current.clone().add(0, 1, 0),
                1, new Particle.DustOptions(Color.GREEN, 1));
        // Red at target
        target.getWorld().spawnParticle(Particle.DUST, target.clone().add(0, 1, 0),
                1, new Particle.DustOptions(Color.RED, 1));

        if (currentPath == null) return;
        for (int i = pathIndex; i < Math.min(pathIndex + 10, currentPath.size()); i++) {
            AnnotatedWaypoint wp = currentPath.get(i);
            Location wpLoc = new Location(current.getWorld(), wp.x(), wp.y() + 0.5, wp.z());

            // Color by movement type
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
