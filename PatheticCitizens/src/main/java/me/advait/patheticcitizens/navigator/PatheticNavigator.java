package me.advait.patheticcitizens.navigator;

import me.advait.patheticcitizens.npc.PatheticNPC;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.npc.ai.CitizensNavigator;
import org.bukkit.Bukkit;
import org.bukkit.Location;

public final class PatheticNavigator extends CitizensNavigator {

    private final PatheticNPC npc;

    public PatheticNavigator(PatheticNPC npc) {
        super(npc);
        this.npc = npc;
    }

    public void setWalkableTarget(Location target) {
        // TODO
        super.setTarget(target);
        Bukkit.broadcastMessage(super.getNPC().getNavigator().toString());
    }

    public void setBuildableTarget(Location target) {
        // TODO
    }

}
