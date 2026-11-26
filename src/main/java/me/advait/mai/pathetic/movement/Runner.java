package me.advait.mai.pathetic.movement;

import me.advait.mai.body.Body;
import me.advait.mai.pathetic.config.MovementConfig;

import java.util.List;

/**
 * Runs a stretch of plain ground (walks, diagonals, steps up and down) the
 * way a player does: sprinting, jumping whenever a jump gets further, running
 * off ledges instead of stopping at them, never going back for a waypoint.
 *
 * <p>Each tick {@link #plan} tries a set of ways to go on from the body's
 * exact state: every combination of how far ahead on the route to aim
 * ({@link #AIM_AHEAD}) and when to jump ({@link #JUMP_AFTER}, then on every
 * landing or never again). Each is played forward in {@link RunSim} for
 * {@link #HORIZON} ticks and judged by how far along the route it gets. One
 * that takes fall damage, touches anything the simulation doesn't model,
 * stands anywhere but on the route, or overshoots its end is thrown out. The
 * keys of the best one's first tick are what the body presses; next tick the
 * choice is made again from where the body really is.
 *
 * <p>The route is the planner's path, so the pathfinder still decides where
 * to go and this only decides how to move along it. When nothing is valid
 * (or the route's end is close), {@link #plan} returns null and the
 * movement types' own executors take over.
 */
public final class Runner {

    /** What to press this tick, and where the simulation says that leaves the body. */
    public record Step(float yaw, boolean jump, RunSim.State next, double score, boolean bumped, String label) {}

    /** Pure-pursuit distances tried for the heading. */
    private static final double[] AIM_AHEAD = {1.5, 2.5, 4.0, 6.0};
    /** Ground ticks to wait before the first jump; the last means never jump. */
    private static final int[] JUMP_AFTER = {0, 1, 2, 3, 5, Integer.MAX_VALUE};
    /** Ticks a candidate is played forward before it is scored. */
    private static final int HORIZON = 28;
    /** A candidate still in the air at the horizon is followed this much longer, to its landing. */
    private static final int LANDING_GRACE = 18;
    /** How far from the route line the body may stand. */
    private static final double CORRIDOR = 1.25;
    /** How far from the route's height (the waypoints' floor cells) the body may stand. */
    private static final double HEIGHT_SLACK = 0.6;
    /** The run ends this far before the route does; the next movement's own executor takes it from there. */
    private static final double END_NEAR = 1.0;
    /** Spare ground beyond the friction slide where the walk stops at the route's end. */
    private static final double STOP_MARGIN = 0.3;
    /** Progress credited per tick to a candidate that reached the end early. */
    private static final double END_CREDIT = 0.3;
    /**
     * Sidesteps: when every straight candidate runs into something, the
     * heading is also tried turned by these angles for the first
     * {@link #SIDESTEP_TICKS} ticks (a hitbox that overlaps a block corner
     * by a few pixels gets round it instead of pushing into it).
     */
    private static final float[] SIDESTEPS = {-50, -25, 25, 50};
    private static final int SIDESTEP_TICKS = 5;
    /** How far before the route's first swim cell going into the water still counts (the bank's edge is rarely where the planner put it). */
    private static final double WATER_EARLY = 1.5;
    /** Block slipperiness above which the runner stands down (ordinary blocks are 0.6, slime 0.8, ice 0.98). */
    private static final double SLIPPERY = 0.61;
    /** A jump this tick is taken over a plan without one that gets at most this much further. */
    private static final double JUMP_NOW_BIAS = 0.3;
    /** A first step that moves the body less than this, without jumping, is standing still. */
    private static final double STALL_STEP = 0.02;
    /** A plan that gets less far than this over the horizon is no plan: the movement's own executor takes over. */
    private static final double MIN_PROGRESS = 0.5;

    private Runner() {}

    /** Why the last {@link #plan} returned null (for the debug log). */
    public static String lastReject = "";
    /** When set, {@link #plan} appends every candidate and its score or rejection (the driver logs it for a body that isn't moving). */
    public static StringBuilder trace;

    private static Step traced(Step s, double ahead, int after, boolean again, float side, double along0) {
        if (trace != null) {
            trace.append(label(ahead, after, again, side)).append(" -> ")
                    .append(s == null ? lastReject : String.format("%.2f%s", s.score() - along0, s.bumped() ? " bumped" : "")).append("; ");
        }
        return s;
    }

    /** The planner's path as a polyline, with distance along it. */
    public static final class Route {
        private final double[] x, y, z, at;
        private final double minY;
        /** Distance along at which the route goes on as a swim; infinity when it doesn't. Going into the water from there on is arriving. */
        public double waterFrom = Double.POSITIVE_INFINITY;

        public Route(List<double[]> points) {
            int n = points.size();
            x = new double[n];
            y = new double[n];
            z = new double[n];
            at = new double[n];
            double low = Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                x[i] = points.get(i)[0];
                y[i] = points.get(i)[1];
                z[i] = points.get(i)[2];
                if (i > 0) at[i] = at[i - 1] + Math.hypot(x[i] - x[i - 1], z[i] - z[i - 1]);
                low = Math.min(low, y[i]);
            }
            minY = low;
        }

        public double length() { return at[at.length - 1]; }

        /** Result of {@link #project}: distance along, distance off the line, and the route's height range there. */
        double along, off, yLow, yHigh;

        /**
         * Projects a position onto the route, looking only at the part from a
         * little before {@code from} on (a route that doubles back must not
         * snap the body onto a stretch it hasn't reached).
         */
        void project(double px, double pz, double from) {
            double best = Double.MAX_VALUE;
            for (int i = 1; i < x.length; i++) {
                if (at[i] < from - 1.0 && i < x.length - 1) continue;
                // With no known progress (from = -infinity) the whole route is searched.
                if (from > Double.NEGATIVE_INFINITY && at[i - 1] > from + 8.0 && best < Double.MAX_VALUE) break;
                double sx = x[i] - x[i - 1], sz = z[i] - z[i - 1], len2 = sx * sx + sz * sz;
                double t = len2 < 1e-9 ? 0 : Math.max(0, Math.min(1, ((px - x[i - 1]) * sx + (pz - z[i - 1]) * sz) / len2));
                double d = Math.hypot(px - (x[i - 1] + sx * t), pz - (z[i - 1] + sz * t));
                if (d < best - 1e-9) {
                    best = d;
                    along = at[i - 1] + Math.sqrt(len2) * t;
                    yLow = Math.min(y[i - 1], y[i]);
                    yHigh = Math.max(y[i - 1], y[i]);
                }
            }
            off = best;
        }

        /** The point {@code s} along the route, into {@code out} (x, z). */
        void pointAt(double s, double[] out) {
            int last = x.length - 1;
            if (s >= at[last]) {
                out[0] = x[last];
                out[1] = z[last];
                return;
            }
            for (int i = 1; i <= last; i++) {
                if (s <= at[i]) {
                    double len = at[i] - at[i - 1], t = len < 1e-9 ? 1 : Math.max(0, (s - at[i - 1]) / len);
                    out[0] = x[i - 1] + (x[i] - x[i - 1]) * t;
                    out[1] = z[i - 1] + (z[i] - z[i - 1]) * t;
                    return;
                }
            }
        }
    }

    /**
     * The best keys for this tick from {@code start} along {@code route},
     * or null if no candidate is valid. {@code stopAtEnd}: the walk stops on
     * the route's last point, so the run must leave room to stop before it.
     */
    public static Step plan(RunSim.State start, RunSim.Terrain terrain, MovementConfig c, Route route, boolean stopAtEnd) {
        route.project(start.x, start.z, Double.NEGATIVE_INFINITY);
        double along0 = route.along;
        if (route.off > CORRIDOR + 0.5) return reject(String.format("far_from_route %.2f", route.off));
        if (route.length() - along0 < END_NEAR + 0.25) return reject("route_end");
        // Ice is the walk executor's: every candidate here holds W and sprint, and on ice a run can't
        // be turned or stopped inside the horizon (it jumped along an ice walkway and slid off its corner).
        if (start.onGround && RunSim.frictionUnder(start, terrain) > SLIPPERY) return reject("slippery");
        // The best candidate overall, and the best of those that jump this tick.
        Step best = null, bestJump = null;
        String why = "";
        for (double ahead : AIM_AHEAD) {
            for (int after : JUMP_AFTER) {
                for (int again = 0; again < 2; again++) {
                    if (after == Integer.MAX_VALUE && again == 1) continue;
                    Step s = traced(rollout(start, terrain, c, route, along0, stopAtEnd, ahead, after, again == 1, 0),
                            ahead, after, again == 1, 0, along0);
                    if (s == null) {
                        why = lastReject;
                        continue;
                    }
                    if (best == null || s.score() > best.score() + 1e-6) best = s;
                    if (s.jump() && (bestJump == null || s.score() > bestJump.score() + 1e-6)) bestJump = s;
                }
            }
        }
        if (best == null || best.bumped()) {
            for (float side : SIDESTEPS) {
                for (double ahead : AIM_AHEAD) {
                    for (int after : JUMP_AFTER) {
                        Step s = traced(rollout(start, terrain, c, route, along0, stopAtEnd, ahead, after, true, side),
                                ahead, after, true, side, along0);
                        if (s == null) continue;
                        // A sidestep has to beat running straight by a margin, or the bot weaves.
                        if (best == null || s.score() > best.score() + (best.bumped() && !s.bumped() ? 1e-6 : 0.25)) best = s;
                        if (s.jump() && (bestJump == null || s.score() > bestJump.score() + 1e-6)) bestJump = s;
                    }
                }
            }
        }
        if (best == null) return reject(why);
        // "Wait, then jump" from a body the wait doesn't move is the same choice again next tick, forever
        // (candidates differ in what they do after, and a later jump can score higher by that alone).
        if (!best.jump() && start.onGround
                && Math.hypot(best.next().x - start.x, best.next().z - start.z) < STALL_STEP) {
            if (bestJump == null) return reject("stalled");
            best = bestJump;
        }
        // Between a jump now and a later one that scores about the same, jump now: the later one is chosen
        // again next tick (it is re-planned from scratch), and the body dawdles into the step it has to jump.
        if (!best.jump() && bestJump != null && bestJump.score() >= best.score() - JUMP_NOW_BIAS) best = bestJump;
        if (best.score() - along0 < MIN_PROGRESS) return reject(String.format("no_progress %.2f", best.score() - along0));
        return best;
    }

    private static Step reject(String why) {
        lastReject = why;
        return null;
    }

    /** One candidate played forward: aim {@code ahead} along the route, first jump after {@code after} ground ticks, then on every landing if {@code again}. */
    private static Step rollout(RunSim.State start, RunSim.Terrain terrain, MovementConfig c, Route route, double along0,
                                boolean stopAtEnd, double ahead, int after, boolean again, float side) {
        RunSim.State s = start.copy();
        double total = route.length(), along = along0, scoreAt = Double.NaN;
        double[] aim = new double[2];
        float yaw0 = 0, yaw = 0;
        boolean jump0 = false, yawSet = false, bumped = false;
        RunSim.State next = null;
        int groundTicks = 0, jumps = 0;
        for (int t = 0; ; t++) {
            route.project(s.x, s.z, along);
            along = Math.max(along, route.along);
            if (t == HORIZON) scoreAt = along;
            if (s.onGround) {
                if (t > 0 && RunSim.frictionUnder(s, terrain) > SLIPPERY) return reject("slippery t=" + t);
                if (t > 0) {
                    if (route.off > CORRIDOR || s.y < route.yLow - HEIGHT_SLACK || s.y > route.yHigh + HEIGHT_SLACK)
                        return reject(String.format("off_route t=%d d=%.2f y=%.2f", t, route.off, s.y));
                    if (route.along >= total - END_NEAR) {
                        // Keys released here, the body slides v*f/(1-f) further.
                        if (stopAtEnd) {
                            double f = RunSim.frictionUnder(s, terrain) * c.getAirDrag();
                            if (total - route.along < s.hspeed() * f / (1 - f) + STOP_MARGIN) return reject("no_stop_room t=" + t);
                        }
                        double score = Double.isNaN(scoreAt) ? route.along + END_CREDIT * (HORIZON - t) : scoreAt;
                        return new Step(yaw0, jump0, next, score, bumped, label(ahead, after, again, side));
                    }
                }
                if (t >= HORIZON) {
                    if (!canStop(s, terrain, c, route, along, stopAtEnd, start.damage)) return reject("cannot_stop");
                    return new Step(yaw0, jump0, next, scoreAt, bumped, label(ahead, after, again, side));
                }
            } else {
                if (t >= HORIZON + LANDING_GRACE) return reject("no_landing");
                if (route.off > 3.0) return reject("flew_off_route");
                // Past the end in the air: nothing to land on that the route knows about.
                if (t > 0 && route.along >= total - 1e-6 && route.off > 0.5) return reject("past_end");
            }
            route.pointAt(route.along + ahead, aim);
            double ax = aim[0] - s.x, az = aim[1] - s.z;
            if (!yawSet || ax * ax + az * az > 0.04) {
                yaw = Body.yawOf(ax, az);
                yawSet = true;
            }
            boolean jump = false;
            if (s.onGround) {
                if (groundTicks >= after && (jumps == 0 || again)) {
                    jump = true;
                    jumps++;
                }
                groundTicks++;
            }
            float heading = t < SIDESTEP_TICKS ? yaw + side : yaw;
            RunSim.step(s, terrain, c, heading, true, true, jump);
            if (s.hardCollision) bumped = true;
            if (s.dead) {
                // Into the water where the route swims on: that is where it was going, and the sooner the better.
                if (RunSim.WATER.equals(s.why)) {
                    route.project(s.x, s.z, along);
                    if (route.off <= CORRIDOR && route.along >= route.waterFrom - WATER_EARLY) {
                        if (t == 0) {
                            yaw0 = heading;
                            jump0 = jump;
                            next = s.copy();
                        }
                        double reached = Math.max(along, route.along);
                        return new Step(yaw0, jump0, next, t >= HORIZON ? scoreAt : reached + END_CREDIT * (HORIZON - t),
                                bumped, label(ahead, after, again, side));
                    }
                }
                return reject(s.why + " t=" + t);
            }
            if (s.damage > start.damage) return reject("fall_damage t=" + t);
            if (s.y < route.minY - 8) return reject("fell t=" + t);
            if (t == 0) {
                yaw0 = heading;
                jump0 = jump;
                next = s.copy();
            }
        }
    }

    /**
     * Whether the body can still come to rest on the route from {@code from}
     * by letting go of the keys. A plan is only as good as where it leaves
     * the body: on ice the slide is several blocks, and a plan that ended at
     * the horizon running at a corner or at the route's end slid off it.
     */
    private static boolean canStop(RunSim.State from, RunSim.Terrain terrain, MovementConfig c, Route route, double along,
                                   boolean stopAtEnd, int damage) {
        // Ordinary ground stops a sprint within half a block: nothing to check unless there is an end to overrun.
        double f = RunSim.frictionUnder(from, terrain) * c.getAirDrag();
        double slide = from.hspeed() * f / (1 - f);
        if (slide < 0.6 && !stopAtEnd) return true;
        RunSim.State s = from.copy();
        for (int t = 0; t < 120; t++) {
            RunSim.step(s, terrain, c, 0f, false, false, false);
            if (s.dead || s.damage > damage || s.y < route.minY - 8) return false;
            if (s.onGround && s.hspeed() < 0.01) break;
        }
        if (!s.onGround) return false;
        route.project(s.x, s.z, along);
        if (route.off > CORRIDOR || s.y < route.yLow - HEIGHT_SLACK || s.y > route.yHigh + HEIGHT_SLACK) return false;
        return !stopAtEnd || route.along < route.length() - 1e-6 || route.off < 0.3;
    }

    private static String label(double ahead, int after, boolean again, float side) {
        return "aim=" + ahead + (after == Integer.MAX_VALUE ? " run" : " jump@" + after + (again ? "+" : ""))
                + (side == 0 ? "" : " side=" + (int) side);
    }
}
