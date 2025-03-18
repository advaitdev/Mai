package me.advait.patheticcitizens.navigator;

import me.advait.patheticcitizens.PatheticCitizens;
import me.advait.patheticcitizens.npc.PatheticNPC;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.util.NMS;
import net.citizensnpcs.util.Util;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.Deque;

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
        // Util.faceLocation(citizensNPC.getEntity(), nextLocation);

        Location currentLocation = patheticNPC.getLocation();

        if (nextLocation == null) {
            patheticNavigator.setNavigating(false);
            return;
        }

        // TODO: why isn't this working consistently?
        // TODO: stop NPC from banging into things
        setNMSDestination(citizensNPC, center(nextLocation), speed);

        if (center(currentLocation).equals(nextLocation)) {
            path.remove();
        }

        patheticNavigator.setNavigating(true);
    }

    public boolean arrived() {
        // return center(patheticNPC.getLocation()).equals(destination);
        return patheticNPC.getLocation().distance(destination) <= 1;
    }

    private Location center(Location location) {
        return Util.getCenterLocation(location.getBlock());
    }

    private void setNMSDestination(NPC citizensNPC, Location nmsDestination, float speed) {
        NMS.updatePathfindingRange(citizensNPC, 1000f);
        scheduler.runTaskTimer(PatheticCitizens.getInstance(), task -> {
            if (arrived()) {
                task.cancel();
                return;
            }
            if (center(citizensNPC.getStoredLocation()).equals(nmsDestination)) {
                if (!path.isEmpty()) path.remove();
                task.cancel();
                return;
            }
            NMS.setDestination(citizensNPC.getEntity(), nmsDestination.getX(), nmsDestination.getY(), nmsDestination.getZ(), speed);
        }, 0, 1);
    }

}
