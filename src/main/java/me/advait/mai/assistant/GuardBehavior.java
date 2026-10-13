package me.advait.mai.assistant;

import me.advait.mai.body.Humanoid;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bodyguard: follow a player, and fight whatever threatens them: hostile
 * mobs that come within {@code assistant.guard.radius}, anyone who hits
 * the player, and anyone who hits the guard itself. Returns to following
 * when the threat is dead or has run off.
 */
public final class GuardBehavior implements Behavior {

    /** Last living attacker of each player / humanoid entity, with the tick it happened. */
    private static final Map<UUID, Attack> LAST_ATTACKER = new ConcurrentHashMap<>();
    private record Attack(LivingEntity attacker, long tick) {}

    private final Humanoid humanoid;
    private final Player owner;
    private final FollowBehavior follow;
    private CombatBehavior fight;
    private int scanCooldown = 0;
    private int kills = 0;

    public GuardBehavior(Humanoid humanoid, Player owner) {
        this.humanoid = humanoid;
        this.owner = owner;
        this.follow = new FollowBehavior(humanoid, owner, AssistantConfig.followDistance());
    }

    @Override
    public String name() { return "guard"; }

    @Override
    public String describe() {
        return "guarding " + owner.getName() + (fight != null ? ": " + fight.describe() : "")
                + (kills > 0 ? " (" + kills + " defeated)" : "");
    }

    @Override
    public boolean tick() {
        if (!owner.isOnline()) return false;
        LivingEntity bot = humanoid.getRawMannequin();
        if (bot == null || !bot.isValid()) return true;

        if (fight != null) {
            LivingEntity t = fight.target();
            boolean tooFar = t.isValid() && t.getWorld().equals(owner.getWorld())
                    && t.getLocation().distanceSquared(owner.getLocation()) > Math.pow(AssistantConfig.guardRadius() * 2, 2);
            if (!fight.tick() || tooFar) {
                if ("won".equals(fight.outcome())) kills++;
                fight.stop();
                fight = null;
            } else {
                return true;
            }
        }

        if (--scanCooldown <= 0) {
            scanCooldown = 5;
            LivingEntity threat = findThreat(bot);
            if (threat != null) {
                follow.stop();
                fight = new CombatBehavior(humanoid, threat, CombatBehavior.Style.SMART);
                return true;
            }
        }
        return follow.tick();
    }

    private LivingEntity findThreat(LivingEntity bot) {
        // Someone hit the owner or the guard in the last 10 seconds.
        for (UUID victim : new UUID[]{owner.getUniqueId(), bot.getUniqueId()}) {
            Attack a = LAST_ATTACKER.get(victim);
            if (a != null && org.bukkit.Bukkit.getCurrentTick() - a.tick() < 200 && a.attacker().isValid() && !a.attacker().isDead()
                    && !a.attacker().equals(owner) && !a.attacker().equals(bot)
                    && a.attacker().getWorld().equals(bot.getWorld())) {
                return a.attacker();
            }
        }
        double r = AssistantConfig.guardRadius();
        LivingEntity best = null;
        double bestD = r * r;
        for (Entity e : owner.getNearbyEntities(r, r / 2, r)) {
            if (!(e instanceof Enemy) || !(e instanceof LivingEntity le) || le.isDead()) continue;
            if (!bot.hasLineOfSight(le) && !owner.hasLineOfSight(le)) continue;
            double d = le.getLocation().distanceSquared(owner.getLocation());
            if (d < bestD) { bestD = d; best = le; }
        }
        return best;
    }

    @Override
    public void stop() {
        follow.stop();
        if (fight != null) fight.stop();
    }

    /** Records who hit whom, so guards can retaliate. */
    public static final class DamageListener implements Listener {
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onDamage(EntityDamageByEntityEvent event) {
            Entity damager = event.getDamager();
            if (damager instanceof Projectile p && p.getShooter() instanceof LivingEntity shooter) {
                damager = shooter;
            }
            if (damager instanceof LivingEntity attacker) {
                LAST_ATTACKER.put(event.getEntity().getUniqueId(), new Attack(attacker, org.bukkit.Bukkit.getCurrentTick()));
            }
        }
    }
}
