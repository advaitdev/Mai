package me.advait.patheticcitizens.npc;

import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.npc.CitizensNPC;
import net.citizensnpcs.npc.EntityController;

import java.util.UUID;

public class PatheticNPC extends CitizensNPC {


    public PatheticNPC(UUID uuid, int id, String name, EntityController controller, NPCRegistry registry) {
        super(uuid, id, name, controller, registry);
    }


}
