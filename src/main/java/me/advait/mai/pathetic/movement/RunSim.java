package me.advait.mai.pathetic.movement;

import me.advait.mai.body.Body;
import me.advait.mai.pathetic.BlockClassifier;
import me.advait.mai.pathetic.config.MovementConfig;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.util.BoundingBox;

import java.util.Arrays;
import java.util.Collection;

/**
 * One tick of a player's land movement, the way the server computes it:
 * the sprint rules, the jump and its sprint boost, key acceleration, the
 * move against the real block collision shapes (vertical first, then the
 * larger horizontal axis, with the 0.6 step-up), landing, fall distance,
 * then gravity and drag. Copied from vanilla ({@code LivingEntity.aiStep},
 * {@code travelInAir}, {@code Entity.move} and {@code collide}), so a
 * controller can try keys out here before pressing them.
 *
 * <p>It models walking, running, jumping and falling on ordinary blocks
 * only. Anything else (liquids, ladders and vines, cobwebs, powder snow,
 * hazards to touch or stand on, soul sand, honey, slime) marks the state
 * {@link State#dead}: the caller must not go there on this model's word.
 */
public final class RunSim {

    public static final double HALF = 0.3, HEIGHT = 1.8, STEP = 0.6;
    private static final double EPS = 1.0E-7;
    /** {@link State#why} of a body that went into water (where a route that goes on as a swim wants it). */
    public static final String WATER = "water";
    /** {@code LivingEntity.aiStep} scales the client's (already x0.98) input by 0.98 again. */
    private static final double INPUT = Body.KEY_SCALE * 0.98F;

    private RunSim() {}

    /** A block's collision boxes (world coordinates, six numbers each) and what the model needs to know about it. */
    private record Cell(double[] boxes, boolean avoid, boolean water, boolean badFloor, float friction) {}

    private static final Cell EMPTY = new Cell(new double[0], false, false, false, 0.6F);

    /** The blocks around the bot, read on demand and remembered for one plan. */
    public static final class Terrain {
        private static final int SX = 40, SY = 32, SZ = 40;
        private final World world;
        private final int x0, y0, z0;
        private final Cell[] cells = new Cell[SX * SY * SZ];

        public Terrain(World world, double x, double y, double z) {
            this.world = world;
            this.x0 = (int) Math.floor(x) - SX / 2;
            this.y0 = (int) Math.floor(y) - SY / 2;
            this.z0 = (int) Math.floor(z) - SZ / 2;
        }

        Cell cell(int x, int y, int z) {
            int ix = x - x0, iy = y - y0, iz = z - z0;
            if (ix < 0 || iy < 0 || iz < 0 || ix >= SX || iy >= SY || iz >= SZ) return read(x, y, z);
            int i = (ix * SY + iy) * SZ + iz;
            Cell c = cells[i];
            if (c == null) cells[i] = c = read(x, y, z);
            return c;
        }

        private Cell read(int x, int y, int z) {
            if (y < world.getMinHeight() || y >= world.getMaxHeight()) return EMPTY;
            Block b = world.getBlockAt(x, y, z);
            Material m = b.getType();
            if (m.isAir()) return EMPTY;
            BlockData data = b.getBlockData();
            boolean avoid = b.isLiquid() || (data instanceof Waterlogged w && w.isWaterlogged())
                    || BlockClassifier.isDangerous(m) || BlockClassifier.isClimbable(m)
                    || m == Material.COBWEB || m == Material.POWDER_SNOW || m == Material.SCAFFOLDING
                    || m == Material.BUBBLE_COLUMN || m == Material.SWEET_BERRY_BUSH;
            boolean badFloor = BlockClassifier.isDangerous(m) || BlockClassifier.speedMultiplier(m) < 1.0
                    || m == Material.SLIME_BLOCK || m == Material.HONEY_BLOCK || m.name().endsWith("_BED");
            double[] boxes = EMPTY.boxes;
            if (!b.isPassable()) {
                Collection<BoundingBox> shape = b.getCollisionShape().getBoundingBoxes();
                boxes = new double[shape.size() * 6];
                int i = 0;
                for (BoundingBox s : shape) {
                    boxes[i++] = x + s.getMinX();
                    boxes[i++] = y + s.getMinY();
                    boxes[i++] = z + s.getMinZ();
                    boxes[i++] = x + s.getMaxX();
                    boxes[i++] = y + s.getMaxY();
                    boxes[i++] = z + s.getMaxZ();
                }
            }
            return new Cell(boxes, avoid, m == Material.WATER, badFloor, m.getSlipperiness());
        }
    }

    /** A body between two ticks: what the driver reads from the entity before pressing keys. */
    public static final class State {
        public double x, y, z, vx, vy, vz;
        /** Where the view points (the last tick's yaw): a turn starts from here. */
        public float yaw;
        /** How far the view turned last tick (signed degrees): the runner prefers to keep turning as it was. */
        public float turned;
        /** Vanilla fall distance: descent since last standing, not counting the landing tick. */
        public double fall;
        public boolean onGround;
        /** Sprinting when the tick begins. */
        public boolean sprinting;
        /** The last move ran into a wall other than at a glancing angle (ends a sprint). */
        public boolean hardCollision;
        /** Hunger allows starting a sprint. */
        public boolean canSprint = true;
        /** A player body (not a Mannequin). */
        public boolean player = true;
        /** Fall damage taken so far. */
        public int damage;
        /** The last move touched a wall at all. */
        public boolean collided;
        /** Left what this model covers; {@link #why} says how. */
        public boolean dead;
        public String why;

        public State copy() {
            State s = new State();
            s.x = x; s.y = y; s.z = z; s.vx = vx; s.vy = vy; s.vz = vz; s.yaw = yaw; s.turned = turned;
            s.fall = fall; s.onGround = onGround; s.sprinting = sprinting; s.hardCollision = hardCollision;
            s.canSprint = canSprint; s.player = player; s.damage = damage; s.collided = collided; s.dead = dead; s.why = why;
            return s;
        }

        public double hspeed() { return Math.hypot(vx, vz); }
    }

    /** Slipperiness of the block the body stands on (0.6 for most, 0.98 for ice): what its slide to a stop depends on. */
    public static double frictionUnder(State s, Terrain t) {
        int y = (int) Math.floor(s.y - 0.5000001);
        int cx = (int) Math.floor(s.x), cz = (int) Math.floor(s.z);
        Cell under = t.cell(cx, y, cz);
        if (under.boxes().length > 0) return under.friction();
        // Entity.getOnPos: with the center over air, the block that counts is the one holding the
        // body up, the nearest of those under the hitbox (standing on the edge of ice is still ice).
        Cell best = under;
        double bestDist = Double.MAX_VALUE;
        for (int bx = (int) Math.floor(s.x - HALF + EPS); bx <= (int) Math.floor(s.x + HALF - EPS); bx++) {
            for (int bz = (int) Math.floor(s.z - HALF + EPS); bz <= (int) Math.floor(s.z + HALF - EPS); bz++) {
                Cell c = t.cell(bx, y, bz);
                if (c.boxes().length == 0) continue;
                double dx = bx + 0.5 - s.x, dz = bz + 0.5 - s.z, d = dx * dx + dz * dz;
                if (d < bestDist) {
                    bestDist = d;
                    best = c;
                }
            }
        }
        return best.friction();
    }

    /** Scratch space for one thread's collision queries. */
    private static final class Work {
        double[] boxes = new double[6 * 96];
        int count;
        final double[] out = new double[3];
        float[] heights = new float[32];
    }

    private static final ThreadLocal<Work> WORK = ThreadLocal.withInitial(Work::new);

    /**
     * Advances {@code s} one tick with W held or not, the sprint and jump
     * keys, facing {@code yaw}.
     */
    public static void step(State s, Terrain t, MovementConfig c, float yaw, boolean forward, boolean sprintKey, boolean jumpKey) {
        if (s.dead) return;
        Work w = WORK.get();

        // LocalPlayer sprint rules, from the tick-start state and this tick's keys.
        boolean sprinting = s.sprinting;
        if (!sprinting) sprinting = sprintKey && forward && s.canSprint;
        else if (!forward || s.hardCollision) sprinting = false;

        double vx = s.vx, vy = s.vy, vz = s.vz;
        // LivingEntity.aiStep: a player's leftover horizontal speed is dropped as a whole, anything else's per axis.
        if (s.player) {
            if (vx * vx + vz * vz < 9.0E-6) vx = vz = 0;
        } else {
            if (Math.abs(vx) < 0.003) vx = 0;
            if (Math.abs(vz) < 0.003) vz = 0;
        }
        if (Math.abs(vy) < 0.003) vy = 0;

        double rad = Math.toRadians(yaw), sin = Math.sin(rad), cos = Math.cos(rad);
        if (jumpKey && s.onGround) {
            vy = c.getJumpVelocity();
            if (sprinting) {
                vx -= sin * c.getSprintJumpBoost();
                vz += cos * c.getSprintJumpBoost();
            }
        }

        // Friction of the block 0.5 under the feet, as it was before the move.
        double block = s.onGround ? frictionUnder(s, t) : 1.0;
        double drag = block * c.getAirDrag();
        if (forward) {
            double speed = Body.PLAYER_MOVEMENT_SPEED * (sprinting ? Body.SPRINT_SPEED_FACTOR : 1.0);
            double accel = (s.onGround ? speed * (0.21600002 / (block * block * block))
                    : sprinting ? Body.SPRINT_AIR_SPEED : Body.LIVING_AIR_SPEED) * INPUT;
            vx -= sin * accel;
            vz += cos * accel;
        }

        move(s, t, w, vx, vy, vz);
        double mx = w.out[0], my = w.out[1], mz = w.out[2];
        boolean hitX = Math.abs(mx - vx) > 1.0E-5, hitZ = Math.abs(mz - vz) > 1.0E-5, hitY = my != vy;
        s.x += mx;
        s.y += my;
        s.z += mz;
        s.onGround = hitY && vy < 0;
        s.collided = hitX || hitZ;
        if (hitX) vx = 0;
        if (hitZ) vz = 0;
        if (hitY) vy = 0;

        // LocalPlayer.isHorizontalCollisionMinor: still moving within 8 degrees of the keys.
        boolean minor = false;
        if (s.collided && forward) {
            double moved = mx * mx + mz * mz;
            if (moved >= 1.0E-5) {
                double dot = (-sin * mx + cos * mz) / Math.sqrt(moved);
                minor = Math.acos(Math.max(-1, Math.min(1, dot))) < 0.13962634;
            }
        }
        s.hardCollision = s.collided && !minor;
        s.sprinting = sprinting;

        if (s.onGround) {
            if (s.fall > 0) s.damage += Math.max(0, (int) Math.ceil(s.fall - 3.0));
            s.fall = 0;
        } else if (my < 0) {
            s.fall -= my;
        }

        s.vx = vx * drag;
        s.vz = vz * drag;
        s.vy = (vy - c.getGravity()) * c.getVerticalDrag();

        // Somewhere this model doesn't cover?
        for (int bx = (int) Math.floor(s.x - HALF + EPS); bx <= (int) Math.floor(s.x + HALF - EPS); bx++) {
            for (int bz = (int) Math.floor(s.z - HALF + EPS); bz <= (int) Math.floor(s.z + HALF - EPS); bz++) {
                for (int by = (int) Math.floor(s.y + EPS); by <= (int) Math.floor(s.y + HEIGHT - EPS); by++) {
                    Cell in = t.cell(bx, by, bz);
                    if (in.avoid()) {
                        s.dead = true;
                        s.why = in.water() ? WATER : "hazard";
                        return;
                    }
                }
                if (s.onGround && t.cell(bx, (int) Math.floor(s.y - 0.01), bz).badFloor()) {
                    s.dead = true;
                    s.why = "bad_floor";
                    return;
                }
            }
        }
    }

    // =========================================================================
    // Entity.collide
    // =========================================================================

    /** Resolves the move {@code (dx, dy, dz)} of the hitbox at {@code s} into {@code w.out}. */
    private static void move(State s, Terrain t, Work w, double dx, double dy, double dz) {
        double x0 = s.x - HALF, y0 = s.y, z0 = s.z - HALF, x1 = s.x + HALF, y1 = s.y + HEIGHT, z1 = s.z + HALF;
        gather(t, w, Math.min(x0, x0 + dx), Math.min(y0, y0 + dy), Math.min(z0, z0 + dz),
                Math.max(x1, x1 + dx), Math.max(y1, y1 + dy) + STEP, Math.max(z1, z1 + dz));
        collide(w, x0, y0, z0, x1, y1, z1, dx, dy, dz);
        double rx = w.out[0], ry = w.out[1], rz = w.out[2];
        boolean hitX = rx != dx, hitY = ry != dy, hitZ = rz != dz;
        boolean landed = hitY && dy < 0;
        if (!(landed || s.onGround) || !(hitX || hitZ)) return;

        // Step up: from where the fall ended (or from here), try each height a
        // nearby block face offers, lowest first, and take the first that gets further.
        double by0 = landed ? y0 + ry : y0, by1 = by0 + HEIGHT;
        float current = (float) ry;
        int n = 0;
        for (int i = 0; i < w.count; i++) {
            int o = i * 6;
            // Only colliders in the swept box (the move, up to the step height).
            if (w.boxes[o + 3] <= Math.min(x0, x0 + dx) + EPS || w.boxes[o] >= Math.max(x1, x1 + dx) - EPS
                    || w.boxes[o + 5] <= Math.min(z0, z0 + dz) + EPS || w.boxes[o + 2] >= Math.max(z1, z1 + dz) - EPS
                    || w.boxes[o + 4] <= by0 - (landed ? 0 : 1.0E-5) + EPS || w.boxes[o + 1] >= by1 + STEP - EPS) continue;
            for (int k = 1; k <= 4; k += 3) {
                float f = (float) (w.boxes[o + k] - by0);
                if (f < 0 || f == current || f > (float) STEP) continue;
                if (n == w.heights.length) w.heights = Arrays.copyOf(w.heights, n * 2);
                w.heights[n++] = f;
            }
        }
        if (n == 0) return;
        Arrays.sort(w.heights, 0, n);
        double best = rx * rx + rz * rz;
        float last = Float.NaN;
        for (int i = 0; i < n; i++) {
            float g = w.heights[i];
            if (g == last) continue;
            last = g;
            collide(w, x0, by0, z0, x1, by1, z1, dx, g, dz);
            if (w.out[0] * w.out[0] + w.out[2] * w.out[2] > best) {
                w.out[1] += by0 - y0;
                return;
            }
        }
        w.out[0] = rx;
        w.out[1] = ry;
        w.out[2] = rz;
    }

    /** {@code Entity.collideWithShapes}: Y, then the larger of X and Z, then the other. */
    private static void collide(Work w, double x0, double y0, double z0, double x1, double y1, double z1,
                                double dx, double dy, double dz) {
        if (dy != 0) {
            dy = sweep(w, 1, x0, y0, z0, x1, y1, z1, dy);
            y0 += dy;
            y1 += dy;
        }
        boolean zFirst = Math.abs(dx) < Math.abs(dz);
        if (zFirst && dz != 0) {
            dz = sweep(w, 2, x0, y0, z0, x1, y1, z1, dz);
            z0 += dz;
            z1 += dz;
        }
        if (dx != 0) {
            dx = sweep(w, 0, x0, y0, z0, x1, y1, z1, dx);
            x0 += dx;
            x1 += dx;
        }
        if (!zFirst && dz != 0) dz = sweep(w, 2, x0, y0, z0, x1, y1, z1, dz);
        w.out[0] = dx;
        w.out[1] = dy;
        w.out[2] = dz;
    }

    /** How far the box can move along one axis (0 = x, 1 = y, 2 = z) before a collider stops it. */
    private static double sweep(Work w, int axis, double x0, double y0, double z0, double x1, double y1, double z1, double d) {
        int a = (axis + 1) % 3, b = (axis + 2) % 3;
        double[] lo = {x0, y0, z0}, hi = {x1, y1, z1};
        for (int i = 0; i < w.count && d != 0; i++) {
            int o = i * 6;
            if (w.boxes[o + 3 + a] <= lo[a] + EPS || w.boxes[o + a] >= hi[a] - EPS
                    || w.boxes[o + 3 + b] <= lo[b] + EPS || w.boxes[o + b] >= hi[b] - EPS) continue;
            if (d > 0 && w.boxes[o + axis] >= hi[axis] - EPS) d = Math.min(d, w.boxes[o + axis] - hi[axis]);
            else if (d < 0 && w.boxes[o + 3 + axis] <= lo[axis] + EPS) d = Math.max(d, w.boxes[o + 3 + axis] - lo[axis]);
        }
        return Math.abs(d) < EPS ? 0 : d;
    }

    /** Every collision box that reaches into the region (a fence in the cell below is 1.5 tall). */
    private static void gather(Terrain t, Work w, double x0, double y0, double z0, double x1, double y1, double z1) {
        w.count = 0;
        for (int bx = (int) Math.floor(x0 - EPS); bx <= (int) Math.floor(x1 + EPS); bx++) {
            for (int bz = (int) Math.floor(z0 - EPS); bz <= (int) Math.floor(z1 + EPS); bz++) {
                for (int by = (int) Math.floor(y0 - EPS) - 1; by <= (int) Math.floor(y1 + EPS); by++) {
                    double[] boxes = t.cell(bx, by, bz).boxes();
                    if (boxes.length == 0) continue;
                    if ((w.count + boxes.length / 6) * 6 > w.boxes.length) w.boxes = Arrays.copyOf(w.boxes, w.boxes.length * 2 + boxes.length);
                    System.arraycopy(boxes, 0, w.boxes, w.count * 6, boxes.length);
                    w.count += boxes.length / 6;
                }
            }
        }
    }
}
