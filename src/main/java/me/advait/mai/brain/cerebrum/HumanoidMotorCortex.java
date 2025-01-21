package me.advait.mai.brain.cerebrum;

import me.advait.mai.brain.action.HumanoidAction;
import me.advait.mai.brain.action.result.HumanoidActionResult;

import java.util.concurrent.CompletableFuture;

public class HumanoidMotorCortex implements MotorCortex {

    @Override
    public CompletableFuture<HumanoidActionResult> performAction(HumanoidAction action) {
        return action.run();
    }

}
