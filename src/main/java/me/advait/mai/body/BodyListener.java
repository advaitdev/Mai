package me.advait.mai.body;

import com.destroystokyo.paper.event.server.ServerTickStartEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/** Releases every body's keys at the start of each server tick (see {@link Body}). */
public final class BodyListener implements Listener {

    @EventHandler
    public void onTickStart(ServerTickStartEvent event) {
        Body.releaseAll();
    }
}
