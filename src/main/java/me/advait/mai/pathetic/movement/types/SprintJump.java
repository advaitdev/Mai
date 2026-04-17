package me.advait.mai.pathetic.movement.types;

import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import me.advait.mai.pathetic.BlockClassifier;
import me.advait.mai.pathetic.PathContext;
import me.advait.mai.pathetic.capabilities.HumanoidCapabilities;
import me.advait.mai.pathetic.movement.*;

/**
 * Sprint-jump across a gap of 1-4 blocks (horizontal distance 2-5).
 * Supports flat jumps (dy=0), ascending (+1), and descending (-1).
 *
 * Validates:
 * - Source has solid ground and 3-block headroom (room to jump)
 * - Landing has solid ground and is standable
 * - Intermediate columns are clear at feet+head+above-head level
 * - The gap is entirely air/passable (no walls blocking the arc)
 */
public record SprintJump() implements MovementType {

    @Override
    public String key() { return "sprint_jump"; }

    @Override
    public boolean matches(PathPosition current, PathPosition previous, PathContext ctx) {
        int dx = current.getFlooredX() - previous.getFlooredX();
        int dy = current.getFlooredY() - previous.getFlooredY();
        int dz = current.getFlooredZ() - previous.getFlooredZ();

        // dy must be -1, 0, or +1
        if (Math.abs(dy) > 1) return false;

        // Horizontal distance caps (vanilla 1.21 sprint-jump, verified
        // against MCPK "Longest Jumps"):
        //   dy= 0 → 4 blocks edge-to-edge (3-block gap) reliable
        //   dy=+1 → 3 blocks edge-to-edge (2-block gap) reliable
        //   dy=-1 → 4.5 blocks edge-to-edge (3-4 block gap) reliable
        // Anything beyond requires frame-perfect timing humans can hit
        // but an NPC can't guarantee — don't plan on it.
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        if (horizDist < 1.5) return false;
        if (dy == 1 && horizDist > 3.5) return false;
        if (dy == 0 && horizDist > 4.5) return false;
        if (dy == -1 && horizDist > 4.5) return false;

        MaterialProvider materials = ctx.materials();

        // Source must have 3-block headroom for jumping
        int sx = previous.getFlooredX(), sy = previous.getFlooredY(), sz = previous.getFlooredZ();
        if (!BlockClassifier.isTraversable(materials.getMaterial(sx, sy + 2, sz))) return false;

        // Landing must be standable
        if (!WalkFlat.isStandable(current, materials)) return false;

        // Validate the jump arc: intermediate columns must be clear
        return validateArc(previous, dx, dy, dz, materials);
    }

    @Override
    public double computeCost(PathPosition current, PathPosition previous, PathContext ctx) {
        int dx = current.getFlooredX() - previous.getFlooredX();
        int dz = current.getFlooredZ() - previous.getFlooredZ();
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        int gap = Math.max(0, (int) Math.round(horizDist) - 1);

        // Sprint-jump cost: base per block + jump hunger penalty + sprint hunger
        return ctx.config().getSprintJumpBase() + gap * ctx.config().getSprintJumpPerGap()
                + ctx.config().getJumpPenalty() + horizDist * ctx.config().getHungerSprintCost();
    }

    @Override
    public ExecutionHint executionHint(PathPosition current, PathPosition previous) {
        int dx = current.getFlooredX() - previous.getFlooredX();
        int dz = current.getFlooredZ() - previous.getFlooredZ();
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        int gap = Math.max(0, (int) Math.round(horizDist) - 1);
        return ExecutionHint.sprintJump(gap);
    }

    @Override
    public boolean isAllowed(HumanoidCapabilities caps) {
        return caps.canJump() && caps.canSprint();
    }

    @Override
    public boolean canReachAsEndpoint(PathPosition position, PathContext ctx) {
        return WalkFlat.isStandable(position, ctx.materials());
    }

    @Override
    public MovementStatus tick(TickContext ctx) {
        // phase 0 = building momentum on ground; phase 1 = airborne
        if (ctx.phase == 1) {
            // Airborne — steer toward landing with sprint air accel
            MovementExecutors.airSteer(ctx, MovementExecutors.SPRINT_AIR_ACCEL);
            // Touchdown: on ground with non-positive Y velocity
            if (ctx.onGround() && ctx.entity().getVelocity().getY() <= 0) {
                return MovementStatus.SUCCESS;
            }
            return MovementStatus.RUNNING;
        }

        // Ground phase: sprint to build momentum, then jump when fast enough.
        MovementExecutors.groundAccelerate(ctx, ctx.config.getSprintFactor(), true);

        if (!ctx.onGround()) {
            // Lost the ground (e.g. we already cleared a step) — go to air phase
            ctx.phase = 1;
            return MovementStatus.RUNNING;
        }

        int gap = ctx.waypoint.hint() != null ? ctx.waypoint.hint().gapLength() : 0;
        double currentSpeed = MovementExecutors.horizontalSpeed(ctx.entity().getVelocity());
        double distToLanding = MovementExecutors.horizontalDistance(ctx.current, ctx.waypoint);

        // Wider gaps need more runway. Vanilla terminal sprint is 0.2806
        // b/t; for gap ≥ 2 we want to be at or near terminal velocity when
        // the jump fires, because air accel (0.026) can't recover a
        // shortfall in the ~11 airborne ticks.
        double minSpeed = switch (gap) {
            case 0, 1 -> 0.12;
            case 2 -> 0.22;
            default -> 0.27;
        };

        boolean fastEnough = currentSpeed >= minSpeed;
        boolean inJumpRange = distToLanding < gap + 2.5 && distToLanding > 0.5;

        if (ctx.jumpCooldown == 0 && fastEnough && inJumpRange) {
            MovementExecutors.jump(ctx, true);
            ctx.phase = 1;
        }
        return MovementStatus.RUNNING;
    }

    @Override
    public boolean safeToCancel(TickContext ctx) {
        // Don't replan mid-arc — would strand the bot in the air. Only
        // safe to swap paths while still on the runway, before we've left
        // the ground.
        return ctx.phase == 0 && ctx.onGround();
    }

    @Override
    public boolean allowsAutoUnstick() {
        // Sprint-jump times its own jump; an external unstick hop during
        // the runway would kill our momentum and strand us short of the gap.
        return false;
    }

    private boolean validateArc(PathPosition src, int dx, int dy, int dz, MaterialProvider materials) {
        int steps = Math.max(Math.abs(dx), Math.abs(dz));
        int srcY = src.getFlooredY();

        // Check each intermediate column (excluding source and destination)
        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            int ix = src.getFlooredX() + (int) Math.round(dx * t);
            int iz = src.getFlooredZ() + (int) Math.round(dz * t);

            // The entity passes through at the source Y level during the jump.
            // Check feet, head, and above-head at source level (conservative —
            // the entity is airborne and may be higher).
            for (int yOff = 0; yOff <= 2; yOff++) {
                if (!BlockClassifier.isTraversable(materials.getMaterial(ix, srcY + yOff, iz))) {
                    return false;
                }
            }
        }
        return true;
    }
}
