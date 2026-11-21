package me.advait.mai.pathetic.movement;

import me.advait.mai.body.Body;
import me.advait.mai.pathetic.config.MovementConfig;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * Predictive airborne control. Replaces "push toward the waypoint every
 * tick" (which saturates at the air-drag equilibrium speed and overshoots
 * every short or tight landing) with a model-based controller: simulate the
 * remaining airtime, then choose this tick's horizontal input so the
 * predicted touchdown point is the landing block's center.
 *
 * <p>The model is vanilla's airborne {@code travel()}:
 * <ul>
 *   <li>The velocity read at the start of a tick, plus this tick's key
 *       acceleration, is exactly the displacement the entity makes.
 *   <li>Afterwards horizontal velocity is multiplied by {@code air_drag}
 *       (0.91) and vertical by {@code (vy - gravity) * vertical_drag}.
 * </ul>
 * The controller only has a player's inputs: each tick it picks one of the
 * nine W/A/S/D combinations (or none) with the view on the landing, the one
 * whose predicted touchdown lands nearest the landing block's center.
 * Early ticks move the touchdown a lot and late ticks a little, so picking
 * greedily each tick converges like a player feathering the keys.
 */
public final class AirControl {

    private static final int MAX_SIM_TICKS = 80;

    private AirControl() {}

    /**
     * Ticks until the entity, starting at height {@code y} with upcoming
     * vertical displacement {@code vy}, is descending at or below
     * {@code landY}. Returns -1 if that never happens within the horizon
     * (the landing is above the apex).
     */
    public static int ticksToLand(double y, double vy, double landY, MovementConfig c) {
        for (int n = 1; n <= MAX_SIM_TICKS; n++) {
            y += vy;
            vy = (vy - c.getGravity()) * c.getVerticalDrag();
            if (vy < 0 && y <= landY + 1e-3) return n;
        }
        return -1;
    }

    /** Horizontal travel over {@code n} ticks from velocity {@code v} with constant input {@code a}. */
    public static double travel(double v, double a, int n, double drag) {
        double sum = 0;
        for (int k = 0; k < n; k++) {
            v += a;
            sum += v;
            v *= drag;
        }
        return sum;
    }

    /**
     * One tick of airborne control toward ({@code tx}, {@code tz}) landing at
     * height {@code landY}: faces the landing and presses the key combination
     * that brings the predicted touchdown closest to it. Strafe keys correct
     * sideways drift, S brakes an overshoot.
     */
    public static void steer(TickContext ctx, double tx, double tz, double landY) {
        LivingEntity entity = ctx.entity();
        MovementConfig c = ctx.config;
        Vector vel = entity.getVelocity();
        double drag = c.getAirDrag();
        Body body = ctx.body();

        double dx = tx - ctx.current.getX();
        double dz = tz - ctx.current.getZ();
        float yaw = Math.hypot(dx, dz) > 0.05 ? Body.yawOf(dx, dz) : ctx.current.getYaw();
        body.yaw(yaw);

        int n = ticksToLand(ctx.current.getY(), vel.getY(), landY, c);
        if (n < 0) {
            // Landing above the reachable apex; nothing smarter to do than
            // drive straight at it.
            body.keys(1, 0).sprint(true);
            return;
        }

        // Touchdown error with no more input, and how far one tick of unit
        // input now moves the touchdown (it keeps paying off, decayed by drag).
        double rx = dx - travel(vel.getX(), 0, n, drag);
        double rz = dz - travel(vel.getZ(), 0, n, drag);
        double now = (1 - Math.pow(drag, n)) / (1 - drag);

        int bestF = 0, bestR = 0;
        double best = Double.MAX_VALUE;
        for (int f = -1; f <= 1; f++) {
            for (int r = -1; r <= 1; r++) {
                // Sprint holds only while W is pressed.
                double speed = f > 0 ? Body.SPRINT_AIR_SPEED : Body.LIVING_AIR_SPEED;
                double[] a = Body.accel(f, r, false, yaw, speed);
                double miss = Math.hypot(rx - a[0] * now, rz - a[1] * now);
                if (miss < best - 1e-9) {
                    best = miss;
                    bestF = f;
                    bestR = r;
                }
            }
        }
        body.keys(bestF, bestR).sprint(true);
    }

    /**
     * Takeoff planning for a jump whose landing-block center is {@code dist}
     * away horizontally, with height change {@code dy}, taking off from a
     * block of slipperiness {@code slip}. Returns {@code {preferred, minimum}}
     * initial horizontal speeds, or null if the landing is above the apex.
     *
     * <p>The takeoff tick is a ground tick: the entity moves by the speed we
     * set, then its velocity is multiplied by ground friction
     * ({@code slip * air_drag}, about 0.55), not by air drag. Every later
     * tick is airborne. Air input starts on the second tick.
     *
     * <ul>
     *   <li>{@code minimum}: full forward air input the whole way, and the
     *       hitbox overlaps the landing block's near edge by 0.2 (center 0.6
     *       short of the block center) on the last tick the feet are still
     *       above its top.
     *   <li>{@code preferred}: land on the block center while reserving half
     *       the air-control budget so the controller can correct either way.
     * </ul>
     */
    public static double[] takeoffSpeeds(double dist, double dy, double slip,
                                         double maxAccel, MovementConfig c) {
        int n = ticksToLand(0, c.getJumpVelocity(), dy, c);
        if (n < 0) return null;
        double drag = c.getAirDrag();
        double groundDrag = slip * drag;
        // Travel per unit of takeoff speed: the takeoff tick, then the decayed
        // remainder over n-1 airborne ticks.
        double perUnitV = 1.0 + travel(groundDrag, 0, n - 1, drag);
        double authority = maxAccel * travel(0, 1, n - 1, drag);
        double preferred = (dist - 0.5 * authority) / perUnitV;
        // Vanilla resolves vertical movement before horizontal, so on the
        // touchdown tick the feet drop past the landing block's top before
        // the bot moves forward. The hitbox must already overlap the block
        // one tick earlier, after n - 1 ticks.
        int m = Math.max(1, n - 1);
        double perUnitM = 1.0 + travel(groundDrag, 0, m - 1, drag);
        double authorityM = maxAccel * travel(0, 1, m - 1, drag);
        double minimum = (dist - 0.6 - authorityM) / perUnitM;
        return new double[]{Math.max(0.05, preferred), Math.max(0.0, minimum)};
    }
}
