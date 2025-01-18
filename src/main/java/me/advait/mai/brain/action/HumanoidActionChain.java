package me.advait.mai.brain.action;

import me.advait.mai.Mai;
import me.advait.mai.brain.action.result.HumanoidActionResult;

import java.util.concurrent.CompletableFuture;

public final class HumanoidActionChain {

    /**
     * Chains together a variable number of HumanoidActions and executes them sequentially.
     *
     * @param actions The HumanoidActions to be performed sequentially.
     * @return A CompletableFuture that completes with the result of the last action in the chain.
     */
    public static CompletableFuture<HumanoidActionResult> executeChainedActions(HumanoidAction... actions) {
        if (actions == null || actions.length == 0) {
            Mai.log().severe("HumanoidActionChain requires at least one action, but 0 were provided.");
            throw new IllegalArgumentException("At least one action must be provided.");
        }

        // Start the chain with the first action
        CompletableFuture<HumanoidActionResult> chain = actions[0].run();

        // Chain the remaining actions
        for (int i = 1; i < actions.length; i++) {
            HumanoidAction action = actions[i];
            chain = chain.thenCompose(previousResult -> {
                if (!previousResult.isSuccess()) {
                    // If a previous action failed, short-circuit the chain
                    return CompletableFuture.completedFuture(previousResult);
                }
                // Execute the next action in the sequence
                return action.run();
            });
        }

        return chain;
    }
}