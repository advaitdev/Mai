package me.advait.patheticcitizens.navigator;

import de.metaphoriker.pathetic.bukkit.mapper.BukkitMapper;
import me.advait.patheticcitizens.PatheticCitizens;
import me.advait.patheticcitizens.npc.PatheticNPC;
import me.advait.patheticcitizens.pathfinder.PatheticAgent;
import me.advait.patheticcitizens.util.PatheticUtil;
import net.citizensnpcs.api.ai.NavigatorParameters;
import net.citizensnpcs.api.ai.PathfinderType;
import net.citizensnpcs.api.ai.event.CancelReason;
import net.citizensnpcs.api.ai.event.NavigatorCallback;
import net.citizensnpcs.api.astar.pathfinder.Path;
import net.citizensnpcs.api.npc.NPC;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class PatheticNavigator {

    private final PatheticNPC npc;

    private final PatheticAgent AGENT = PatheticAgent.getInstance();
    private final BukkitScheduler scheduler = Bukkit.getScheduler();

    public PatheticNavigator(PatheticNPC npc) {
        this.npc = npc;
    }

    public void setWalkableTarget(Location target) {
        // TODO: why does this throw an error lol
        // npc.getCitizensNPC().getNavigator().getDefaultParameters().pathfinderType(PathfinderType.PLUGIN);
        npc.getCitizensNPC().getNavigator().getDefaultParameters().useNewPathfinder(true);

        npc.getCitizensNPC().getNavigator().getDefaultParameters().debug(true);
        npc.getCitizensNPC().getNavigator().setTarget(target);

        AtomicInteger counter = new AtomicInteger(20);

        npc.getCitizensNPC().getNavigator().getDefaultParameters().addRunCallback(new BukkitRunnable() {

            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) player.sendActionBar(Component.text("runCallback ran at " + System.currentTimeMillis()));
                NPC citizensNPC = npc.getCitizensNPC();

                if (citizensNPC.getStoredLocation().distance(target) <= 1) {
                    citizensNPC.getNavigator().cancelNavigation();
                    cancel();
                    return;
                }

                if (counter.get() == 20) {
                    AGENT.getGroundPath(citizensNPC.getStoredLocation(), target).thenAccept(result -> {
                        if (result.successful()) {
                            Bukkit.getScheduler().runTask(PatheticCitizens.getInstance(), () -> {
                                List<Vector> pathVectors = new ArrayList<>();
                                result.getPath().forEach(pathPosition -> pathVectors.add(BukkitMapper.toVector(pathPosition.toVector())));
                                citizensNPC.getNavigator().setTarget(pathVectors);
                            });
                        }
                    });
                    counter.set(0);
                } else
                    counter.getAndIncrement();
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
