package me.advait.mai.brain.action;

import me.advait.mai.brain.action.mechanic.HumanoidAction;
import me.advait.mai.brain.action.result.HumanoidActionResult;

import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Executes actions sequentially for a single humanoid.
 * Each Humanoid owns its own ActionAgent instance.
 */
public final class HumanoidActionAgent {

    private final Queue<HumanoidAction> actionQueue = new ConcurrentLinkedQueue<>();
    private CompletableFuture<HumanoidActionResult> processingFuture =
            CompletableFuture.completedFuture(new HumanoidActionResult(true, "Action queue initialized."));

    public synchronized CompletableFuture<HumanoidActionResult> addActions(HumanoidAction... actions) {
        if (actions == null || actions.length == 0) {
            throw new IllegalArgumentException("At least one action must be provided.");
        }
        for (HumanoidAction action : actions) {
            if (action == null) throw new IllegalArgumentException("Cannot add a null action.");
            actionQueue.add(action);
        }

        processingFuture = processingFuture.thenCompose(result -> processQueue());
        return processingFuture;
    }

    private CompletableFuture<HumanoidActionResult> processQueue() {
        CompletableFuture<HumanoidActionResult> chain =
                CompletableFuture.completedFuture(new HumanoidActionResult(true, "Processing started."));

        while (!actionQueue.isEmpty()) {
            HumanoidAction action = actionQueue.poll();
            if (action != null) {
                chain = chain.thenCompose(prev -> {
                    if (!prev.success()) return CompletableFuture.completedFuture(prev);
                    return action.run();
                });
            }
        }

        return chain;
    }

    public boolean isProcessing() {
        return !processingFuture.isDone();
    }
}
