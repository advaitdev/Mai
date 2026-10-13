package me.advait.mai.assistant;

import me.advait.mai.Mai;
import me.advait.mai.body.Humanoid;
import me.advait.mai.brain.action.mechanic.movement.runnable.HumanoidWalkToRunnable;
import me.advait.mai.brain.action.result.HumanoidActionResult;
import org.bukkit.Location;

import java.util.concurrent.CompletableFuture;

/**
 * Pathfinding legs for behaviors. Keeps at most one walk alive and points it
 * at a moving goal with {@link HumanoidWalkToRunnable#retarget}, so a
 * follower replans toward where the target is now instead of restarting
 * from scratch every time they move. After a failed walk it waits a second
 * before trying again, so an unreachable target doesn't spam the pathfinder.
 */
final class Navigator {

    private static final int RETRY_TICKS = 20;
    private static final double RETARGET_DIST = 2.0;

    private final Humanoid humanoid;
    private HumanoidWalkToRunnable walk;
    private int retryIn = 0;
    private String lastFailure = null;

    Navigator(Humanoid humanoid) {
        this.humanoid = humanoid;
    }

    /** Head for {@code goal}, arriving anywhere within {@code radius} (0 = exact block). */
    void moveTo(Location goal, double radius) {
        if (walk != null && !walk.isDone()) {
            // Only replan once the goal has really moved; a leader walking
            // along would otherwise trigger a fresh pathfind every block.
            Location cur = walk.getTarget();
            if (cur == null || cur.getWorld() != goal.getWorld() || cur.distanceSquared(goal) >= RETARGET_DIST * RETARGET_DIST) {
                walk.retarget(goal);
            }
            return;
        }
        if (retryIn > 0) return;
        CompletableFuture<HumanoidActionResult> future = new CompletableFuture<>();
        HumanoidWalkToRunnable w = new HumanoidWalkToRunnable(humanoid, goal, radius, future);
        walk = w;
        future.thenAccept(r -> {
            if (!r.success() && !"Stopped.".equals(r.message())) {
                lastFailure = r.message();
                retryIn = RETRY_TICKS;
            } else {
                lastFailure = null;
            }
        });
        w.runTaskTimer(Mai.getInstance(), 0, 1);
    }

    void tick() {
        if (retryIn > 0) retryIn--;
    }

    boolean isMoving() {
        return walk != null && !walk.isDone();
    }

    void stop() {
        if (walk != null && !walk.isDone()) walk.stop();
        walk = null;
    }

    String lastFailure() {
        return lastFailure;
    }
}
