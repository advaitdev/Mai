package me.advait.mai.assistant;

import me.advait.mai.body.Body;
import me.advait.mai.pathetic.config.MovementConfig;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

/**
 * Direct body control for behaviors that steer without a path (combat
 * footwork, closing the last few blocks on a target). Everything goes
 * through {@link Body}: the bot faces a point and presses W/A/S/D, jump and
 * sprint like a player, and vanilla physics turns that into motion.
 */
public final class Motor {

    private Motor() {}

    /**
     * One tick of WASD input with the view toward {@code facing}.
     * {@code forward}/{@code strafe} pick keys by sign ({@code forward > 0}
     * = W, {@code strafe > 0} = D); a player's keys have no half-presses.
     */
    public static void drive(LivingEntity e, Location facing, double forward, double strafe,
                             boolean sprint, MovementConfig cfg) {
        Location cur = e.getLocation();
        double fx = facing.getX() - cur.getX(), fz = facing.getZ() - cur.getZ();
        if (fx * fx + fz * fz < 1e-8) return;
        Body.of(e).face(fx, fz)
                .keys(key(forward), key(strafe))
                .sprint(sprint);
    }

    private static int key(double axis) {
        return axis > 0.05 ? 1 : axis < -0.05 ? -1 : 0;
    }

    /** Press jump if standing. Sprinting adds the vanilla sprint-jump boost on its own. */
    public static boolean jump(LivingEntity e, boolean sprintBoost, MovementConfig cfg) {
        if (!e.isOnGround()) return false;
        Body.of(e).jump();
        return true;
    }

    /** Turn head and body toward a point (yaw and pitch). */
    public static void look(LivingEntity e, Location at) {
        Location eye = e.getEyeLocation();
        double dx = at.getX() - eye.getX(), dy = at.getY() - eye.getY(), dz = at.getZ() - eye.getZ();
        double h = Math.sqrt(dx * dx + dz * dz);
        float yaw = Body.yawOf(dx, dz);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, h));
        Body.of(e).look(yaw, pitch);
    }

    /** Look at the middle of another entity's body. */
    public static void look(LivingEntity e, Entity target) {
        Location at = target.getLocation().add(0, target.getHeight() * 0.85, 0);
        look(e, at);
    }

    /**
     * Distance from {@code e}'s eyes to the nearest point of {@code target}'s
     * hitbox: the quantity vanilla's 3-block attack reach is measured on.
     */
    public static double reachDistance(LivingEntity e, Entity target) {
        Location eye = e.getEyeLocation();
        var box = target.getBoundingBox();
        double cx = Math.max(box.getMinX(), Math.min(eye.getX(), box.getMaxX()));
        double cy = Math.max(box.getMinY(), Math.min(eye.getY(), box.getMaxY()));
        double cz = Math.max(box.getMinZ(), Math.min(eye.getZ(), box.getMaxZ()));
        double dx = eye.getX() - cx, dy = eye.getY() - cy, dz = eye.getZ() - cz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static double horizontalDistance(Location a, Location b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Whether stepping a little toward the given side would walk the bot
     * off a drop of more than 2 blocks (or into the void). Footwork must
     * never strafe off a ledge.
     */
    public static boolean unsafeStep(LivingEntity e, Location facing, double strafe, double forward) {
        Location cur = e.getLocation();
        double fx = facing.getX() - cur.getX(), fz = facing.getZ() - cur.getZ();
        double len = Math.sqrt(fx * fx + fz * fz);
        if (len < 1e-4) return false;
        fx /= len; fz /= len;
        double ix = fx * forward - fz * strafe, iz = fz * forward + fx * strafe;
        double ilen = Math.sqrt(ix * ix + iz * iz);
        if (ilen < 1e-4) return false;
        double px = cur.getX() + ix / ilen * 0.9, pz = cur.getZ() + iz / ilen * 0.9;
        int bx = (int) Math.floor(px), bz = (int) Math.floor(pz);
        int by = (int) Math.floor(cur.getY() - 0.01);
        for (int d = 0; d <= 2; d++) {
            var b = cur.getWorld().getBlockAt(bx, by - d, bz);
            if (b.getType().isSolid()) return false;
            if (b.isLiquid()) return b.getType() == org.bukkit.Material.LAVA;
        }
        return true;
    }

    /**
     * Whether there is a drop of more than 3 blocks (or void) within
     * {@code dist} blocks of the entity along the horizontal direction
     * {@code (dx, dz)}. Used to keep fighters from being knocked off edges.
     */
    public static boolean dropWithin(LivingEntity e, double dx, double dz, double dist) {
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-4) return false;
        dx /= len; dz /= len;
        Location cur = e.getLocation();
        int by = (int) Math.floor(cur.getY() - 0.01);
        for (double d = 1.0; d <= dist; d += 1.0) {
            int bx = (int) Math.floor(cur.getX() + dx * d), bz = (int) Math.floor(cur.getZ() + dz * d);
            boolean ground = false;
            for (int k = 0; k <= 3 && !ground; k++) {
                var b = cur.getWorld().getBlockAt(bx, by - k, bz);
                ground = b.getType().isSolid() || b.getType() == org.bukkit.Material.WATER;
            }
            if (!ground) return true;
        }
        return false;
    }
}
