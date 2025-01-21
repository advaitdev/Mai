package me.advait.mai.brain.action.event;

import me.advait.mai.body.Humanoid;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class HumanoidActionEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean isCancelled;

    private final Humanoid humanoid;

    public HumanoidActionEvent(Humanoid humanoid) {
        this.humanoid = humanoid;
    }

    public Humanoid getHumanoid() {
        return humanoid;
    }

    @Override
    public boolean isCancelled() {
        return isCancelled;
    }

    @Override
    public void setCancelled(boolean isCancelled) {
        this.isCancelled = isCancelled;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }


}
