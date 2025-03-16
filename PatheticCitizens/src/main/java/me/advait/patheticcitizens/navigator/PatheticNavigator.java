package me.advait.patheticcitizens.navigator;

import de.metaphoriker.pathetic.api.pathing.result.Path;
import de.metaphoriker.pathetic.api.pathing.result.PathfinderResult;
import de.metaphoriker.pathetic.api.wrapper.PathPosition;
import de.metaphoriker.pathetic.bukkit.mapper.BukkitMapper;
import me.advait.patheticcitizens.PatheticCitizens;
import me.advait.patheticcitizens.npc.PatheticNPC;
import me.advait.patheticcitizens.pathfinder.PatheticAgent;
import me.advait.patheticcitizens.util.PatheticUtil;
import net.citizensnpcs.api.NMSHelper;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.npc.ai.CitizensNavigator;
import net.citizensnpcs.util.NMS;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;

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

        scheduler.runTaskTimer(PatheticCitizens.getInstance(), task -> {
            if (navigationStrategy.arrived()) task.cancel();

            else {
                // TODO: We're running Pathetic every tick, is this necessary?
                var groundPathResult = AGENT.getGroundPath(npc.getLocation(), target);

                Deque<Location> locationQueue = new ArrayDeque<>();
                groundPathResult.thenAccept(result -> {

                    if (result.successful()) {
                        Path path = result.getPath();

                        for (PathPosition pathPosition : path) {
                            Location bukkitLocation = BukkitMapper.toLocation(pathPosition);
                            locationQueue.add(bukkitLocation);
                            for (Player player : Bukkit.getOnlinePlayers()) PatheticUtil.sendDebugPath(player, bukkitLocation);
                        }

                        navigationStrategy.setPath(locationQueue);
                    }

                    navigationStrategy.tick();
                });
            }
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
