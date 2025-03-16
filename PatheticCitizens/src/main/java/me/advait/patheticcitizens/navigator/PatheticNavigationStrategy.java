package me.advait.patheticcitizens.navigator;

import me.advait.patheticcitizens.npc.PatheticNPC;
import me.advait.patheticcitizens.npc.PatheticNPCRegistry;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.npc.ai.CitizensNavigator;
import net.citizensnpcs.util.NMS;
import net.citizensnpcs.util.Util;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.Deque;
import java.util.Queue;

public class PatheticNavigationStrategy {

    private final PatheticNPC patheticNPC;
    private final PatheticNavigator patheticNavigator;
    private Deque<Location> path;
    private float speed;

    public PatheticNavigationStrategy(PatheticNPC patheticNPC, PatheticNavigator patheticNavigator, Deque<Location> path, float speed) {
        this.patheticNPC = patheticNPC;
        this.patheticNavigator = patheticNavigator;
        this.path = path;
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

        Location currentLocation = patheticNPC.getLocation();
        Location destination = path.peek();

        if (destination == null) {
            patheticNavigator.setNavigating(false);
            return;
        }

        Vector direction = destination.toVector().subtract(currentLocation.toVector()).normalize().multiply(speed);

        if (citizensNPC.getEntity() != null) {
            citizensNPC.getEntity().setVelocity(direction);
        }

        Util.faceLocation(citizensNPC.getEntity(), destination);

        if (currentLocation.distance(destination) < 0.5) {
            path.remove();
        }

        patheticNavigator.setNavigating(true);
    }

    public boolean arrived() {
        if (path == null) return false;
        if (path.peekLast() == null) return false;
        return patheticNPC.getLocation().distance(path.peekLast()) <= 1;
    }
}
