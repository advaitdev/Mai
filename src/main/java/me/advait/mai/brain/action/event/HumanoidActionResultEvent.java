package me.advait.mai.brain.action.event;

import me.advait.mai.body.Humanoid;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class HumanoidActionResultEvent extends Event  {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Humanoid humanoid;
    private final Class<?> resultClassOrigin;

    public HumanoidActionResultEvent(Humanoid humanoid, Class<?> resultClassOrigin) {
        this.humanoid = humanoid;
        this.resultClassOrigin = resultClassOrigin;
    }

    public Humanoid getHumanoid() {
        return humanoid;
    }

    public Class<?> getResultClassOrigin() {
        return resultClassOrigin;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

}
