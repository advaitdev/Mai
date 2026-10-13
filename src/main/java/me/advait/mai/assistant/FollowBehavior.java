package me.advait.mai.assistant;

import me.advait.mai.body.Humanoid;
import me.advait.mai.pathetic.PatheticAgent;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Stay near a moving entity. Pathfinds (with retargeting) when it falls
 * behind, stands and watches when close, and, like a tamed wolf, teleports
 * when the leader gets very far away or changes world (configurable; set
 * {@code assistant.follow.teleport_distance} to 0 to walk everywhere).
 */
public final class FollowBehavior implements Behavior {

    private final Humanoid humanoid;
    private final Entity leader;
    private final double distance;
    private final Navigator nav;
    private double lastDist = 0;

    public FollowBehavior(Humanoid humanoid, Entity leader, double distance) {
        this.humanoid = humanoid;
        this.leader = leader;
        this.distance = distance;
        this.nav = new Navigator(humanoid);
    }

    public Entity leader() { return leader; }

    @Override
    public String name() { return "follow"; }

    @Override
    public String describe() {
        return "following " + leader.getName() + String.format(" (%.1f blocks)", lastDist)
                + (nav.lastFailure() != null && !nav.isMoving() ? " - can't reach: " + nav.lastFailure() : "");
    }

    @Override
    public boolean tick() {
        if (!leader.isValid() || leader.isDead()
                || (leader instanceof Player p && !p.isOnline())) {
            Assistant.of(humanoid).say("I lost track of " + leader.getName() + ".");
            return false;
        }
        LivingEntity bot = humanoid.getEntity();
        if (bot == null) return false;
        nav.tick();

        Location here = bot.getLocation();
        Location there = leader.getLocation();
        double tp = AssistantConfig.followTeleportDistance();
        if (!here.getWorld().equals(there.getWorld())
                || (tp > 0 && here.distanceSquared(there) > tp * tp)) {
            nav.stop();
            Location dest = landingSpot(there);
            bot.teleport(dest != null ? dest : there);
            return true;
        }

        lastDist = here.distance(there);
        if (lastDist > distance + 1.0) {
            Location goal = landingSpot(there);
            nav.moveTo(goal != null ? goal : there, distance);
        } else if (!nav.isMoving()) {
            Motor.look(bot, leader);
        }
        return true;
    }

    @Override
    public void stop() {
        nav.stop();
    }

    /**
     * Where a target that is jumping, flying or falling will stand: the first
     * standable cell at or below it (up to 6 blocks). Pathing toward the air
     * cell itself would fail every time the leader jumps.
     */
    static Location landingSpot(Location at) {
        Location probe = PatheticAgent.feetCell(at);
        for (int dy = 0; dy <= 6; dy++) {
            Location c = probe.clone().add(0, -dy, 0);
            if (PatheticAgent.getInstance().isStandable(c)) return c;
        }
        return null;
    }
}
