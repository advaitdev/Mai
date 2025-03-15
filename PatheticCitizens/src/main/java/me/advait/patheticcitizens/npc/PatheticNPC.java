package me.advait.patheticcitizens.npc;

import me.advait.patheticcitizens.navigator.PatheticNavigator;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;

public class PatheticNPC {

    private final PatheticNavigator navigator = new PatheticNavigator(this);
    private final String name;
    private final NPC citizensNPC;

    public PatheticNPC(String name) {
        this.name = name;
        this.citizensNPC = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, name);
    }

    public void destroy() {
        citizensNPC.destroy();
        PatheticNPCRegistry.getInstance().unregister(this);
    }

    public Location getLocation() {
        return citizensNPC.getStoredLocation();
    }

    public PatheticNavigator getNavigator() {
        return navigator;
    }

    public String getName() {
        return name;
    }

    public NPC getCitizensNPC() {
        return citizensNPC;
    }

}
