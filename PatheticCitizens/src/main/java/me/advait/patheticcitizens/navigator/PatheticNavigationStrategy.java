package me.advait.patheticcitizens.navigator;

import me.advait.patheticcitizens.npc.PatheticNPC;
import me.advait.patheticcitizens.npc.PatheticNPCRegistry;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.util.NMS;
import net.citizensnpcs.util.Util;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

import java.util.Queue;

public class PatheticNavigationStrategy {

    private final PatheticNPC patheticNPC;
    private Queue<Location> path;
    private boolean complete;

    public PatheticNavigationStrategy(PatheticNPC patheticNPC, Queue<Location> path) {
        this.patheticNPC = patheticNPC;
        this.path = path;
        this.complete = false;
    }

    public void setPath(@Nullable Queue<Location> path) {
        this.path = path;
    }

    public void tick() {
        NPC citizensNPC = patheticNPC.getCitizensNPC();

        if (path == null) {
            return;
        }

        if (path.isEmpty()) {
            this.complete = true;
            return;
        }

        Location destination = Util.getCenterLocation(path.remove().getBlock());
        NMS.setDestination(
                citizensNPC.getEntity(),
                destination.getX(), destination.getY(), destination.getZ(),
                1.0f);
    }

    public boolean isComplete() {
        return complete;
    }

}
