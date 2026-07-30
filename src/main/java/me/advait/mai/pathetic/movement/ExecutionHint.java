package me.advait.mai.pathetic.movement;

/**
 * Tells the movement executor how to physically traverse a waypoint transition.
 *
 * @param locomotion the movement mode to use
 * @param jumpAtStart whether to jump when beginning this segment
 * @param edgeJump   whether to delay the jump until the block edge (parkour)
 * @param gapLength  for sprint-jumps: horizontal gap in blocks (0 otherwise)
 */
public record ExecutionHint(
        Locomotion locomotion,
        boolean jumpAtStart,
        boolean edgeJump,
        int gapLength
) {
    public enum Locomotion {
        WALK,
        SPRINT,
        SPRINT_JUMP,
        SNEAK,
        SWIM,
        CLIMB,
        MINE,
        BRIDGE
    }

    public static ExecutionHint walk() {
        return new ExecutionHint(Locomotion.WALK, false, false, 0);
    }

    public static ExecutionHint sprint() {
        return new ExecutionHint(Locomotion.SPRINT, false, false, 0);
    }

    public static ExecutionHint walkJump() {
        return new ExecutionHint(Locomotion.WALK, true, false, 0);
    }

    public static ExecutionHint sprintJump(int gap) {
        return new ExecutionHint(Locomotion.SPRINT_JUMP, true, true, gap);
    }

    public static ExecutionHint sneak() {
        return new ExecutionHint(Locomotion.SNEAK, false, false, 0);
    }

    public static ExecutionHint swim() {
        return new ExecutionHint(Locomotion.SWIM, false, false, 0);
    }

    public static ExecutionHint climb() {
        return new ExecutionHint(Locomotion.CLIMB, false, false, 0);
    }

    /** Mining moves: kept per-waypoint by simplify (non-WALK/SPRINT locomotion). */
    public static ExecutionHint mine() {
        return new ExecutionHint(Locomotion.MINE, false, false, 0);
    }

    /** Placing/bridging moves: kept per-waypoint by simplify so each cell executes. */
    public static ExecutionHint bridge() {
        return new ExecutionHint(Locomotion.BRIDGE, false, false, 0);
    }
}
