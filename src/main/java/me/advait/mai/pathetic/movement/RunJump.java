package me.advait.mai.pathetic.movement;

import me.advait.mai.body.Body;
import me.advait.mai.pathetic.BlockClassifier;
import me.advait.mai.pathetic.config.MovementConfig;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sprint-jumping along the route the way a player holding space does: over
 * flat ground, up one-block rises and down small drops, not only on dead
 * level straights. A sprint-jump covers ground faster than sprinting
 * (the 0.2 takeoff boost and low air drag), so a player jumps whenever the
 * jump lands somewhere they meant to go.
 *
 * <p>{@link #plan} simulates the jump tick by tick with vanilla physics
 * (this tick's velocity, ground acceleration and the boost on takeoff, then
 * gravity, drag and sprint air control with W held toward a fixed heading)
 * against the real block collision shapes, and accepts it only if the
 * hitbox touches nothing until it lands, on the route (within {@link #ON_ROUTE}
 * of it, at its height), no more than {@link #MAX_DROP} below the takeoff
 * (deeper hurts), and further along than {@link #MIN_PROGRESS}.
 */
public final class RunJump {

    /** A planned jump: the heading to hold, where it lands, and how far along the route that is. */
    public record Plan(float yaw, double landX, double landY, double landZ, int ticks, double progress) {}

    /** How far the landing may be from the route line. */
    private static final double ON_ROUTE = 0.6;
    /** Fall distance is the peak (~1.25) plus the drop; over 3 hurts. */
    private static final double MAX_DROP = 1.7;
    /** A jump that gains less than this over the route isn't worth leaving the ground. */
    private static final double MIN_PROGRESS = 2.0;
    private static final int MAX_TICKS = 20;
    /** Spare ground beyond the friction slide when the route ends where the walk stops. */
    private static final double STOP_MARGIN = 0.3;
    private static final double HALF = 0.3, HEIGHT = 1.8;
    /** Pure-pursuit lookaheads tried for the heading. */
    private static final double[] AIM_AHEAD = {2.5, 3.5, 4.5, 6.0};

    private RunJump() {}

    /** Why the last {@link #plan} found nothing (for the debug log). */
    public static String lastReject = "";

    private static Plan reject(String why) {
        lastReject = why;
        return null;
    }

    /**
     * The best jump to take now along {@code route} (points x, y, z from the
     * bot's position on), or null if none lands back on it.
     */
    public static Plan plan(TickContext ctx, List<double[]> route) {
        return plan(ctx, route, false);
    }

    /**
     * {@code stopAtEnd}: the route's last point is where the walk stops, so
     * the landing must leave room to stop on friction before it. A jump that
     * touched down on the last block at sprint-jump speed slid off its far side.
     */
    public static Plan plan(TickContext ctx, List<double[]> route, boolean stopAtEnd) {
        if (route.size() < 2) return reject("no_route");
        double total = 0;
        for (int i = 1; i < route.size(); i++) total += hdist(route.get(i - 1), route.get(i));
        if (total < MIN_PROGRESS + 0.5) return reject(String.format("short_route %.2f", total));
        World world = ctx.current.getWorld();
        Map<Long, List<BoundingBox>> shapes = new HashMap<>();
        Plan best = null;
        for (double ahead : AIM_AHEAD) {
            double[] p = pointAt(route, Math.min(ahead, total));
            double dx = p[0] - ctx.current.getX(), dz = p[2] - ctx.current.getZ();
            double len = Math.hypot(dx, dz);
            if (len < 1e-3) continue;
            Plan plan = simulate(ctx, world, shapes, route, total, dx / len, dz / len, stopAtEnd);
            if (plan != null && (best == null || plan.progress() > best.progress())) best = plan;
        }
        return best;
    }

    private static Plan simulate(TickContext ctx, World world, Map<Long, List<BoundingBox>> shapes,
                                 List<double[]> route, double total, double fx, double fz, boolean stopAtEnd) {
        MovementConfig c = ctx.config;
        boolean sprinting = ctx.body().sprintingAtTickStart();
        Vector v0 = ctx.velocity();
        double slip = MovementExecutors.slipperinessBelow(ctx.current);
        double x = ctx.current.getX(), y = ctx.current.getY(), z = ctx.current.getZ();
        double startY = y;
        // Takeoff tick: the jump and boost, then this tick's ground acceleration.
        double ga = Body.groundAccel(slip, sprinting) + (sprinting ? c.getSprintJumpBoost() : 0);
        double vx = v0.getX() + fx * ga, vz = v0.getZ() + fz * ga, vy = c.getJumpVelocity();
        double aa = Body.airAccel(sprinting);
        for (int t = 0; t < MAX_TICKS; t++) {
            if (t > 0) {
                vx += fx * aa;
                vz += fz * aa;
            }
            double ny = y + vy, nx = x + vx, nz = z + vz;
            if (vy < 0) {
                // Vanilla resolves vertical movement first: a floor top the
                // feet pass through this tick is a landing.
                double floor = floorTop(world, shapes, x, z, y, ny);
                if (!Double.isNaN(floor)) {
                    // The horizontal part of this tick then moves along the floor.
                    if (overlaps(world, shapes, nx, floor + 1e-4, nz)) return reject("land_wall t=" + t);
                    Plan plan = landing(route, total, ctx, startY, fx, fz, nx, floor, nz, t + 1);
                    if (plan != null && stopAtEnd) {
                        // Keys released on touchdown, the bot slides v*f/(1-f) further.
                        Block under = world.getBlockAt((int) Math.floor(nx), (int) Math.floor(floor - 0.01), (int) Math.floor(nz));
                        double f = under.getType().getSlipperiness() * c.getAirDrag();
                        double slide = Math.hypot(vx, vz) * f / (1 - f);
                        if (total - plan.progress() < slide + STOP_MARGIN)
                            return reject(String.format("no_stop_room left=%.2f slide=%.2f", total - plan.progress(), slide));
                    }
                    return plan;
                }
            }
            if (overlaps(world, shapes, nx, ny, nz)) return reject(String.format("hit t=%d at (%.2f,%.2f,%.2f)", t, nx, ny, nz));
            x = nx;
            y = ny;
            z = nz;
            double f = t == 0 ? slip * c.getAirDrag() : c.getAirDrag();
            vx *= f;
            vz *= f;
            vy = (vy - c.getGravity()) * c.getVerticalDrag();
            if (y < startY - MAX_DROP - 1) return null;
        }
        return null;
    }

    private static Plan landing(List<double[]> route, double total, TickContext ctx, double startY,
                                double fx, double fz, double x, double y, double z, int ticks) {
        if (y < startY - MAX_DROP || y > startY + 1.01) return reject("height " + y);
        // On the route, at its height there.
        double best = Double.MAX_VALUE, along = 0, at = 0, routeY = 0;
        for (int i = 1; i < route.size(); i++) {
            double[] a = route.get(i - 1), b = route.get(i);
            double sx = b[0] - a[0], sz = b[2] - a[2], len2 = sx * sx + sz * sz;
            double t = len2 < 1e-9 ? 0 : Math.max(0, Math.min(1, ((x - a[0]) * sx + (z - a[2]) * sz) / len2));
            double px = a[0] + sx * t, pz = a[2] + sz * t;
            double d = Math.hypot(x - px, z - pz);
            if (d < best) {
                best = d;
                at = along + Math.sqrt(len2) * t;
                // Waypoint heights are the floor cell; the real floor may be a slab or path on it.
                routeY = t < 0.5 ? a[1] : b[1];
            }
            along += Math.sqrt(len2);
        }
        if (best > ON_ROUTE || Math.abs(y - routeY) > 0.6 || at < MIN_PROGRESS)
            return reject(String.format("off_route d=%.2f dy=%.2f at=%.2f", best, y - routeY, at));
        // Not past the end (an overshoot of the last point has to walk back).
        if (at > total - 0.3 && hdist(route.getLast(), new double[]{x, 0, z}) > 0.5) return reject("past_end");
        Block floor = ctx.current.getWorld().getBlockAt((int) Math.floor(x), (int) Math.floor(y - 0.01), (int) Math.floor(z));
        if (BlockClassifier.isDangerous(floor.getType()) || floor.isLiquid()
                || BlockClassifier.speedMultiplier(floor.getType()) < 1.0) return null;
        return new Plan(Body.yawOf(fx, fz), x, y, z, ticks, at);
    }

    /** Highest collision top under the hitbox footprint that the feet cross going from y0 down to y1, or NaN. */
    private static double floorTop(World w, Map<Long, List<BoundingBox>> shapes, double x, double z, double y0, double y1) {
        double top = Double.NaN;
        for (int bx = (int) Math.floor(x - HALF); bx <= (int) Math.floor(x + HALF - 1e-7); bx++) {
            for (int bz = (int) Math.floor(z - HALF); bz <= (int) Math.floor(z + HALF - 1e-7); bz++) {
                for (int by = (int) Math.floor(y1) - 1; by <= (int) Math.floor(y0); by++) {
                    for (BoundingBox b : shapes(w, shapes, bx, by, bz)) {
                        if (b.getMaxX() <= x - HALF || b.getMinX() >= x + HALF
                                || b.getMaxZ() <= z - HALF || b.getMinZ() >= z + HALF) continue;
                        double t = b.getMaxY();
                        if (t <= y0 + 1e-7 && t > y1 && (Double.isNaN(top) || t > top)) top = t;
                    }
                }
            }
        }
        return top;
    }

    private static boolean overlaps(World w, Map<Long, List<BoundingBox>> shapes, double x, double y, double z) {
        for (int bx = (int) Math.floor(x - HALF); bx <= (int) Math.floor(x + HALF - 1e-7); bx++) {
            for (int bz = (int) Math.floor(z - HALF); bz <= (int) Math.floor(z + HALF - 1e-7); bz++) {
                for (int by = (int) Math.floor(y) - 1; by <= (int) Math.floor(y + HEIGHT); by++) {
                    for (BoundingBox b : shapes(w, shapes, bx, by, bz)) {
                        if (b.getMaxX() > x - HALF && b.getMinX() < x + HALF
                                && b.getMaxZ() > z - HALF && b.getMinZ() < z + HALF
                                && b.getMaxY() > y + 1e-7 && b.getMinY() < y + HEIGHT) return true;
                    }
                }
            }
        }
        return false;
    }

    /** A block's collision boxes in world coordinates (liquids and dangerous blocks count as solid, to stay out). */
    private static List<BoundingBox> shapes(World w, Map<Long, List<BoundingBox>> cache, int x, int y, int z) {
        long key = ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (y & 0xFFF);
        List<BoundingBox> out = cache.get(key);
        if (out != null) return out;
        Block b = w.getBlockAt(x, y, z);
        out = new ArrayList<>();
        if (b.isLiquid() || BlockClassifier.isDangerous(b.getType())) {
            out.add(new BoundingBox(x, y, z, x + 1, y + 1, z + 1));
        } else if (!b.isPassable()) {
            for (BoundingBox box : b.getCollisionShape().getBoundingBoxes()) out.add(box.clone().shift(x, y, z));
        }
        cache.put(key, out);
        return out;
    }

    private static double hdist(double[] a, double[] b) {
        return Math.hypot(a[0] - b[0], a[2] - b[2]);
    }

    /** The point {@code s} along the route (horizontal arc length). */
    private static double[] pointAt(List<double[]> route, double s) {
        for (int i = 1; i < route.size(); i++) {
            double[] a = route.get(i - 1), b = route.get(i);
            double len = hdist(a, b);
            if (s <= len || i == route.size() - 1) {
                double t = len < 1e-9 ? 1 : Math.min(1, s / len);
                return new double[]{a[0] + (b[0] - a[0]) * t, a[1], a[2] + (b[2] - a[2]) * t};
            }
            s -= len;
        }
        return route.getLast();
    }
}
