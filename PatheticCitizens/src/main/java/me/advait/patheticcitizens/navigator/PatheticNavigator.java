package me.advait.patheticcitizens.navigator;

import me.advait.patheticcitizens.PatheticCitizens;
import me.advait.patheticcitizens.npc.PatheticNPC;
import me.advait.patheticcitizens.pathfinder.PatheticAgent;
import net.citizensnpcs.api.ai.NavigatorParameters;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitScheduler;

public class PatheticNavigator {

    private final PatheticNPC npc;
    private final BukkitScheduler scheduler = Bukkit.getScheduler();

    public PatheticNavigator(PatheticNPC npc) {
        this.npc = npc;
    }

    public void setWalkableTarget(Location target) {
        PatheticNavigationStrategy patheticNavigationStrategy = new PatheticNavigationStrategy(npc.getCitizensNPC(), target, new NavigatorParameters());
        scheduler.runTaskTimer(PatheticCitizens.getInstance(), task -> {
            if (patheticNavigationStrategy.isComplete()) {
                patheticNavigationStrategy.stop();
                task.cancel();
            } else patheticNavigationStrategy.update();
        }, 0, 1);
    }

}
