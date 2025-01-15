package me.advait.mai.body;

import me.advait.mai.brain.Brain;
import me.advait.mai.brain.cerebrum.*;
import me.advait.mai.npc.trait.HumanoidTrait;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.trait.Equipment;
import net.citizensnpcs.api.trait.trait.Inventory;
import net.citizensnpcs.trait.DropsTrait;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.entity.EntityType;
import org.mcmonkey.sentinel.SentinelTrait;

public class Humanoid {

    private final NPC npc;

    private final Brain brain;
    private final BrocasArea brocasArea;
    private final MotorCortex motorCortex;
    private final PrefrontalCortex prefrontalCortex;

    public Humanoid(String npcName) {
        this.npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, npcName);

        this.brocasArea = new HumanoidBrocasArea();
        this.motorCortex = new HumanoidMotorCortex();
        this.prefrontalCortex = new HumanoidPrefrontalCortex();
        this.brain = new Brain(brocasArea, motorCortex, prefrontalCortex);

        npc.getOrAddTrait(HumanoidTrait.class);
        npc.getOrAddTrait(SentinelTrait.class);

        npc.getOrAddTrait(Inventory.class);
        npc.getOrAddTrait(Equipment.class);
        npc.getOrAddTrait(DropsTrait.class);

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


    // "Bukkit-equivalent" methods
    public org.bukkit.inventory.Inventory getInventory() {
        return npc.getOrAddTrait(Inventory.class).getInventoryView();
    }




}
