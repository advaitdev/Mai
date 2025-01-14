package me.advait.mai.brain.action;

import me.advait.mai.Mai;
import me.advait.mai.body.Humanoid;
import me.advait.mai.brain.action.event.HumanoidActionEvent;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.concurrent.CompletableFuture;

public abstract class HumanoidAction {

    protected final Humanoid humanoid;

    protected final BukkitScheduler scheduler = Mai.getInstance().getServer().getScheduler();

    public HumanoidAction(final Humanoid humanoid) {
        this.humanoid = humanoid;
    }

    /**
     * The actual driver code for the action; checks that the event isn't cancelled first and then performs the action.
     */
    public CompletableFuture<HumanoidActionResult> run() {
        CompletableFuture<HumanoidActionResult> resultFuture = new CompletableFuture<>();

        callEvent(getEvent());

        if (isEventCancelled()) {
            resultFuture.complete(new HumanoidActionResult(false, "Action cancelled due to an external source—most likely this (or another) Spigot plugin."));
        }

        perform(resultFuture);
        return resultFuture;
    }

    /**
     * The logic code for the specific action.
     */
    protected abstract void perform(CompletableFuture<HumanoidActionResult> resultFuture);

    public static class HumanoidActionResult {

        private final boolean success;
        private String message;

        public HumanoidActionResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        @Override
        public String toString() {
            return (success ? "SUCCEEDED" : "FAILED") + ": " + message;
        }

    }

    public Humanoid getHumanoid() {
        return humanoid;
    }


    // Bukkit event code

    protected abstract HumanoidActionEvent getEvent();

    private void callEvent(HumanoidActionEvent event) {
        Mai.getInstance().getServer().getPluginManager().callEvent(event);
    }

    private boolean isEventCancelled() {
        Event event = getEvent();
        return event != null && ((Cancellable) event).isCancelled();
    }

}
