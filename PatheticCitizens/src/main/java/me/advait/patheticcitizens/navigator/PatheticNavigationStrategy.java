package me.advait.patheticcitizens.navigator;

import me.advait.patheticcitizens.PatheticCitizens;
import me.advait.patheticcitizens.npc.PatheticNPC;
import me.advait.patheticcitizens.npc.PatheticNPCRegistry;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.npc.ai.CitizensNavigator;
import net.citizensnpcs.util.NMS;
import net.citizensnpcs.util.Util;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.Deque;
import java.util.Queue;

public class PatheticNavigationStrategy {

    private final PatheticNPC patheticNPC;
    private final PatheticNavigator patheticNavigator;
    private Deque<Location> path;
    private final Location destination;
    private float speed;

    private final BukkitScheduler scheduler = Bukkit.getScheduler();

    public PatheticNavigationStrategy(PatheticNPC patheticNPC, PatheticNavigator patheticNavigator, Deque<Location> path, Location destination, float speed) {
        this.patheticNPC = patheticNPC;
        this.patheticNavigator = patheticNavigator;
        this.path = path;
        this.destination = destination;
        this.speed = speed;
    }

    public void setPath(Deque<Location> path) {
        this.path = path;
    }

    public void tick() {
        NPC citizensNPC = patheticNPC.getCitizensNPC();

        if (path == null || path.isEmpty()) {
            patheticNavigator.setNavigating(false);
            return;
        }

        if (arrived()) {
            patheticNavigator.setNavigating(false);
            return;
        }

        Location nextLocation = path.peek();
        Util.faceLocation(citizensNPC.getEntity(), nextLocation);

        Location currentLocation = patheticNPC.getLocation();

        if (nextLocation == null) {
            patheticNavigator.setNavigating(false);
            return;
        }

        NMS.setDestination(citizensNPC.getEntity(), nextLocation.getX(), nextLocation.getY(), nextLocation.getZ(), speed);

        if (currentLocation.distance(nextLocation) <= 1) {
            path.remove();
        }

        patheticNavigator.setNavigating(true);
    }

    public boolean arrived() {
        return patheticNPC.getLocation().distance(destination) <= 1;
    }

    private void setNMSDestination(NPC citizensNPC, Location destination, float speed) {
        NMS.updatePathfindingRange(citizensNPC, 1000f);
        scheduler.runTaskTimer(PatheticCitizens.getInstance(), task -> {
            if (citizensNPC.getStoredLocation().distance(destination) <= 1) {
                task.cancel();
                return;
            }
            NMS.setDestination(citizensNPC.getEntity(), destination.getX(), destination.getY(), destination.getZ(), speed);
        }, 0, 1);
    }

}
