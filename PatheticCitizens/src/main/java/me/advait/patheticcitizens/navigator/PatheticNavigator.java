package me.advait.patheticcitizens.navigator;

import de.metaphoriker.pathetic.api.pathing.result.Path;
import de.metaphoriker.pathetic.api.wrapper.PathPosition;
import de.metaphoriker.pathetic.bukkit.mapper.BukkitMapper;
import me.advait.patheticcitizens.PatheticCitizens;
import me.advait.patheticcitizens.npc.PatheticNPC;
import me.advait.patheticcitizens.pathfinder.PatheticAgent;
import me.advait.patheticcitizens.util.PatheticUtil;
import net.citizensnpcs.npc.ai.CitizensNavigator;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;

public final class PatheticNavigator {

    private final PatheticNPC npc;
    private final PatheticAgent AGENT = PatheticAgent.getInstance();

    private final BukkitScheduler scheduler = Bukkit.getScheduler();

    private boolean isNavigating = false;

    public PatheticNavigator(PatheticNPC npc) {
        this.npc = npc;
    }

    public void setWalkableTarget(Location target) {
        PatheticNavigationStrategy navigationStrategy = new PatheticNavigationStrategy(
                npc,
                this,
                null,
                target,
                1F);

        int PATHETIC_ITERATIONS = 1;  // Pathetic will run every 1 tick
        AtomicInteger CURRENT_ITERATION = new AtomicInteger();
        CURRENT_ITERATION.set(PATHETIC_ITERATIONS);

        scheduler.runTaskTimer(PatheticCitizens.getInstance(), task -> {
            if (navigationStrategy.arrived()) {
                Bukkit.broadcast(Component.text("Arrived!"));
                task.cancel();
                return;
            }

            if (CURRENT_ITERATION.get() == PATHETIC_ITERATIONS) {
                var groundPathResult = AGENT.getGroundPath(npc.getLocation(), target);

                Deque<Location> locationQueue = new ArrayDeque<>();
                groundPathResult.thenAccept(result -> {

                    if (result.successful()) {
                        Path path = result.getPath();

                        for (PathPosition pathPosition : path) {
                            Location bukkitLocation = BukkitMapper.toLocation(pathPosition);
                            locationQueue.add(bukkitLocation);

                            for (Player player : Bukkit.getOnlinePlayers()) {
                                PatheticUtil.sendDebugPath(player, bukkitLocation);
                            }
                        }

                        navigationStrategy.setPath(locationQueue);
                        CURRENT_ITERATION.set(0);
                    }
                });
            }

            else {
                CURRENT_ITERATION.getAndIncrement();
            }

            navigationStrategy.tick();

        }, 0, 1);

    }

    public boolean isNavigating() {
        return isNavigating;
    }

    public void setNavigating(boolean navigating) {
        isNavigating = navigating;
    }

    public void setBuildableTarget(Location target) {
        // TODO
    }

    public void setRawTarget(Location target) {
        // TODO
    }

}
