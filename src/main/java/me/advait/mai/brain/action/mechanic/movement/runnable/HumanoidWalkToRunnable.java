package me.advait.mai.brain.action.mechanic.movement.runnable;

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
 * Tick-by-tick movement executor with context-aware speed control.
 *
 * <h3>Key design principles</h3>
 * <ul>
 *   <li>Path recalculates every tick (when previous request completes)</li>
 *   <li>New paths always start from index 1 (skip start node) — no closest-waypoint
 *       search that causes back-and-forth oscillation</li>
 *   <li>Speed adapts to context: sprint on open ground, walk near obstacles,
 *       build momentum before parkour gaps, slow down near destination</li>
 *   <li>Server handles friction/gravity; we only add impulses</li>
 * </ul>
 */
public class HumanoidWalkToRunnable extends BukkitRunnable {

    private static final double SPRINT_AIR_ACCEL = 0.026;
    private static final double WALK_AIR_ACCEL = 0.02;
    private static final int JUMP_COOLDOWN_TICKS = 4;

    private static boolean debugMode = false;

    private final Humanoid humanoid;
    private final Location target;
    private final CompletableFuture<HumanoidActionResult> resultFuture;
    private final MovementConfig config;

    private AnnotatedPath currentPath;
    private int pathIndex = 0;
    private boolean hasEverHadPath = false;

    private int timeStuck = 0;
    private Location previousLocation = null;

    private final AtomicBoolean pathfindingInProgress = new AtomicBoolean(false);

    private int jumpCooldown = 0;

    private enum ParkourPhase { NONE, APPROACHING, IN_AIR }
    private ParkourPhase parkourPhase = ParkourPhase.NONE;

    public HumanoidWalkToRunnable(Humanoid humanoid, Location target,
                                  CompletableFuture<HumanoidActionResult> resultFuture) {
        this.humanoid = humanoid;
        this.target = target;
        this.resultFuture = resultFuture;
        this.config = PatheticAgent.getInstance().getConfig();
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

        // Exact block arrival
        if (isAtTarget(current)) {
            complete(true, "Arrived at destination.");
            return;
        }

        // Stuck detection
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

        if (jumpCooldown > 0) jumpCooldown--;

        // Request a new path every tick (gated by pathfindingInProgress)
        if (!pathfindingInProgress.get()) {
            requestNewPath(current);
        }

        if (currentPath != null && pathIndex < currentPath.size()) {
            moveToWaypoint(entity, current);
        }

        if (debugMode) showDebugParticles(current);
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

    private void requestNewPath(Location current) {
        pathfindingInProgress.set(true);

        PatheticAgent.getInstance().getAnnotatedPath(current, target)
                .thenAccept(pathOpt -> {
                    pathfindingInProgress.set(false);
                    if (resultFuture.isDone()) return;

                    if (pathOpt.isPresent()) {
                        AnnotatedPath newPath = pathOpt.get();
                        // Always start from index 1 (skip the start node which is our
                        // current pos). This avoids the back-and-forth oscillation that
                        // happens when findClosestWaypoint picks a node behind us.
                        currentPath = newPath;
                        pathIndex = Math.min(1, newPath.size() - 1);
                        hasEverHadPath = true;
                        // Don't reset parkour phase — if we're mid-jump, keep going
                        if (parkourPhase != ParkourPhase.IN_AIR) {
                            parkourPhase = ParkourPhase.NONE;
                        }
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

    // ===================== Movement Dispatch =====================

    private void moveToWaypoint(LivingEntity entity, Location current) {
        AnnotatedWaypoint waypoint = currentPath.get(pathIndex);
        ExecutionHint hint = waypoint.hint();

        // Parkour in-air: don't advance until landed
        if (parkourPhase == ParkourPhase.IN_AIR) {
            executeAirControl(entity, current, waypoint);
            return;
        }

        // Waypoint reached check
        if (horizontalDistance(current, waypoint) < config.getWaypointRadius()
                && Math.abs(current.getY() - waypoint.y()) < 1.5) {
            pathIndex++;
            parkourPhase = ParkourPhase.NONE;
            if (pathIndex >= currentPath.size()) return;
            waypoint = currentPath.get(pathIndex);
            hint = waypoint.hint();
        }

        if (hint == null) {
            executeContextual(entity, current, waypoint);
            return;
        }

        switch (hint.locomotion()) {
            case WALK, SPRINT -> executeContextual(entity, current, waypoint);
            case SPRINT_JUMP -> executeSprintJump(entity, current, waypoint, hint);
            case SNEAK -> executeWithSpeed(entity, current, waypoint, config.getSneakFactor(), false);
            case SWIM -> executeSwim(entity, current, waypoint);
            case CLIMB -> executeClimb(entity, current, waypoint);
        }
    }

    // ===================== Context-Aware Movement =====================

    /**
     * Moves toward the waypoint at a speed determined by context:
     * <ul>
     *   <li>Sprint on open flat ground</li>
     *   <li>Walk when approaching a step-up, turn, or destination</li>
     *   <li>Sprint-jump boost on step-ups to clear cleanly</li>
     * </ul>
     */
    private void executeContextual(LivingEntity entity, Location current, AnnotatedWaypoint wp) {
        double horizDist = horizontalDistance(current, wp);
        double distToTarget = Math.sqrt(current.distanceSquared(target));
        boolean needsJump = wp.y() > current.getY() + 0.3;
        boolean nearDestination = distToTarget < 3.0;

        // Look ahead: is the NEXT waypoint a direction change or special movement?
        boolean upcomingTurn = isUpcomingTurn(wp);

        // Decide speed factor
        double speedFactor;
        if (nearDestination) {
            // Approaching destination: walk to avoid overshooting
            speedFactor = 1.0;
        } else if (needsJump && horizDist < 2.5) {
            // Close to a step-up: walk speed for precise jump timing
            speedFactor = 1.0;
        } else if (upcomingTurn) {
            // Direction change ahead: slow down
            speedFactor = 1.0;
        } else {
            // Open ground: sprint
            speedFactor = config.getSprintFactor();
        }

        boolean sprinting = speedFactor > 1.0;
        executeWithSpeed(entity, current, wp, speedFactor, sprinting);
    }

    /**
     * Core movement tick: applies acceleration at the given speed factor,
     * and handles jump logic for step-ups.
     */
    private void executeWithSpeed(LivingEntity entity, Location current,
                                  AnnotatedWaypoint wp, double speedFactor, boolean sprinting) {
        Vector vel = entity.getVelocity();
        double[] dir = directionTo(current, wp);
        boolean onGround = entity.isOnGround();

        double airAccel = sprinting ? SPRINT_AIR_ACCEL : WALK_AIR_ACCEL;

        double vx, vz;
        if (onGround) {
            double slip = getSlipperiness(current);
            double inertia = slip * 0.91;
            double accel = config.getWalkAcceleration() * speedFactor
                    * (0.16277136 / (inertia * inertia * inertia));
            vx = vel.getX() + dir[0] * accel;
            vz = vel.getZ() + dir[1] * accel;
        } else {
            vx = vel.getX() + dir[0] * airAccel;
            vz = vel.getZ() + dir[1] * airAccel;
        }

        double vy = vel.getY();
        if (onGround && jumpCooldown == 0 && shouldJumpForWaypoint(current, wp, dir)) {
            vy = config.getJumpVelocity();
            jumpCooldown = JUMP_COOLDOWN_TICKS;
            // Sprint-jump boost on step-ups to clear the block with momentum
            if (sprinting && wp.y() > current.getY() + 0.3) {
                float yawRad = (float) Math.toRadians(entity.getLocation().getYaw());
                vx += -Math.sin(yawRad) * config.getSprintJumpBoost();
                vz += Math.cos(yawRad) * config.getSprintJumpBoost();
            }
        }

        faceDirection(entity, dir);
        entity.setVelocity(new Vector(vx, vy, vz));
    }

    /** Should we jump now to reach this waypoint? */
    private boolean shouldJumpForWaypoint(Location current, AnnotatedWaypoint wp, double[] dir) {
        double horizDist = horizontalDistance(current, wp);

        // Waypoint is above: jump preemptively when within range
        if (wp.y() > current.getY() + 0.3 && horizDist < 1.8) return true;

        // Solid block directly ahead with open space above: step up
        if (current.getWorld() != null) {
            Location ahead = current.clone().add(dir[0] * 0.5, 0, dir[1] * 0.5);
            Material blockAhead = ahead.getBlock().getType();
            if (BlockClassifier.isSolid(blockAhead)) {
                Material above = ahead.clone().add(0, 1, 0).getBlock().getType();
                if (!BlockClassifier.isSolid(above)) return true;
            }
        }

        return false;
    }

    /** Checks if the next 2 waypoints involve a significant direction change. */
    private boolean isUpcomingTurn(AnnotatedWaypoint current) {
        if (pathIndex + 1 >= currentPath.size()) return false;
        AnnotatedWaypoint next = currentPath.get(pathIndex + 1);

        // Check if direction from current wp to next wp differs significantly
        // from our current heading
        if (pathIndex > 0) {
            AnnotatedWaypoint prev = currentPath.get(pathIndex - 1);
            double dx1 = current.x() - prev.x(), dz1 = current.z() - prev.z();
            double dx2 = next.x() - current.x(), dz2 = next.z() - current.z();
            double len1 = Math.sqrt(dx1 * dx1 + dz1 * dz1);
            double len2 = Math.sqrt(dx2 * dx2 + dz2 * dz2);
            if (len1 > 0.1 && len2 > 0.1) {
                double dot = (dx1 * dx2 + dz1 * dz2) / (len1 * len2);
                // dot < 0.5 means > 60 degree turn
                return dot < 0.5;
            }
        }
        return false;
    }

    // ===================== Sprint-Jump (Parkour) =====================

    /**
     * Sprint-jump with momentum building. The bot sprints until it has
     * enough speed, then jumps. For larger gaps it needs more runway.
     */
    private void executeSprintJump(LivingEntity entity, Location current,
                                   AnnotatedWaypoint wp, ExecutionHint hint) {
        Vector vel = entity.getVelocity();
        double[] dir = directionTo(current, wp);
        boolean onGround = entity.isOnGround();

        if (!onGround) {
            // Already airborne — switch to air control
            parkourPhase = ParkourPhase.IN_AIR;
            executeAirControl(entity, current, wp);
            return;
        }

        // Sprint on ground, building momentum
        double slip = getSlipperiness(current);
        double inertia = slip * 0.91;
        double accel = config.getWalkAcceleration() * config.getSprintFactor()
                * (0.16277136 / (inertia * inertia * inertia));
        double vx = vel.getX() + dir[0] * accel;
        double vz = vel.getZ() + dir[1] * accel;

        // Current horizontal speed
        double currentSpeed = Math.sqrt(vel.getX() * vel.getX() + vel.getZ() * vel.getZ());
        double distToLanding = horizontalDistance(current, wp);
        int gap = hint.gapLength();

        // Minimum speed required to clear the gap. Wider gaps need more momentum.
        // Sprint terminal velocity is ~0.28 b/t. Require at least a fraction of that.
        double minSpeedForGap = switch (gap) {
            case 0, 1 -> 0.10; // 1-block gap: easy, low speed ok
            case 2 -> 0.15;    // 2-block gap: need some speed
            case 3 -> 0.20;    // 3-block gap: need good sprint speed
            default -> 0.25;   // 4-block gap: need near-max sprint
        };

        // Jump conditions: close enough, fast enough, cooldown ready
        double jumpRange = gap + 2.5;
        boolean fastEnough = currentSpeed >= minSpeedForGap;
        boolean inRange = distToLanding < jumpRange && distToLanding > 0.5;

        if (jumpCooldown == 0 && inRange && fastEnough) {
            double vy = config.getJumpVelocity();
            float yawRad = (float) Math.toRadians(entity.getLocation().getYaw());
            vx += -Math.sin(yawRad) * config.getSprintJumpBoost();
            vz += Math.cos(yawRad) * config.getSprintJumpBoost();
            entity.setVelocity(new Vector(vx, vy, vz));
            parkourPhase = ParkourPhase.IN_AIR;
            jumpCooldown = JUMP_COOLDOWN_TICKS;
        } else {
            // Keep sprinting, building momentum
            entity.setVelocity(new Vector(vx, vel.getY(), vz));
            parkourPhase = ParkourPhase.APPROACHING;
        }
        faceDirection(entity, dir);
    }

    /** Air phase: steer toward landing, wait for touchdown. */
    private void executeAirControl(LivingEntity entity, Location current,
                                   AnnotatedWaypoint wp) {
        Vector vel = entity.getVelocity();
        double[] dir = directionTo(current, wp);

        double vx = vel.getX() + dir[0] * SPRINT_AIR_ACCEL;
        double vz = vel.getZ() + dir[1] * SPRINT_AIR_ACCEL;
        entity.setVelocity(new Vector(vx, vel.getY(), vz));
        faceDirection(entity, dir);

        if (entity.isOnGround() && vel.getY() <= 0) {
            parkourPhase = ParkourPhase.NONE;
            pathIndex++;
        }
    }

    // ===================== Other Locomotion =====================

    private void executeSwim(LivingEntity entity, Location current, AnnotatedWaypoint wp) {
        double[] dir3d = directionTo3D(current, wp);
        Vector vel = entity.getVelocity();
        double swimAccel = 0.04;
        entity.setVelocity(new Vector(
                vel.getX() + dir3d[0] * swimAccel,
                vel.getY() + dir3d[1] * swimAccel,
                vel.getZ() + dir3d[2] * swimAccel));
        faceDirection(entity, new double[]{dir3d[0], dir3d[2]});
    }

    private void executeClimb(LivingEntity entity, Location current, AnnotatedWaypoint wp) {
        double dy = wp.y() - current.getY();
        double[] dir = directionTo(current, wp);
        double climbSpeed = dy >= 0 ? 0.12 : -0.15;
        Vector vel = entity.getVelocity();
        entity.setVelocity(new Vector(
                vel.getX() + dir[0] * 0.02,
                climbSpeed,
                vel.getZ() + dir[1] * 0.02));
        faceDirection(entity, dir);
    }

    // ===================== Helpers =====================

    private double getSlipperiness(Location loc) {
        Material below = loc.clone().add(0, -1, 0).getBlock().getType();
        return BlockClassifier.slipperiness(below);
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
