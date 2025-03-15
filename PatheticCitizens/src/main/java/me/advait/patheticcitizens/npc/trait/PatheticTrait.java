package me.advait.patheticcitizens.npc.trait;

import me.advait.patheticcitizens.npc.PatheticNPC;
import me.advait.patheticcitizens.npc.PatheticNPCRegistry;
import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.api.trait.TraitName;
import org.bukkit.Bukkit;

@TraitName("Pathetic")
public class PatheticTrait extends Trait {

    public PatheticTrait() {
        super("Pathetic");
    }


    @Override
    public void run() {
        PatheticNPCRegistry registry = PatheticNPCRegistry.getInstance();

        PatheticNPC existingPatheticNPC = registry.getPatheticNPC(npc);
        if (existingPatheticNPC == null) {
            Bukkit.getLogger().info("Detected unregistered Pathetic NPC: " + npc.getName() + "! Registering...");

            PatheticNPC patheticNPC = new PatheticNPC(npc.getName());
            registry.register(patheticNPC);
        }

    }
}
