package me.advait.mai.brain.action.mechanic;

import me.advait.mai.brain.action.result.HumanoidActionResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * A composite action that runs multiple sub-actions in parallel.
 */
public final class HumanoidMultiAction extends HumanoidAction {

    private final List<HumanoidAction> actions;

    public HumanoidMultiAction(List<HumanoidAction> actions) {
        super(actions.getFirst().getHumanoid());
        this.actions = actions;
    }

    public HumanoidMultiAction(HumanoidAction... actions) {
        this(List.of(actions));
    }

    @Override
    protected void perform(CompletableFuture<HumanoidActionResult> resultFuture) {
        if (actions.isEmpty()) {
            resultFuture.complete(new HumanoidActionResult(false, "No sub-actions provided."));
            return;
        }

        List<CompletableFuture<HumanoidActionResult>> futures = new ArrayList<>();
        for (HumanoidAction action : actions) {
            futures.add(action.run());
        }

        CompletableFuture
                .allOf(futures.toArray(new CompletableFuture[0]))
                .thenAccept(v -> {
                    boolean allSuccess = futures.stream().allMatch(f -> f.join().success());
                    resultFuture.complete(new HumanoidActionResult(allSuccess,
                            allSuccess ? "All actions completed." : "One or more actions failed."));
                })
                .exceptionally(ex -> {
                    resultFuture.complete(new HumanoidActionResult(false, "Multi-action failed: " + ex.getMessage()));
                    return null;
                });
    }
}
