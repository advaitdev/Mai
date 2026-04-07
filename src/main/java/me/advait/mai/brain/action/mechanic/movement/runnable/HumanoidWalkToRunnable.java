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
 * The Mannequin has gravity=true and AI=false. The server applies gravity,
 * vertical drag (0.98), and horizontal friction (block slip * 0.91) each tick
 * AFTER our setVelocity() call. We only add acceleration impulses — no drag.
 *
 * <h3>Key design decisions</h3>
 * <ul>
 *   <li>Step-ups: jump preemptively when ~1.5 blocks away, not on block impact</li>
 *   <li>Sprint-jumps: jump based on distance to gap, not block-edge position</li>
 *   <li>Jump cooldown: 4 ticks (matching vanilla/TerminatorPlus)</li>
 *   <li>Arrival: must reach exact target block, not just "near" it</li>
 * </ul>
 */
public class HumanoidWalkToRunnable extends BukkitRunnable {

    private static final double SPRINT_AIR_ACCEL = 0.026;
    private static final int JUMP_COOLDOWN_TICKS = 4;
    private static final double ARRIVAL_DISTANCE_SQ = 1.0; // 1 block

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

    private int ticksSincePathUpdate;
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
        this.ticksSincePathUpdate = config.getPathUpdateInterval();
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

        // Exact arrival check: same block position
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

        // Path management
        ticksSincePathUpdate++;
        if ((currentPath == null || pathIndex >= currentPath.size()
                || ticksSincePathUpdate >= config.getPathUpdateInterval())
                && !pathfindingInProgress.get()) {
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

        // Parkour in-air: don't advance until landed
        if (parkourPhase == ParkourPhase.IN_AIR) {
            executeSprintJumpAir(entity, current, waypoint);
            return;
        }

        // Waypoint reached check
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
            case WALK, SPRINT -> executeSprint(entity, current, waypoint);
            case SPRINT_JUMP -> executeSprintJump(entity, current, waypoint, hint);
            case SNEAK -> executeSneak(entity, current, waypoint);
            case SWIM -> executeSwim(entity, current, waypoint);
            case CLIMB -> executeClimb(entity, current, waypoint);
        }
    }

    // ===================== Sprint with preemptive jump =====================

    /**
     * Sprint toward waypoint. If the waypoint is above us (step-up), jump
     * preemptively when within ~1.5 blocks rather than waiting to hit the block face.
     */
    private void executeSprint(LivingEntity entity, Location current, AnnotatedWaypoint wp) {
        Vector vel = entity.getVelocity();
        double[] dir = directionTo(current, wp);
        boolean onGround = entity.isOnGround();

        double vx, vz;
        if (onGround) {
            double[] groundAccel = computeGroundAccel(current, config.getSprintFactor());
            vx = vel.getX() + dir[0] * groundAccel[0];
            vz = vel.getZ() + dir[1] * groundAccel[0];
        } else {
            vx = vel.getX() + dir[0] * SPRINT_AIR_ACCEL;
            vz = vel.getZ() + dir[1] * SPRINT_AIR_ACCEL;
        }

        double vy = vel.getY();
        if (onGround && jumpCooldown == 0) {
            if (shouldJumpForWaypoint(current, wp, dir)) {
                vy = config.getJumpVelocity();
                jumpCooldown = JUMP_COOLDOWN_TICKS;
                // Add sprint-jump boost for step-ups to clear the block
                if (wp.y() > current.getY() + 0.5) {
                    float yawRad = (float) Math.toRadians(entity.getLocation().getYaw());
                    vx += -Math.sin(yawRad) * config.getSprintJumpBoost();
                    vz += Math.cos(yawRad) * config.getSprintJumpBoost();
                }
            }
        }

        faceDirection(entity, dir);
        entity.setVelocity(new Vector(vx, vy, vz));
    }

    /**
     * Determines if the bot should jump NOW for the current waypoint.
     * Preemptive: jumps when close to a step-up, not when hitting the block face.
     */
    private boolean shouldJumpForWaypoint(Location current, AnnotatedWaypoint wp, double[] dir) {
        double horizDist = horizontalDistance(current, wp);

        // If waypoint is above us, jump preemptively when within 1.5 blocks
        if (wp.y() > current.getY() + 0.3 && horizDist < 1.8) {
            return true;
        }

        // If there's a solid block directly ahead, jump to clear it
        if (current.getWorld() != null) {
            Location ahead = current.clone().add(dir[0] * 0.5, 0, dir[1] * 0.5);
            Material blockAhead = ahead.getBlock().getType();
            if (BlockClassifier.isSolid(blockAhead)) {
                Material aboveAhead = ahead.clone().add(0, 1, 0).getBlock().getType();
                if (!BlockClassifier.isSolid(aboveAhead)) {
                    return true;
                }
            }
        }

        return false;
    }

    // ===================== Sprint-Jump (Parkour) =====================

    /**
     * Sprint-jump across a gap. Approach phase: sprint toward the gap and
     * jump when within range. Distance-based trigger instead of edge position.
     */
    private void executeSprintJump(LivingEntity entity, Location current,
                                   AnnotatedWaypoint wp, ExecutionHint hint) {
        Vector vel = entity.getVelocity();
        double[] dir = directionTo(current, wp);
        boolean onGround = entity.isOnGround();

        if (!onGround) {
            // Airborne unexpectedly — air control toward target
            double vx = vel.getX() + dir[0] * SPRINT_AIR_ACCEL;
            double vz = vel.getZ() + dir[1] * SPRINT_AIR_ACCEL;
            entity.setVelocity(new Vector(vx, vel.getY(), vz));
            faceDirection(entity, dir);
            return;
        }

        // Sprint on ground, building momentum
        double[] groundAccel = computeGroundAccel(current, config.getSprintFactor());
        double vx = vel.getX() + dir[0] * groundAccel[0];
        double vz = vel.getZ() + dir[1] * groundAccel[0];

        // Jump trigger: distance-based. For a gap of N blocks, the landing
        // waypoint is N+1 blocks away. Jump when we're within ~2 blocks of
        // the gap start (which is ~gap+1 blocks from the landing waypoint).
        // Simpler: jump when distance to landing is within jump range + margin.
        double distToLanding = horizontalDistance(current, wp);
        int gap = hint.gapLength();
        double jumpRange = gap + 2.0; // gap + 1 block before + 1 block margin

        if (jumpCooldown == 0 && distToLanding < jumpRange && distToLanding > 0.5) {
            // JUMP with sprint-jump boost
            double vy = config.getJumpVelocity();
            float yawRad = (float) Math.toRadians(entity.getLocation().getYaw());
            vx += -Math.sin(yawRad) * config.getSprintJumpBoost();
            vz += Math.cos(yawRad) * config.getSprintJumpBoost();
            entity.setVelocity(new Vector(vx, vy, vz));
            parkourPhase = ParkourPhase.IN_AIR;
            jumpCooldown = JUMP_COOLDOWN_TICKS;
        } else {
            entity.setVelocity(new Vector(vx, vel.getY(), vz));
            parkourPhase = ParkourPhase.APPROACHING;
        }
        faceDirection(entity, dir);
    }

    /** Air phase of sprint-jump: air control toward landing, wait for touchdown. */
    private void executeSprintJumpAir(LivingEntity entity, Location current,
                                      AnnotatedWaypoint wp) {
        Vector vel = entity.getVelocity();
        double[] dir = directionTo(current, wp);

        double vx = vel.getX() + dir[0] * SPRINT_AIR_ACCEL;
        double vz = vel.getZ() + dir[1] * SPRINT_AIR_ACCEL;
        entity.setVelocity(new Vector(vx, vel.getY(), vz));
        faceDirection(entity, dir);

        // Landed when on ground and descending (past apex)
        if (entity.isOnGround() && vel.getY() <= 0) {
            parkourPhase = ParkourPhase.NONE;
            pathIndex++;
        }
    }

    // ===================== Other Locomotion =====================

    private void executeSneak(LivingEntity entity, Location current, AnnotatedWaypoint wp) {
        Vector vel = entity.getVelocity();
        double[] dir = directionTo(current, wp);

        if (entity.isOnGround()) {
            double[] accel = computeGroundAccel(current, config.getSneakFactor());
            double vx = vel.getX() + dir[0] * accel[0];
            double vz = vel.getZ() + dir[1] * accel[0];
            entity.setVelocity(new Vector(vx, vel.getY(), vz));
        } else {
            double vx = vel.getX() + dir[0] * config.getAirAcceleration();
            double vz = vel.getZ() + dir[1] * config.getAirAcceleration();
            entity.setVelocity(new Vector(vx, vel.getY(), vz));
        }
        faceDirection(entity, dir);
    }

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

    // ===================== Physics Helpers =====================

    /**
     * Computes ground acceleration using vanilla normalization.
     * accel = moveSpeed * speedFactor * (0.16277136 / (slip * 0.91)^3)
     * Returns [accel].
     */
    private double[] computeGroundAccel(Location loc, double speedFactor) {
        double slip = getSlipperiness(loc);
        double inertia = slip * 0.91;
        double accel = config.getWalkAcceleration() * speedFactor
                * (0.16277136 / (inertia * inertia * inertia));
        return new double[]{accel};
    }

    private double getSlipperiness(Location loc) {
        Material below = loc.clone().add(0, -1, 0).getBlock().getType();
        return BlockClassifier.slipperiness(below);
    }

    // ===================== Geometry =====================

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
