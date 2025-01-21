package me.advait.mai.brain.cerebrum;

import me.advait.mai.brain.action.HumanoidAction;
import me.advait.mai.brain.action.result.HumanoidActionResult;

import java.util.concurrent.CompletableFuture;

// responsible for movement

/*
 * behaviors:
 * - walk to
 * - bridge to
 * - wander
 * - mine
 * - pick up
 * - place
 * - craft
 * - smelt
 * - melee attack
 * - shoot
 * - drop
 */

public interface MotorCortex {

    CompletableFuture<HumanoidActionResult> performAction(HumanoidAction action);

}
