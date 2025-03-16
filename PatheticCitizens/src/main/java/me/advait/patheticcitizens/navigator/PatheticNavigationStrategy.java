package me.advait.patheticcitizens.navigator;

import me.advait.patheticcitizens.npc.PatheticNPC;
import me.advait.patheticcitizens.npc.PatheticNPCRegistry;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.npc.ai.CitizensNavigator;
import net.citizensnpcs.util.NMS;
import net.citizensnpcs.util.Util;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

import java.util.Deque;
import java.util.Queue;

public class PatheticNavigationStrategy {

    private final PatheticNPC patheticNPC;
    private final PatheticNavigator patheticNavigator;
    private @Nullable Deque<Location> path;

    public PatheticNavigationStrategy(PatheticNPC patheticNPC, PatheticNavigator patheticNavigator, @Nullable Deque<Location> path) {
        this.patheticNPC = patheticNPC;
        this.patheticNavigator = patheticNavigator;
        this.path = path;
    }

    public void setPath(@Nullable Deque<Location> path) {
        this.path = path;
    }

    public void tick() {
        NPC citizensNPC = patheticNPC.getCitizensNPC();

        if (path == null) {
            patheticNavigator.setNavigating(false);
            return;
        }

        if (path.isEmpty()) {
            return;
        }

        if (arrived()) {
            System.out.println("NPC arrived.");
            patheticNavigator.setNavigating(false);
            return;
        }

        Location destination = Util.getCenterLocation(path.peekFirst().getBlock());
        System.out.println(destination);
        patheticNavigator.setNavigating(true);
        Util.faceLocation(citizensNPC.getEntity(), destination);
        NMS.setDestination(
                citizensNPC.getEntity(),
                destination.getX(), destination.getY(), destination.getZ(),
                1.0f);
    }

    public boolean arrived() {
        if (path == null) return false;
        if (path.peekLast() == null) return false;
        return patheticNPC.getLocation().distance(path.peekLast()) <= 1;
    }
}
