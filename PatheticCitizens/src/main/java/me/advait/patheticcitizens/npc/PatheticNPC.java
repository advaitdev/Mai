package me.advait.patheticcitizens.npc;

import me.advait.patheticcitizens.navigator.PatheticNavigator;
import net.citizensnpcs.api.CitizensAPI;
import org.bukkit.entity.EntityType;

public class PatheticNPC {

    private final PatheticNavigator navigator = new PatheticNavigator(this);

    public PatheticNPC(String name) {
        CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, name);
    }

    public PatheticNavigator getNavigator() {
        return navigator;
    }

}
