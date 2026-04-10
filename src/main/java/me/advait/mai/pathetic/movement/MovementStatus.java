package me.advait.mai.pathetic.movement;

/**
 * Return value of {@link MovementType#tick}. Drives the
 * {@code HumanoidWalkToRunnable} state machine:
 *
 * <ul>
 *   <li>{@link #RUNNING} — keep ticking this movement, don't advance the path index.
 *   <li>{@link #SUCCESS} — the movement reached its waypoint; advance to the next.
 *   <li>{@link #FAILED} — the movement can't complete (obstacle appeared, jump
 *       missed, etc.); the runnable should replan or abort.
 *   <li>{@link #UNREACHABLE} — the movement's target is no longer reachable at
 *       all; replanning won't help, abort the walk.
 * </ul>
 */
public enum MovementStatus {
    RUNNING,
    SUCCESS,
    FAILED,
    UNREACHABLE
}
