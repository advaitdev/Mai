package me.advait.mai.assistant;

import me.advait.mai.body.Humanoid;
import me.advait.mai.brain.action.mechanic.HumanoidAction;
import me.advait.mai.brain.action.result.HumanoidActionResult;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * A one-tick queued step (equip a tool, place a block) written as a lambda,
 * so tools can chain "walk there, then do X" through the normal action
 * queue. The lambda returns the result; a thrown exception fails the step.
 */
final class InstantAction extends HumanoidAction {

    private final String label;
    private final Function<Humanoid, HumanoidActionResult> body;

    InstantAction(Humanoid humanoid, String label, Function<Humanoid, HumanoidActionResult> body) {
        super(humanoid);
        this.label = label;
        this.body = body;
    }

    @Override
    protected void perform(CompletableFuture<HumanoidActionResult> resultFuture) {
        try {
            resultFuture.complete(body.apply(humanoid));
        } catch (RuntimeException e) {
            resultFuture.complete(new HumanoidActionResult(false, label + " failed: " + e.getMessage()));
        }
    }

    @Override
    public String toString() { return label; }
}
