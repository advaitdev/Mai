package me.advait.mai.pathetic.movement;

import me.advait.mai.body.Body;
import me.advait.mai.pathetic.BlockClassifier;
import me.advait.mai.pathetic.config.MovementConfig;
import me.advait.mai.pathetic.path.AnnotatedWaypoint;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * Stateless tick-level helpers shared by movement type {@code tick()}
 * implementations: key presses toward a waypoint, plus the geometry and
 * block probes the executors decide with.
 *
 * <p>Nothing here writes velocity. Movement types press keys and turn the
 * view through {@link me.advait.mai.body.Body}; vanilla physics does the rest.
 */
public final class MovementExecutors {

    /** Vanilla air acceleration with W held while sprinting (0.026 x key scale). */
    public static final double SPRINT_AIR_ACCEL = Body.airAccel(true);
    /** Vanilla air acceleration with W held while walking (0.02 x key scale). */
    public static final double WALK_AIR_ACCEL = Body.airAccel(false);
    /** Cooldown ticks the runnable holds after a jump. */
    public static final int JUMP_COOLDOWN_TICKS = 4;

    /** Sideways drift below this (blocks/tick) isn't worth steering against. */
    private static final double DRIFT_EPSILON = 0.01;

    private MovementExecutors() {}

    // =========================================================================
    // Key presses
    // =========================================================================

    /**
     * Hold W toward the current waypoint, sprinting if asked. Steers like a
     * player with a mouse: when last tick left sideways momentum (after a
     * turn), the view is angled so this tick's acceleration cancels it and
     * the bot's motion lines up with the waypoint again, instead of
     * drifting wide off a narrow path.
     */
    public static void walk(TickContext ctx, boolean sprint) {
        double[] dir = direction2D(ctx.current, ctx.waypoint);
        if (!ctx.entity().isInWater() && isLevelWalk(ctx.waypoint)) {
            dir = visibleAim(ctx, dir);
            // Stepping back onto the line is a short, careful move.
            if (ctx.detourTick == ctx.totalTicks) sprint = false;
        }
        if (ctx.entity().isInWater()) {
            // Wading or treading water: hold jump unless heading down. In
            // water a jump is a slow rise, and pushing into a bank while
            // rising is what triggers vanilla's 0.3 b/t climb-out boost.
            ctx.body().face(dir[0], dir[1]).keys(1, 0).sprint(false);
            // On a film of water (vanilla jumps from the ground below 0.4
            // deep) that is a full jump, and hopping is how a player gets up
            // a stream: each hop leaves the water's drag and current, about
            // three times faster than wading against them.
            if (ctx.waypoint.y() > ctx.current.getY() - 0.5) ctx.body().jump();
            return;
        }
        Vector v = ctx.velocity();
        // Sideways component of the momentum we carry into this tick.
        double perp = v.getX() * -dir[1] + v.getZ() * dir[0];
        double ax = dir[0], az = dir[1];
        // Sliding along a wall isn't drift: steering against it pins the
        // bot to the wall instead of letting it slide round the corner.
        boolean againstWall = !"-".equals(ctx.body().collision());
        if (ctx.onGround() && !againstWall && Math.abs(perp) > DRIFT_EPSILON) {
            double accel = Body.groundAccel(slipperinessBelow(ctx.current), sprint);
            double cancel = Math.max(-accel, Math.min(accel, -perp));
            double along = Math.sqrt(Math.max(0, accel * accel - cancel * cancel));
            ax = dir[0] * along + -dir[1] * cancel;
            az = dir[1] * along + dir[0] * cancel;
        }
        ctx.body().face(ax, az).keys(1, 0).sprint(sprint);
    }

    /**
     * Heading toward the furthest point of the path segment leading to the
     * waypoint that the hitbox can reach in a straight line. Knocked or
     * drifted off the path (or turned too early), the waypoint can sit
     * behind a wall corner; steering straight at it pins the bot to that
     * wall, while a player steps back onto the line first.
     */
    private static double[] visibleAim(TickContext ctx, double[] dir) {
        AnnotatedWaypoint wp = ctx.waypoint, prev = ctx.previousWaypoint;
        if (prev == null || Math.abs(ctx.current.getY() - wp.y()) > 0.6) return dir;
        if (hitboxLineClear(ctx.current, wp.x(), wp.z(), AIM_CLEARANCE, false)) return dir;
        double sx = wp.x() - prev.x(), sz = wp.z() - prev.z();
        double len = Math.sqrt(sx * sx + sz * sz);
        for (double back = 0.5; back < len; back += 0.5) {
            double px = wp.x() - sx / len * back, pz = wp.z() - sz / len * back;
            if (hitboxLineClear(ctx.current, px, pz, AIM_CLEARANCE, false)) {
                ctx.detourTick = ctx.totalTicks;
                return unit(px - ctx.current.getX(), pz - ctx.current.getZ(), dir);
            }
        }
        // Nothing on the line is in plain view (terrain the check can't
        // read, like a step): head for the waypoint as before.
        return dir;
    }

    private static double[] unit(double dx, double dz, double[] fallback) {
        double len = Math.sqrt(dx * dx + dz * dz);
        return len < 0.05 ? fallback : new double[]{dx / len, dz / len};
    }

    /** Press jump. Vanilla takes off if on the ground; a sprinting bot gets the sprint-jump boost. */
    public static void jump(TickContext ctx) {
        ctx.body().jump();
        ctx.jumpCooldown = JUMP_COOLDOWN_TICKS;
    }

    /** Ticks a vanilla jump keeps the feet at least one block above takeoff (ticks 3-8 of the arc). */
    public static final int STEP_UP_CLEAR_TICK = 8;

    /**
     * Horizontal distance along {@code dir} covered by the first {@code n}
     * ticks of a jump pressed this tick with W held: this tick's ground
     * acceleration plus the sprint-jump boost on takeoff, ground friction
     * once, then air drag and air control.
     */
    public static double jumpReach(TickContext ctx, double[] dir, int n) {
        boolean sprinting = ctx.body().sprinting();
        Vector v = ctx.velocity();
        double along = Math.max(0, v.getX() * dir[0] + v.getZ() * dir[1]);
        double slip = slipperinessBelow(ctx.current);
        double drag = ctx.config.getAirDrag();
        double takeoff = along + Body.groundAccel(slip, sprinting)
                + (sprinting ? ctx.config.getSprintJumpBoost() : 0);
        return takeoff + AirControl.travel(takeoff * slip * drag, Body.airAccel(sprinting), n - 1, drag);
    }

    /**
     * Whether to jump now for a one-block rise at {@code wp}: close enough
     * that the hitbox is over the step before the arc drops back below its
     * top (tick {@link #STEP_UP_CLEAR_TICK}), with a little overlap to land on.
     * Jumping earlier than that misses; jumping later only costs speed (the
     * bot rides up the face), so this fires on the earliest safe tick.
     */
    public static boolean stepUpJumpNow(TickContext ctx, AnnotatedWaypoint wp) {
        double dist = horizontalDistance(ctx.current, wp);
        if (dist <= 0.4) return false;
        if (dist < 0.9) return true;
        return jumpReach(ctx, direction2D(ctx.current, wp), STEP_UP_CLEAR_TICK) >= dist - 0.5;
    }

    /**
     * Half-width of the hitbox plus the clearance kept from walls: taking a
     * shortcut keeps the most (there is drift to come), turning a corner
     * less, steering back onto the path just the hitbox.
     */
    public static final double SKIP_CLEARANCE = 0.4, TURN_CLEARANCE = 0.35, AIM_CLEARANCE = 0.31;

    /** Movements that are plain walking on one level (straight lines between them are free to take). */
    public static boolean isLevelWalk(AnnotatedWaypoint wp) {
        if (wp == null || wp.type() == null) return false;
        String k = wp.type().key();
        return k.equals("walk_flat") || k.equals("walk_diagonal");
    }

    /**
     * Whether the hitbox can walk a straight line from {@code from} to the
     * point (tx, tz) on the level it stands on: floor under the center the
     * whole way at the same height (within a carpet's or path's lip), never a
     * dangerous one, and no block or liquid anywhere the hitbox (plus
     * {@code clearance}, the half-width the hitbox is treated as) passes, feet to head. This is what lets the bot
     * take the straight line a player would instead of the grid's zigzag,
     * and turn only once its hitbox clears the corner.
     */
    public static boolean hitboxLineClear(Location from, double tx, double tz, double clearance) {
        return hitboxLineClear(from, tx, tz, clearance, true);
    }

    /**
     * As above; with {@code level} false the line may also step over
     * partial blocks within the vanilla step height (carpet, slabs, paths),
     * which walking takes in stride: for steering and turning, not for
     * dropping waypoints.
     */
    public static boolean hitboxLineClear(Location from, double tx, double tz, double clearance, boolean level) {
        World world = from.getWorld();
        if (world == null) return false;
        double fx = from.getX(), fz = from.getZ(), y = from.getY();
        double dx = tx - fx, dz = tz - fz;
        double len = Math.sqrt(dx * dx + dz * dz);
        int steps = Math.max(1, (int) Math.ceil(len / 0.2));
        int feet = (int) Math.floor(y + 0.01), head = (int) Math.floor(y + 1.79);
        long lastFloor = Long.MIN_VALUE;
        for (int i = 0; i <= steps; i++) {
            double px = fx + dx * i / steps, pz = fz + dz * i / steps;
            int bx = (int) Math.floor(px), bz = (int) Math.floor(pz);
            long cell = ((long) bx << 32) ^ (bz & 0xffffffffL);
            if (cell != lastFloor) {
                lastFloor = cell;
                if (!levelFloor(world, bx, bz, y, level ? 0.1 : STEP_HEIGHT)) return false;
            }
            // The start is where the hitbox already is (it may touch a wall).
            double r = i == 0 ? 0.299 : clearance;
            for (double ox : new double[]{-r, r}) {
                for (double oz : new double[]{-r, r}) {
                    int cx = (int) Math.floor(px + ox), cz = (int) Math.floor(pz + oz);
                    for (int by = feet; by <= head; by++) {
                        org.bukkit.block.Block b = world.getBlockAt(cx, by, cz);
                        if (b.isLiquid() || !BlockClassifier.isTraversable(b.getType())) return false;
                        if (b.isPassable()) continue;
                        // A lip the hitbox steps onto (carpet, a path's edge) is floor, not wall.
                        double rise = collisionTop(b) - y;
                        if (by == feet && rise <= (level ? 0.1 : STEP_HEIGHT)) continue;
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** Floor under (bx, bz) tops out within {@code tolerance} of {@code y} and is safe to stand on. */
    private static boolean levelFloor(World world, int bx, int bz, double y, double tolerance) {
        for (int by = (int) Math.floor(y + tolerance); by >= (int) Math.floor(y - tolerance - 0.01); by--) {
            org.bukkit.block.Block b = world.getBlockAt(bx, by, bz);
            if (b.isPassable()) continue;
            double top = collisionTop(b);
            return Math.abs(top - y) <= tolerance && !BlockClassifier.isDangerous(b.getType())
                    && !BlockClassifier.isTall(b.getType());
        }
        return false;
    }

    /**
     * Arrival for a level walk: reached, and when the path goes on walking,
     * the hitbox has a clear line to the next waypoint. Turning as soon as
     * the center is within the waypoint radius cut every inside corner and
     * dragged the bot along the wall.
     */
    public static boolean walkArrived(TickContext ctx) {
        if (!reachedFully(ctx)) return false;
        AnnotatedWaypoint next = ctx.nextWaypoint;
        if (!isLevelWalk(next) || Math.abs(next.y() - ctx.waypoint.y()) > 0.01) return true;
        return horizontalDistance(ctx.current, ctx.waypoint) < 0.15
                || hitboxLineClear(ctx.current, next.x(), next.z(), TURN_CLEARANCE, false);
    }

    // =========================================================================
    // Geometry and arrival checks
    // =========================================================================

    public static double[] direction2D(Location from, AnnotatedWaypoint to) {
        double dx = to.x() - from.getX();
        double dz = to.z() - from.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len > 0.001) { dx /= len; dz /= len; }
        return new double[]{dx, dz};
    }

    public static double[] direction3D(Location from, AnnotatedWaypoint to) {
        double dx = to.x() - from.getX();
        double dy = to.y() - from.getY();
        double dz = to.z() - from.getZ();
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 0.001) { dx /= len; dy /= len; dz /= len; }
        return new double[]{dx, dy, dz};
    }

    public static double horizontalDistance(Location from, AnnotatedWaypoint to) {
        double dx = from.getX() - to.x();
        double dz = from.getZ() - to.z();
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static double horizontalSpeed(Vector vel) {
        return Math.sqrt(vel.getX() * vel.getX() + vel.getZ() * vel.getZ());
    }

    /**
     * Whether the entity has horizontally reached the waypoint within the
     * configured radius. Vertical position is ignored.
     */
    public static boolean reachedHorizontally(TickContext ctx) {
        return horizontalDistance(ctx.current, ctx.waypoint) < ctx.config.getWaypointRadius();
    }

    /**
     * Whether the entity has reached the waypoint in both X/Z (within
     * radius) and Y (within ~1 block). The standard arrival check for
     * ground-walk movements.
     */
    public static boolean reachedFully(TickContext ctx) {
        return reachedHorizontally(ctx)
                && Math.abs(ctx.current.getY() - ctx.waypoint.y()) < 1.5;
    }

    // =========================================================================
    // Block lookups
    // =========================================================================

    public static double slipperinessBelow(Location loc) {
        // Vanilla samples the block 0.5 below the feet, so a bot standing on
        // a slab, soul sand or a dirt path reads that block, not the one under it.
        Material below = loc.clone().add(0, -0.5000001, 0).getBlock().getType();
        return BlockClassifier.slipperiness(below);
    }

    /**
     * World Y of the top of a block's collision (not its outline: a fence's
     * outline is 1.0 tall but it collides up to 1.5). A block without
     * collision returns its own base Y.
     */
    public static double collisionTop(org.bukkit.block.Block b) {
        if (b.isPassable()) return b.getY();
        double max = 0;
        for (org.bukkit.util.BoundingBox box : b.getCollisionShape().getBoundingBoxes()) {
            max = Math.max(max, box.getMaxY());
        }
        return b.getY() + max;
    }

    /** Vanilla step height: rises up to this are climbed by walking, no jump. */
    public static final double STEP_HEIGHT = 0.6;

    /**
     * How far the top of the waypoint's supporting block sits above the
     * bot's feet. Uses the real collision box, so a slab, carpet, path or
     * soul sand reads as a short rise the bot simply walks up.
     */
    public static double stepRise(Location current, AnnotatedWaypoint wp) {
        World world = current.getWorld();
        if (world == null) return wp.y() - current.getY();
        org.bukkit.block.Block support = world.getBlockAt(
                (int) Math.floor(wp.x()), (int) Math.floor(wp.y()) - 1, (int) Math.floor(wp.z()));
        return collisionTop(support) - current.getY();
    }

    /** Whether reaching {@code wp} needs a jump rather than a walking step. */
    public static boolean needsJumpUp(Location current, AnnotatedWaypoint wp) {
        return wp.y() > current.getY() + 0.3 && stepRise(current, wp) > STEP_HEIGHT;
    }

    /**
     * Opens any closed wooden door or fence gate the bot is about to walk
     * into (feet or head height, within reach ahead). The planner treats
     * these as passable, so the executor has to make that true.
     */
    public static void openDoorsAhead(LivingEntity entity, Location current, double[] dir) {
        World world = current.getWorld();
        if (world == null || (dir[0] == 0 && dir[1] == 0)) return;
        for (double probe : new double[]{0.4, 0.9}) {
            for (double yOff : new double[]{0.1, 1.1}) {
                org.bukkit.block.Block b = world.getBlockAt(
                        (int) Math.floor(current.getX() + dir[0] * probe),
                        (int) Math.floor(current.getY() + yOff),
                        (int) Math.floor(current.getZ() + dir[1] * probe));
                if (!BlockClassifier.isOpenable(b.getType())) continue;
                if (b.getBlockData() instanceof org.bukkit.block.data.Openable o && !o.isOpen()) {
                    o.setOpen(true);
                    b.setBlockData(o);
                    entity.swingMainHand();
                    world.playSound(b.getLocation(), b.getType().name().endsWith("_FENCE_GATE")
                            ? org.bukkit.Sound.BLOCK_FENCE_GATE_OPEN
                            : org.bukkit.Sound.BLOCK_WOODEN_DOOR_OPEN, 1f, 1f);
                }
            }
        }
    }

    /**
     * Whether a sprint-jump along a flat run is worth it and safe right now:
     * on the ground, already at sprint speed, the waypoint far enough away
     * that the bot lands before it, and every point of the jump's runway has
     * a full-height floor level with the feet (no slow blocks) plus three
     * blocks of headroom for the jump arc. Sprint-jumping on the flat is
     * how players outrun plain sprinting (about 7 b/s against 5.6).
     */
    public static boolean flatRunJumpClear(TickContext ctx, double[] dir) {
        if (!ctx.config.isSprintJumpFlat() || !ctx.onGround() || ctx.jumpCooldown > 0) return false;
        // Velocity read at tick start is post-friction: full sprint reads ~0.156.
        // Along the jump: right after a turn the speed is still sideways,
        // and jumping then throws the bot into the corner it just rounded.
        Vector v = ctx.velocity();
        if (v.getX() * dir[0] + v.getZ() * dir[1] < 0.12) return false;
        double dist = horizontalDistance(ctx.current, ctx.waypoint);
        if (dist < ctx.config.getSprintJumpFlatMinDistance()) return false;
        if (Math.abs(ctx.waypoint.y() - ctx.current.getY()) > 0.5) return false;
        World world = ctx.current.getWorld();
        if (world == null) return false;
        double y = ctx.current.getY();
        int feetY = (int) Math.floor(y + 0.01);
        double runway = Math.min(dist, 4.5);
        // The whole hitbox has to clear the runway: a jump can't correct a
        // drift into a pillar it brushes past.
        if (!hitboxLineClear(ctx.current, ctx.current.getX() + dir[0] * runway,
                ctx.current.getZ() + dir[1] * runway, SKIP_CLEARANCE)) return false;
        for (double d = 0.0; d <= runway; d += 0.5) {
            int bx = (int) Math.floor(ctx.current.getX() + dir[0] * d);
            int bz = (int) Math.floor(ctx.current.getZ() + dir[1] * d);
            org.bukkit.block.Block floor = world.getBlockAt(bx, feetY - 1, bz);
            Material fm = floor.getType();
            if (!BlockClassifier.isSolid(fm) || BlockClassifier.isTall(fm)
                    || BlockClassifier.speedMultiplier(fm) < 1.0
                    || Math.abs(collisionTop(floor) - y) > 0.01) return false;
            for (int h = 0; h <= 2; h++) {
                if (!world.getBlockAt(bx, feetY + h, bz).isPassable()) return false;
            }
        }
        return true;
    }

    // Player half-width is 0.3; subtract an epsilon so the probe samples
    // the block an overlapping corner would actually land in.
    private static final double HALF_WIDTH = 0.29;

    /** The 4 XZ corners of the player bounding box relative to its center. */
    private static final double[][] CORNER_OFFSETS = {
            {-HALF_WIDTH, -HALF_WIDTH}, {-HALF_WIDTH, HALF_WIDTH},
            { HALF_WIDTH, -HALF_WIDTH}, { HALF_WIDTH,  HALF_WIDTH}
    };

    /**
     * True when any of the 4 bounding-box corners, projected {@code probeDist}
     * forward in the given direction, is inside a solid block at the given
     * y-offset from the entity's feet. This catches partial-overlap cases
     * that a single centerline ray misses — the reason bots clip on block
     * edges when a path turns through a block corner.
     */
    public static boolean solidNearFacing(Location current, double[] dir,
                                          double yOffset, double probeDist) {
        World world = current.getWorld();
        if (world == null) return false;
        for (double[] corner : CORNER_OFFSETS) {
            double px = current.getX() + corner[0] + dir[0] * probeDist;
            double py = current.getY() + yOffset;
            double pz = current.getZ() + corner[1] + dir[1] * probeDist;
            Material mat = world.getBlockAt(
                    (int) Math.floor(px),
                    (int) Math.floor(py),
                    (int) Math.floor(pz)).getType();
            if (BlockClassifier.isSolid(mat)) return true;
        }
        return false;
    }

    /**
     * A 1-block-tall obstacle is directly ahead: solid at feet height,
     * clear one block up (so a jump will land on top), clear two blocks
     * up (so the jump apex doesn't collide). Used by ground-walk types
     * as an emergency auto-jump fallback when the planned waypoint didn't
     * anticipate the obstacle (placed block, corner-clip, etc.).
     */
    public static boolean obstacleAheadNeedsJump(Location current, double[] dir) {
        if (!solidNearFacing(current, dir, 0.1, 0.5)
                || solidNearFacing(current, dir, 1.1, 0.5)
                || solidNearFacing(current, dir, 2.1, 0.5)) {
            return false;
        }
        // Only a rise between the step height and a jump's reach is worth a
        // hop: slabs, carpets, paths and soul sand are walked up, and a
        // fence or wall (1.5) can't be jumped onto at all.
        double rise = obstacleTopAhead(current, dir, 0.5) - current.getY();
        return rise > STEP_HEIGHT && rise <= 1.25;
    }

    /** Highest collision top among the blocks the bounding-box corners would enter at feet level. */
    private static double obstacleTopAhead(Location current, double[] dir, double probeDist) {
        World world = current.getWorld();
        double top = current.getY();
        if (world == null) return top;
        for (double[] corner : CORNER_OFFSETS) {
            org.bukkit.block.Block b = world.getBlockAt(
                    (int) Math.floor(current.getX() + corner[0] + dir[0] * probeDist),
                    (int) Math.floor(current.getY() + 0.1),
                    (int) Math.floor(current.getZ() + corner[1] + dir[1] * probeDist));
            top = Math.max(top, collisionTop(b));
        }
        return top;
    }

    /**
     * The bot is at the edge of its current block in the given direction:
     * on solid ground now, but a step past the leading edge would put it
     * over empty space. Used by sprint-jump to time the takeoff for
     * maximum horizontal range — jumping earlier wastes the block of
     * runway that was supposed to bring the bot to terminal sprint.
     */
    /**
     * A sprint-jump takes off on the last tick the hitbox still overlaps the
     * takeoff block: when this tick's move would carry the center more than
     * this far past the lip (half-width 0.3 less a 0.05 margin). That's the
     * edge-of-the-block timing players use; jumping earlier wastes runway.
     * The planner's run-up check uses the same trigger.
     */
    public static final double TAKEOFF_EDGE_LEAD = 0.25;

    /** Distance ahead of the center along {@code dir} to the first point with no floor (up to 4 blocks). */
    public static double edgeDistance(Location current, double[] dir) {
        for (double d = 0.0; d <= 4.0; d += 0.025) {
            if (atEdgeAhead(current, dir, d)) return d;
        }
        return Double.MAX_VALUE;
    }

    public static boolean atEdgeAhead(Location current, double[] dir) {
        return atEdgeAhead(current, dir, 0.4);
    }

    /**
     * Velocity-aware edge probe: {@code probeDist} should be at least the
     * leading-edge half-width (0.3) plus the distance the bot will travel
     * next tick, so a sprinting bot sees the edge on the last tick it is
     * still standing on the block — not one tick after it has stepped off.
     */
    public static boolean atEdgeAhead(Location current, double[] dir, double probeDist) {
        World world = current.getWorld();
        if (world == null) return false;
        double px = current.getX() + dir[0] * probeDist;
        double pz = current.getZ() + dir[1] * probeDist;
        Material belowNext = world.getBlockAt(
                (int) Math.floor(px),
                (int) Math.floor(current.getY() - 0.1),
                (int) Math.floor(pz)).getType();
        return !BlockClassifier.isSolid(belowNext);
    }

    // =========================================================================
    // Rotation
    // =========================================================================

    public static void face(LivingEntity entity, double[] dir2D) {
        Body.of(entity).face(dir2D[0], dir2D[1]);
    }
}
