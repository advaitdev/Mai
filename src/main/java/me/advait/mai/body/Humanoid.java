package me.advait.mai.body;

import me.advait.mai.brain.Brain;
import me.advait.mai.npc.trait.HumanoidStuckAction;
import me.advait.mai.npc.trait.HumanoidTrait;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.npc.ai.CitizensNavigator;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.entity.EntityType;
import org.mcmonkey.sentinel.SentinelTrait;

public class Humanoid {

    private final NPC npc;
    public Humanoid(String npcName) {
        this.npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, npcName);

        npc.getOrAddTrait(HumanoidTrait.class);
        npc.getOrAddTrait(SentinelTrait.class);

        //npc.getNavigator().getLocalParameters().stuckAction(new HumanoidStuckAction());
        npc.getNavigator().getLocalParameters().useNewPathfinder(true);

        npc.getNavigator().getLocalParameters().speedModifier(130);

        SkinTrait skinTrait = npc.getOrAddTrait(SkinTrait.class);
        skinTrait.setSkinName("WeNeedToGoDeeper", true);

    }

    public NPC getNpc() {
        return npc;
    }

    public Brain getBrain() {
        return brain;
    }

}
