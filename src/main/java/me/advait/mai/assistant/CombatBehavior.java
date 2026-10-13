package me.advait.mai.assistant;

import me.advait.mai.body.Humanoid;
import me.advait.mai.pathetic.PatheticAgent;
import me.advait.mai.pathetic.config.MovementConfig;
import me.advait.mai.util.ItemUtil;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Melee fight against one target, within vanilla rules.
 *
 * <p>Far away (or out of sight) it pathfinds toward the target. Up close it
 * steps directly, every tick:
 * <ul>
 *   <li>always faces the target and only swings inside the 3-block reach
 *       (eyes to hitbox), at the weapon's real attack-speed cooldown;
 *       damage scales with charge like vanilla ({@code 0.2 + 0.8c²});</li>
 *   <li>holds a preferred range and strafes, switching side at random and
 *       never strafing off a ledge;</li>
 *   <li>times critical hits: jumps when the charge is nearly full and
 *       swings on the way down (x1.5, no sprinting, like vanilla);</li>
 *   <li>otherwise sprint-hits for extra knockback, then releases sprint for
 *       two ticks (a "w-tap") so the next hit gets sprint knockback again.</li>
 * </ul>
 *
 * <p>{@link Style#BASIC} is a deliberately naive fighter (run straight at
 * the target, click whenever in reach) used as the baseline in duels.
 */
public final class CombatBehavior implements Behavior {

    public enum Style { SMART, BASIC }

    /** When on, every fighter logs its per-tick decision state (grep "[Combat]"). */
    public static volatile boolean trace = false;

    private final Humanoid humanoid;
    private final LivingEntity target;
    private final Style style;
    private final Navigator nav;

    private int ticks = 0;
    private int ticksSinceAttack = 100;
    private int sprintBlock = 0;
    private int strafeDir = 1;
    private int strafeTimer = 0;
    private boolean critArmed = false;
    private boolean sprinting = false;
    private int lostTicks = 0;
    private double prevReach = Double.NaN;
    private Location prevTargetLoc = null;
    /** A 0.42 jump starts falling after ~6 ticks. */
    private static final int TICKS_TO_FALL = 6;

    // Stats, for status lines and duel reports.
    private int swings = 0, hits = 0, crits = 0, sprintHits = 0;
    private double damageDealt = 0;
    private String outcome = null;

    public CombatBehavior(Humanoid humanoid, LivingEntity target, Style style) {
        this.humanoid = humanoid;
        this.target = target;
        this.style = style;
        this.nav = new Navigator(humanoid);
    }

    public LivingEntity target() { return target; }
    public int swings() { return swings; }
    public int hits() { return hits; }
    public int crits() { return crits; }
    public int sprintHits() { return sprintHits; }
    public double damageDealt() { return damageDealt; }
    /** Why the fight ended (won, died, lost, ...), or null while running. */
    public String outcome() { return outcome; }

    @Override
    public String name() { return "attack"; }

    @Override
    public String describe() {
        return String.format("fighting %s (%.0f hp left, %d hits, %d crits)",
                target.getName(), target.getHealth(), hits, crits);
    }

    @Override
    public boolean tick() {
        LivingEntity bot = humanoid.getRawMannequin();
        if (bot == null || !bot.isValid() || bot.isDead()) return end("died");
        if (!target.isValid() || target.isDead()) return end("won");
        if (target instanceof Player p && (!p.isOnline()
                || p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR)) {
            return end("target_unattackable");
        }
        if (!bot.getWorld().equals(target.getWorld())) return end("lost");

        ticks++;
        ticksSinceAttack++;
        if (sprintBlock > 0) sprintBlock--;
        nav.tick();
        MovementConfig cfg = PatheticAgent.getInstance().getConfig();

        Location here = bot.getLocation();
        Location there = target.getLocation();
        double hd = Motor.horizontalDistance(here, there);
        double dy = there.getY() - here.getY();
        lostTicks = hd > 48 ? lostTicks + 1 : 0;
        if (lostTicks > 100) return end("lost");

        // How fast the target is coming at us, from its displacement (a
        // player's server-side velocity is mostly empty, positions are not).
        double targetToward = 0;
        if (prevTargetLoc != null && prevTargetLoc.getWorld() == there.getWorld() && hd > 0.01) {
            double ux = (here.getX() - there.getX()) / hd, uz = (here.getZ() - there.getZ()) / hd;
            targetToward = (there.getX() - prevTargetLoc.getX()) * ux + (there.getZ() - prevTargetLoc.getZ()) * uz;
        }
        prevTargetLoc = there.clone();

        boolean sight = bot.hasLineOfSight(target);
        if (trace) {
            me.advait.mai.Mai.getInstance().getLogger().info(String.format(
                    "[Combat] %s t=%d hd=%.2f dy=%.2f reach=%.2f ground=%s vy=%.3f sight=%s since=%d armed=%s hp=%.1f",
                    humanoid.getName(), ticks, hd, dy, Motor.reachDistance(bot, target), bot.isOnGround(),
                    bot.getVelocity().getY(), sight, ticksSinceAttack, critArmed, bot.getHealth()));
        }
        if (hd > AssistantConfig.chaseDirectRange() || !sight || (Math.abs(dy) > 1.5 && hd > 2.5)) {
            Location goal = FollowBehavior.landingSpot(there);
            nav.moveTo(goal != null ? goal : there, 1.5);
            return true;
        }
        nav.stop();
        Motor.look(bot, target);

        ItemStack weapon = bot.getEquipment() != null ? bot.getEquipment().getItemInMainHand() : null;
        double period = 20.0 / ItemUtil.getAttackSpeed(weapon);
        double charge = Math.min(1.0, (ticksSinceAttack + 0.5) / period);
        double reachMax = AssistantConfig.reach();
        double reach = Motor.reachDistance(bot, target);
        boolean onGround = bot.isOnGround();

        if (style == Style.BASIC) {
            sprinting = hd > 1.2;
            Motor.drive(bot, there, hd > 1.2 ? 1 : 0, 0, sprinting, cfg);
            if (reach <= reachMax && ticksSinceAttack >= 4) attack(bot, weapon, charge, false, sprinting);
            return true;
        }

        // Footwork: hold the preferred range, strafe around the target.
        double range = AssistantConfig.preferredRange();
        double forward = hd > range + 0.4 ? 1 : (hd < range - 0.8 ? -0.7 : 0);
        // Charged and the target is charging in: hold ground and let them
        // walk into a timed crit instead of meeting them in an even trade.
        // Back to an edge: knockback would carry us off, so don't give ground.
        boolean edgeBehind = Motor.dropWithin(bot, here.getX() - there.getX(), here.getZ() - there.getZ(), 4);
        if (edgeBehind) forward = Math.max(forward, hd > 1.5 ? 1 : 0);
        boolean letThemCome = !edgeBehind && AssistantConfig.approachCrits() && targetToward > 0.12
                && ticksSinceAttack + TICKS_TO_FALL >= period && reach > reachMax;
        if (letThemCome && forward > 0) forward = 0;
        double strafe = 0;
        if (AssistantConfig.strafe()) {
            if (--strafeTimer <= 0) {
                if (ThreadLocalRandom.current().nextDouble() < 0.6) strafeDir = -strafeDir;
                strafeTimer = 12 + ThreadLocalRandom.current().nextInt(20);
            }
            strafe = 0.8 * strafeDir;
            // Strafe away from a nearby edge on either side.
            double sx = -(there.getZ() - here.getZ()) * strafeDir, sz = (there.getX() - here.getX()) * strafeDir;
            if (Motor.dropWithin(bot, sx, sz, 3)) {
                strafeDir = -strafeDir;
                strafe = -strafe;
            }
            if (Motor.unsafeStep(bot, there, strafe, forward)) {
                strafeDir = -strafeDir;
                strafe = -strafe;
                if (Motor.unsafeStep(bot, there, strafe, forward)) strafe = 0;
            }
        }
        if (forward != 0 && Motor.unsafeStep(bot, there, 0, forward)) forward = 0;

        boolean charged = charge >= AssistantConfig.minCharge();
        boolean inReach = reach <= reachMax;

        // Crits. Reach is symmetric: the tick we come into range, so does the
        // opponent, and a full-charge trade is a coin flip. What wins trades
        // is making our entry hit a x1.5 crit, so time the hop from the
        // measured closing speed: start falling as reach drops to 3. Also hop
        // while recharging in range (no swing possible then anyway).
        double closing = Double.isNaN(prevReach) ? 0 : Math.max(0, prevReach - reach);
        prevReach = reach;
        boolean jumpedNow = false;
        double chargeAtFall = Math.min(1.0, (ticksSinceAttack + TICKS_TO_FALL + 0.5) / period);
        if (AssistantConfig.crits() && onGround && !critArmed && Math.abs(dy) < 1.0
                && chargeAtFall >= 0.9 && !(charged && inReach)) {
            boolean jumpBoost = forward > 0 && sprinting;
            // Ticks until we come into reach at the current closing speed (plus
            // the jump boost). A jump falls from ~5 ticks after takeoff until
            // it lands at ~11. Aim for the late fall: high in the arc our eyes
            // are above the target, so our reach to it is longer than its
            // reach to us and it would hit first.
            double speed = Math.max(closing, targetToward) + (jumpBoost ? 0.1 : 0);
            double entry = speed > 0.05 ? (reach - (reachMax - 0.1)) / speed : Double.MAX_VALUE;
            boolean approachHop = AssistantConfig.approachCrits() && !inReach && entry >= 7 && entry <= 10;
            boolean rechargeHop = inReach && !charged;
            if (approachHop || rechargeHop) {
                jumpedNow = Motor.jump(bot, jumpBoost, cfg);
                critArmed = jumpedNow;
            }
        }
        if (onGround && !jumpedNow) critArmed = false;

        sprinting = forward > 0 && sprintBlock == 0 && !critArmed;
        Motor.drive(bot, there, forward, strafe, sprinting, cfg);

        if (inReach && charged) {
            // Never hold a ready swing waiting for the drop: the opponent is
            // in range too and would take the hit first.
            boolean falling = !onGround && bot.getVelocity().getY() < 0;
            boolean crit = falling && !sprinting;
            attack(bot, weapon, charge, crit, sprinting);
        }
        return true;
    }

    private void attack(LivingEntity bot, ItemStack weapon, double charge, boolean crit, boolean sprintHit) {
        swings++;
        ticksSinceAttack = 0;
        bot.swingMainHand();

        double dmg = ItemUtil.getWeaponDamage(weapon) * (0.2 + charge * charge * 0.8);
        if (crit) dmg *= 1.5;
        dmg += ItemUtil.getEnchantDamage(weapon) * charge;

        double before = target.getHealth();
        target.damage(dmg, bot);
        boolean landed = target.getHealth() < before || target.isDead();
        Location at = target.getLocation();
        if (!landed) {
            bot.getWorld().playSound(at, Sound.ENTITY_PLAYER_ATTACK_NODAMAGE, 1f, 1f);
            return;
        }
        hits++;
        damageDealt += before - Math.max(0, target.getHealth());

        boolean strong = charge > 0.9;
        if (crit) {
            crits++;
            bot.getWorld().playSound(at, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1f);
            bot.getWorld().spawnParticle(Particle.CRIT, at.clone().add(0, target.getHeight() * 0.6, 0), 12, 0.3, 0.3, 0.3, 0.1);
        } else if (sprintHit && strong) {
            sprintHits++;
            bot.getWorld().playSound(at, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 1f);
            // Vanilla sprint-hit: extra 0.5 knockback, attacker slowed to 60%.
            target.knockback(0.5, bot.getLocation().getX() - at.getX(), bot.getLocation().getZ() - at.getZ());
            Vector v = bot.getVelocity();
            bot.setVelocity(new Vector(v.getX() * 0.6, v.getY(), v.getZ() * 0.6));
            if (style == Style.SMART && AssistantConfig.wTap()) sprintBlock = 2;
        } else {
            bot.getWorld().playSound(at, strong ? Sound.ENTITY_PLAYER_ATTACK_STRONG : Sound.ENTITY_PLAYER_ATTACK_WEAK, 1f, 1f);
        }
    }

    private boolean end(String why) {
        outcome = why;
        return false;
    }

    @Override
    public void stop() {
        nav.stop();
        if (outcome == null) outcome = "stopped";
    }
}
