package me.advait.mai.brain.action;

import me.advait.mai.Mai;
import me.advait.mai.body.Humanoid;
import me.advait.mai.brain.action.event.HumanoidActionEvent;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;

public abstract class HumanoidAction {

    private final Humanoid humanoid;

    public HumanoidAction(final Humanoid humanoid) {
        this.humanoid = humanoid;
    }

    /**
     * The actual driver code for the action; checks that the event isn't cancelled first and then performs the action.
     */
    public void run() {
        callEvent(getEvent());

        if (isEventCancelled()) return;

        perform();
    }

    /**
     * The logic code for the specific action.
     */
    protected abstract void perform();

    protected abstract HumanoidActionEvent getEvent();

    private void callEvent(HumanoidActionEvent event) {
        Mai.getInstance().getServer().getPluginManager().callEvent(event);
    }

    private boolean isEventCancelled() {
        Event event = getEvent();
        return event != null && ((Cancellable) event).isCancelled();
    }

    public Humanoid getHumanoid() {
        return humanoid;
    }

}
