package me.advait.mai.brain.action;

import me.advait.mai.Mai;
import me.advait.mai.brain.action.result.HumanoidActionMessage;
import me.advait.mai.brain.action.result.HumanoidActionResult;

import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class HumanoidActionAgent {

    private static final HumanoidActionAgent INSTANCE = new HumanoidActionAgent();
    private HumanoidActionAgent() {}
    public static HumanoidActionAgent getInstance() {
        return INSTANCE;
    }

    private final Queue<HumanoidAction> actionQueue = new ConcurrentLinkedQueue<>();
    private boolean isProcessing;
    private CompletableFuture<HumanoidActionResult> processingFuture = CompletableFuture.completedFuture(new HumanoidActionResult(true, "The action queue has been initialized!"));

    public synchronized CompletableFuture<HumanoidActionResult> addAction(HumanoidAction... actions) {
        if (actions == null || actions.length == 0) {
            Mai.log().severe("HumanoidActionChain requires at least one action, but 0 were provided.");
            throw new IllegalArgumentException("At least one action must be provided.");
        }

        for (HumanoidAction action : actions) {
            if (action == null) {
                Mai.log().severe("Attempted to add a null action to the queue.");
                throw new IllegalArgumentException("Cannot add a null action to the queue.");
            }
            actionQueue.add(action);
        }

        processingFuture = processingFuture.thenCompose(result -> processQueue());

        return processingFuture;
    }

    private synchronized CompletableFuture<HumanoidActionResult> processQueue() {
        if (isProcessing) {
            return processingFuture;
        }

        isProcessing = true;

        CompletableFuture<HumanoidActionResult> resultFuture = CompletableFuture.completedFuture(new HumanoidActionResult(true, "The action queue has been initialized with the first result!"));
        while (!actionQueue.isEmpty()) {
            HumanoidAction action = actionQueue.poll();

            if (action != null) {
                resultFuture = resultFuture.thenCompose(prevResult -> {
                    if (!prevResult.isSuccess()) {
                        // If the previous action failed, stop processing further
                        return CompletableFuture.completedFuture(prevResult);
                    }
                    return action.run();
                });
            }
        }

        // Once all actions are processed, reset the processing flag
        resultFuture = resultFuture.whenComplete((result, ex) -> {
            synchronized (this) {
                isProcessing = false;
            }
        });

        return resultFuture;
    }


    @Deprecated
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