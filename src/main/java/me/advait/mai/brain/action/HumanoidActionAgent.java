package me.advait.mai.brain.action;

import me.advait.mai.brain.action.mechanic.HumanoidAction;
import me.advait.mai.brain.action.result.HumanoidActionResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Executes actions sequentially for a single humanoid.
 * Each Humanoid owns its own ActionAgent instance.
 * Supports queue inspection and cancellation for debug purposes.
 */
public final class HumanoidActionAgent {

    private final Queue<HumanoidAction> actionQueue = new ConcurrentLinkedQueue<>();
    private CompletableFuture<HumanoidActionResult> processingFuture =
            CompletableFuture.completedFuture(new HumanoidActionResult(true, "Action queue initialized."));

    // Tracking for debug/inspection
    private volatile HumanoidAction currentAction = null;
    private volatile CompletableFuture<HumanoidActionResult> currentActionFuture = null;

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
                    if (!prev.success()) {
                        currentAction = null;
                        return CompletableFuture.completedFuture(prev);
                    }
                    currentAction = action;
                    CompletableFuture<HumanoidActionResult> future = action.run();
                    currentActionFuture = future;
                    return future.whenComplete((r, ex) -> {
                        if (currentAction == action) currentAction = null;
                    });
                });
            }
        }

        return chain;
    }

    /**
     * Cancels the currently running action and clears the queue.
     */
    public void cancelAll() {
        actionQueue.clear();

        // Complete the current action's future to stop any running runnables
        CompletableFuture<HumanoidActionResult> current = currentActionFuture;
        if (current != null && !current.isDone()) {
            current.complete(new HumanoidActionResult(false, "Cancelled."));
        }

        currentAction = null;
        currentActionFuture = null;

        // Reset the processing chain
        processingFuture = CompletableFuture.completedFuture(
                new HumanoidActionResult(false, "Queue cancelled."));
    }

    /**
     * Returns the name of the currently running action, or null if idle.
     */
    public String getCurrentActionName() {
        HumanoidAction action = currentAction;
        return action != null ? action.getClass().getSimpleName() : null;
    }

    /**
     * Returns the names of all queued (not yet started) actions.
     */
    public List<String> getQueuedActionNames() {
        List<String> names = new ArrayList<>();
        for (HumanoidAction action : actionQueue) {
            names.add(action.getClass().getSimpleName());
        }
        return names;
    }

    /**
     * Returns the total number of pending actions (current + queued).
     */
    public int getQueueSize() {
        return actionQueue.size() + (currentAction != null ? 1 : 0);
    }

    public boolean isProcessing() {
        return !processingFuture.isDone();
    }
}
