package me.advait.mai.pathetic.movement;

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
 * <p>The model matches what the obstacle logs measure on this server:
 * <ul>
 *   <li>The velocity read at the start of a tick, plus the input we add,
 *       is exactly the displacement the entity makes that tick.
 *   <li>Afterwards horizontal velocity is multiplied by {@code air_drag}
 *       (0.91) and vertical by {@code (vy - gravity) * vertical_drag}.
 * </ul>
 * Because horizontal travel is linear in both the starting velocity and a
 * constant per-tick input, the required input has a closed form; it is then
 * clamped to the vanilla air-acceleration budget so the result stays
 * physically legitimate.
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
     * height {@code landY}, with at most {@code maxAccel} of input per tick.
     * Controls both axes independently, so it also cancels sideways drift.
     */
    public static void steer(TickContext ctx, double tx, double tz, double landY, double maxAccel) {
        LivingEntity entity = ctx.entity();
        MovementConfig c = ctx.config;
        Vector vel = entity.getVelocity();
        double drag = c.getAirDrag();

        int n = ticksToLand(ctx.current.getY(), vel.getY(), landY, c);
        double dx = tx - ctx.current.getX();
        double dz = tz - ctx.current.getZ();

        double ax, az;
        if (n < 0) {
            // Landing above the reachable apex; nothing smarter to do than
            // drive straight at it.
            double len = Math.hypot(dx, dz);
            ax = len > 1e-6 ? dx / len * maxAccel : 0;
            az = len > 1e-6 ? dz / len * maxAccel : 0;
        } else {
            double unit = travel(0, 1, n, drag);
            ax = (dx - travel(vel.getX(), 0, n, drag)) / unit;
            az = (dz - travel(vel.getZ(), 0, n, drag)) / unit;
            double mag = Math.hypot(ax, az);
            if (mag > maxAccel) {
                ax *= maxAccel / mag;
                az *= maxAccel / mag;
            }
        }

        double fx = dx, fz = dz;
        double flen = Math.hypot(fx, fz);
        if (flen > 0.05) MovementExecutors.face(entity, new double[]{fx / flen, fz / flen});
        entity.setVelocity(new Vector(vel.getX() + ax, vel.getY(), vel.getZ() + az));
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
     *       hitbox only just clears the landing block's near edge (center
     *       0.8 short of the block center, minus a 0.2 safety margin).
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
        double minimum = (dist - 0.6 - authority) / perUnitV;
        return new double[]{Math.max(0.05, preferred), Math.max(0.0, minimum)};
    }
}
