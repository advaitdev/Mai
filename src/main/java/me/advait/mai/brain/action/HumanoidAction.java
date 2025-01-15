package me.advait.mai.brain.action;

import me.advait.mai.Mai;
import me.advait.mai.body.Humanoid;
import me.advait.mai.brain.action.event.HumanoidActionEvent;
import me.advait.mai.brain.action.result.HumanoidActionResult;
import me.advait.mai.brain.cerebrum.MotorCortex;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.concurrent.CompletableFuture;

/**
 * Represents an abstract action that can be performed by a humanoid entity.
 * Subclasses are required to implement the logic specific to the action.
 * Actions are executed in an asynchronous manner and can be cancelled through event mechanisms.
 */

/**
 * TODO:
 * - HumanoidWalkToAction
 * - HumanoidGoToAction
 * - HumanoidAttackEntityAction
 * - HumanoidShootEntityAction
 * - HumanoidMineAction
 * - HumanoidBuildAction
 * - HumanoidBuildSchematicAction
 * - HumanoidCraftAction
 * - HumanoidSmeltAction
 * - HumanoidHoldItemAction
 * - HumanoidHoldItemInOffhandAction
 * - HumanoidDropItemAction
 * - HumanoidEquipArmorAction
 * - HumanoidStoreItemInChestAction
 * - HumanoidStoreItemInFurnaceAction
 * - HumanoidFollowAction
 * - HumanoidSpeakAction
 */

public abstract class HumanoidAction {

    protected final Humanoid humanoid;
    protected final MotorCortex motorCortex;

    protected final BukkitScheduler scheduler = Mai.getInstance().getServer().getScheduler();

    public HumanoidAction(final Humanoid humanoid) {
        this.humanoid = humanoid;
        this.motorCortex = humanoid.getBrain().getMotorCortex();
    }

    /**
     * The actual driver code for the action; checks that the event isn't cancelled first and then performs the action.
     */
    public CompletableFuture<HumanoidActionResult> run() {
        CompletableFuture<HumanoidActionResult> resultFuture = new CompletableFuture<>();

        callEvent(getEvent());

        if (isEventCancelled()) {
            resultFuture.complete(new HumanoidActionResult(false, "Action cancelled due to an external source—most likely this (or another) Spigot plugin."));
        }

        perform(resultFuture);
        return resultFuture;
    }

    /**
     * The logic code for the specific action.
     */
    protected abstract void perform(CompletableFuture<HumanoidActionResult> resultFuture);

    public Humanoid getHumanoid() {
        return humanoid;
    }


    // Bukkit event code

    protected abstract HumanoidActionEvent getEvent();

    private void callEvent(HumanoidActionEvent event) {
        Mai.getInstance().getServer().getPluginManager().callEvent(event);
    }

    private boolean isEventCancelled() {
        Event event = getEvent();
        return event != null && ((Cancellable) event).isCancelled();
    }

}
