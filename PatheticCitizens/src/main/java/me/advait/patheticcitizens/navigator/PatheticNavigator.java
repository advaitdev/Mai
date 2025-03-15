package me.advait.patheticcitizens.navigator;

import de.metaphoriker.pathetic.api.pathing.result.PathfinderResult;
import de.metaphoriker.pathetic.api.wrapper.PathPosition;
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
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.CompletableFuture;

public final class PatheticNavigator {

    private final PatheticNPC npc;
    private final PatheticAgent AGENT = PatheticAgent.getInstance();

    private final BukkitScheduler scheduler = Bukkit.getScheduler();
    private BukkitTask currentTask;

    private boolean isNavigating = false;

    public PatheticNavigator(PatheticNPC npc) {
        this.npc = npc;
    }

    public void setWalkableTarget(Location target) {
        var groundPath = AGENT.getGroundPath(npc.getLocation(), target);
        System.out.println("Found the ground path.");

        PatheticUtil.toLocationQueue(groundPath).forEach(location -> System.out.println(location));

        PatheticNavigationStrategy navigationStrategy = new PatheticNavigationStrategy(
                npc,
                this,
                PatheticUtil.toLocationQueue(groundPath));

        this.currentTask = scheduler.runTaskTimer(PatheticCitizens.getInstance(), () -> {
            if (navigationStrategy.arrived()) currentTask.cancel();
            else {
                navigationStrategy.tick();
            }
        }, 0, 10L);

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
