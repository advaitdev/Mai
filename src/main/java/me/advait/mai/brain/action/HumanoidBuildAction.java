package me.advait.mai.brain.action;

import me.advait.mai.body.Humanoid;
import me.advait.mai.brain.action.event.HumanoidActionEvent;
import me.advait.mai.brain.action.event.HumanoidBuildActionEvent;
import me.advait.mai.brain.action.result.HumanoidActionMessage;
import me.advait.mai.brain.action.result.HumanoidActionResult;
import me.advait.mai.monitor.Monitor;
import me.advait.mai.util.LocationUtil;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.trait.Equipment;
import net.citizensnpcs.util.PlayerAnimation;
import net.citizensnpcs.util.Util;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.concurrent.CompletableFuture;
public class HumanoidBuildAction extends HumanoidAction {

    private final Location location;
    private final ItemStack block;

    public HumanoidBuildAction(Humanoid humanoid, Location location, ItemStack block) {
        super(humanoid);
        this.location = location;
        this.block = block;
    }

    @Override
    protected void perform(CompletableFuture<HumanoidActionResult> resultFuture) {
        if (location == null) {
            resultFuture.complete(new HumanoidActionResult(false, HumanoidActionMessage.BUILD_MESSAGE_FAILURE_NULL));
            return;
        }

        if (!LocationUtil.isBuildable(location)) {
            resultFuture.complete(new HumanoidActionResult(false, HumanoidActionMessage.BUILD_MESSAGE_FAILURE_INVALID_LOCATION));
            return;
        }

        NPC npc = humanoid.getNpc();

        Util.faceLocation(npc.getEntity(), location);

        if (!LocationUtil.isBlockTargetable(npc.getStoredLocation(), location.getBlock())) {
            resultFuture.complete(new HumanoidActionResult(false, HumanoidActionMessage.BUILD_MESSAGE_FAILURE_TOO_FAR));
            return;
        }

        if (!LocationUtil.canSeeLocation((LivingEntity) npc.getEntity(), location)) {
            resultFuture.complete(new HumanoidActionResult(false, HumanoidActionMessage.BUILD_MESSAGE_FAILURE_OUT_OF_SIGHT));
            return;
        }

        if (humanoid.getEquipment().get(Equipment.EquipmentSlot.HAND) == null || !humanoid.getEquipment().get(Equipment.EquipmentSlot.HAND).equals(block)) {
            resultFuture.complete(new HumanoidActionResult(false, HumanoidActionMessage.BUILD_MESSAGE_FAILURE_NOT_HOLDING_BLOCK));
            return;
        }

        if (!block.getType().isBlock()) {
            resultFuture.complete(new HumanoidActionResult(false, HumanoidActionMessage.BUILD_MESSAGE_FAILURE_NOT_PLACEABLE));
            return;
        }

        // Actually place the block
        PlayerAnimation.ARM_SWING.play((Player) npc.getEntity());
        location.getBlock().setType(block.getType());

        block.setAmount(block.getAmount() - 1);
        humanoid.getEquipment().set(Equipment.EquipmentSlot.HAND, block);

        resultFuture.complete(new HumanoidActionResult(true, HumanoidActionMessage.BUILD_MESSAGE_SUCCESS));
    }

    @Override
    protected HumanoidActionEvent getEvent() {
        return new HumanoidBuildActionEvent(humanoid, location, block);
    }

}
