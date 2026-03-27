package me.advait.mai.brain.action.mechanic;

import me.advait.mai.Mai;
import me.advait.mai.body.Humanoid;
import me.advait.mai.brain.action.event.HumanoidActionEvent;
import me.advait.mai.brain.action.result.HumanoidActionResult;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public abstract class HumanoidAction {

    protected final Humanoid humanoid;
    protected final BukkitScheduler scheduler = Mai.getInstance().getServer().getScheduler();

    public HumanoidAction(Humanoid humanoid) {
        this.humanoid = humanoid;
    }

    public CompletableFuture<HumanoidActionResult> run() {
        CompletableFuture<HumanoidActionResult> resultFuture = new CompletableFuture<>();

        HumanoidActionEvent event = createEvent();
        if (event != null) {
            Mai.getInstance().getServer().getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                resultFuture.complete(new HumanoidActionResult(false, "Action cancelled by an external plugin."));
                return resultFuture;
            }
        }

        perform(resultFuture);
        return resultFuture;
    }

    protected abstract void perform(CompletableFuture<HumanoidActionResult> resultFuture);

    /**
     * Override to fire a Bukkit event before the action runs.
     * Return null (the default) to skip event firing.
     */
    protected HumanoidActionEvent createEvent() {
        return null;
    }

    public Humanoid getHumanoid() {
        return humanoid;
    }

    /**
     * Returns actions that cannot run in parallel with this one (e.g. can't block and swing simultaneously).
     */
    public Set<Class<? extends HumanoidAction>> incompatibleWith() {
        return Set.of();
    }
}
