package me.advait.mai.assistant;

import me.advait.mai.Catalog;
import me.advait.mai.Mai;
import me.advait.mai.body.Humanoid;
import me.advait.mai.brain.action.mechanic.HumanoidAction;
import me.advait.mai.brain.action.result.HumanoidActionResult;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The per-humanoid "will": what it is doing right now and how to change
 * that. Commands, chat and (later) an LLM all go through here or through
 * {@link me.advait.mai.assistant.tool.Tools}, never straight to actions, so
 * there is exactly one place that decides that a new job replaces the old.
 *
 * <p>A humanoid is doing at most one of: a {@link Behavior} (follow, guard,
 * fight; runs until stopped) or a queue of one-shot actions (walk, mine,
 * place). Starting either cancels the other.
 */
public final class Assistant {

    private static final Map<UUID, Assistant> ALL = new ConcurrentHashMap<>();

    public static Assistant of(Humanoid humanoid) {
        return ALL.computeIfAbsent(humanoid.getUuid(), id -> new Assistant(humanoid));
    }

    /** Drop state for a humanoid that no longer exists. */
    public static void forget(UUID humanoidId) {
        Assistant a = ALL.remove(humanoidId);
        if (a != null) a.stopBehavior();
    }

    /** Starts the shared ticker. Call once on enable. */
    public static void startTicker() {
        Bukkit.getScheduler().runTaskTimer(Mai.getInstance(), () -> {
            // Shuffled so no humanoid always acts first within a tick (in a
            // duel, acting first means swinging first).
            java.util.List<Assistant> order = new java.util.ArrayList<>(ALL.values());
            java.util.Collections.shuffle(order);
            for (Assistant a : order) {
                if (Catalog.getInstance().getByUuid(a.humanoid.getUuid()) == null) {
                    forget(a.humanoid.getUuid());
                    continue;
                }
                a.tick();
            }
        }, 1L, 1L);
    }

    private final Humanoid humanoid;
    private Behavior behavior;
    private String lastJob = "idle";
    private int idleLookCooldown = 0;

    private Assistant(Humanoid humanoid) {
        this.humanoid = humanoid;
    }

    public Humanoid humanoid() { return humanoid; }

    private void tick() {
        LivingEntity e = humanoid.getRawMannequin();
        if (e == null || !e.isValid()) return;
        if (behavior != null) {
            boolean keep;
            try {
                keep = behavior.tick();
            } catch (RuntimeException ex) {
                Mai.getInstance().getLogger().warning("Behavior " + behavior.name() + " on "
                        + humanoid.getName() + " crashed: " + ex);
                keep = false;
            }
            if (!keep) stopBehavior();
            return;
        }
        // Idle: turn toward the nearest player now and then, so it reads as
        // present rather than a statue. Purely cosmetic.
        if (!humanoid.getActionAgent().isProcessing() && --idleLookCooldown <= 0) {
            idleLookCooldown = 10;
            Player near = nearestPlayer(e, 8);
            if (near != null) Motor.look(e, near.getEyeLocation());
        }
    }

    /** Replace whatever the humanoid is doing with {@code b}. */
    public void setBehavior(Behavior b) {
        stopBehavior();
        humanoid.getActionAgent().cancelAll();
        behavior = b;
        lastJob = b.name();
    }

    /** Queue one-shot actions, ending any running behavior first. */
    public CompletableFuture<HumanoidActionResult> run(String jobName, HumanoidAction... actions) {
        stopBehavior();
        lastJob = jobName;
        return humanoid.getActionAgent().addActions(actions);
    }

    /** Stop everything: behavior, current action and queue. */
    public void stopAll() {
        stopBehavior();
        humanoid.getActionAgent().cancelAll();
        lastJob = "idle";
    }

    private void stopBehavior() {
        Behavior b = behavior;
        behavior = null;
        if (b != null) b.stop();
    }

    public Behavior behavior() { return behavior; }

    /** Human-readable current activity. */
    public String activity() {
        if (behavior != null) return behavior.describe();
        String action = humanoid.getActionAgent().getCurrentActionName();
        if (action != null) {
            int queued = humanoid.getActionAgent().getQueuedActionNames().size();
            return lastJob + " (" + action + (queued > 0 ? ", " + queued + " queued" : "") + ")";
        }
        return "idle";
    }

    /** Say something in chat, as the humanoid, to players within 64 blocks. */
    public void say(String message) {
        LivingEntity e = humanoid.getRawMannequin();
        Component line = Component.text("<" + humanoid.getName() + "> ", NamedTextColor.AQUA)
                .append(Component.text(message, NamedTextColor.WHITE));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (e == null || !p.getWorld().equals(e.getWorld())
                    || p.getLocation().distanceSquared(e.getLocation()) <= 64 * 64) {
                p.sendMessage(line);
            }
        }
        Mai.getInstance().getLogger().info("<" + humanoid.getName() + "> " + message);
    }

    static Player nearestPlayer(LivingEntity e, double radius) {
        Player best = null;
        double bestD = radius * radius;
        for (Player p : e.getWorld().getPlayers()) {
            if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            double d = p.getLocation().distanceSquared(e.getLocation());
            if (d < bestD) { bestD = d; best = p; }
        }
        return best;
    }
}
