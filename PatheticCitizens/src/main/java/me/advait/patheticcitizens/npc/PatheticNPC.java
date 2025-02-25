package me.advait.patheticcitizens.npc;

import me.advait.patheticcitizens.PatheticCitizens;
import me.advait.patheticcitizens.navigator.PatheticNavigator;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.npc.CitizensNPC;
import net.citizensnpcs.npc.EntityController;

import java.lang.reflect.Field;

import java.util.UUID;

public class PatheticNPC extends CitizensNPC {

    private final PatheticNavigator navigator = new PatheticNavigator(this);

    public PatheticNPC(UUID uuid, int id, String name, EntityController controller, NPCRegistry registry) {
        super(uuid, id, name, controller, registry);

        try {
            Field navigatorField = CitizensNPC.class.getDeclaredField("navigator");
            navigatorField.setAccessible(true);
            navigatorField.set(this, this.navigator);
        } catch (Exception e) {
            PatheticCitizens.getInstance().getLogger().severe("Failed to initialize PatheticNPC: " + e.getMessage());
        }
    }

    @Override
    public PatheticNavigator getNavigator() {
        return navigator;
    }

}
