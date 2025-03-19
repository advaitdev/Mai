package me.advait.patheticcitizens.navigator;

import de.metaphoriker.pathetic.bukkit.mapper.BukkitMapper;
import me.advait.patheticcitizens.PatheticCitizens;
import me.advait.patheticcitizens.npc.PatheticNPC;
import me.advait.patheticcitizens.pathfinder.PatheticAgent;
import me.advait.patheticcitizens.util.PatheticUtil;
import net.citizensnpcs.api.ai.NavigatorParameters;
import net.citizensnpcs.api.astar.pathfinder.Path;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public class PatheticNavigator {

    private final PatheticNPC npc;

    private final PatheticAgent AGENT = PatheticAgent.getInstance();
    private final BukkitScheduler scheduler = Bukkit.getScheduler();

    public PatheticNavigator(PatheticNPC npc) {
        this.npc = npc;
    }

    public void setWalkableTarget(Location target) {
        NPC citizensNPC = npc.getCitizensNPC();
        AGENT.getGroundPath(citizensNPC.getStoredLocation(), target).thenAccept(result -> {
            if (result.successful()) {
                Bukkit.getScheduler().runTask(PatheticCitizens.getInstance(), () -> {
                    List<Vector> pathVectors = new ArrayList<>();
                    result.getPath().forEach(pathPosition -> {
                        pathVectors.add(BukkitMapper.toVector(pathPosition.toVector()));
                    });
                    citizensNPC.getNavigator().setTarget(pathVectors);
                });
            }
        });

//        PatheticNavigationStrategy patheticNavigationStrategy = new PatheticNavigationStrategy(npc.getCitizensNPC(), target, new NavigatorParameters());
//        scheduler.runTaskTimer(PatheticCitizens.getInstance(), task -> {
//            if (patheticNavigationStrategy.isComplete()) {
//                patheticNavigationStrategy.stop();
//                task.cancel();
//            } else patheticNavigationStrategy.update();
//        }, 0, 1);

    }

}
