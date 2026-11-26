package me.advait.mai.assistant;

import org.bukkit.Location;

/**
 * Where to path toward a moving target: the cell it stands on (or will land
 * on, {@link FollowBehavior#landingSpot}), else, while it is in the air over
 * a gap with nothing below, the last cell it stood on. Pathing at the air
 * cell itself is refused as unreachable, and each refusal cost the chaser a
 * second of standing still every time the target jumped a gap.
 */
final class GroundTracker {

    private Location last;

    Location resolve(Location at) {
        Location spot = FollowBehavior.landingSpot(at);
        if (spot != null && at.getY() - spot.getY() < HIGH) {
            last = spot;
            return spot;
        }
        if (spot == null && last != null && last.getWorld() == at.getWorld()) return last;
        Location near = nearestStandable(at);
        if (spot == null) return near != null ? near : at;
        // Well above the ground under it: jumping between raised blocks, or
        // falling. A block beside it that is nearer than the ground is the
        // takeoff or landing of its jump; chasing the ground below sent the
        // hunter along the floor under a pillar course (64 ticks late).
        if (near != null && near.clone().add(0.5, 0, 0.5).distanceSquared(at)
                < spot.clone().add(0.5, 0, 0.5).distanceSquared(at)) return near;
        last = spot;
        return spot;
    }

    /** Height above the ground below from which a target counts as in the air over it, not jumping on it. */
    private static final double HIGH = 1.75;

    /**
     * The nearest standable cell beside or below a target in the air (the
     * takeoff or landing of the jump it is in).
     */
    private static Location nearestStandable(Location at) {
        Location feet = me.advait.mai.pathetic.PatheticAgent.feetCell(at);
        Location best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 1; dy >= -6; dy--) {
                    Location c = feet.clone().add(dx, dy, dz);
                    if (!me.advait.mai.pathetic.PatheticAgent.getInstance().isStandable(c)) continue;
                    double d = c.clone().add(0.5, 0, 0.5).distanceSquared(at);
                    if (d < bestD) { bestD = d; best = c; }
                    break;
                }
            }
        }
        return best;
    }
}
