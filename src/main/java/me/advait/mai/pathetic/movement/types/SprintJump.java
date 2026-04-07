package me.advait.mai.pathetic.movement.types;

import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import me.advait.mai.pathetic.BlockClassifier;
import me.advait.mai.pathetic.config.MovementConfig;
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
    public boolean matches(PathPosition current, PathPosition previous, MaterialProvider materials) {
        int dx = current.getFlooredX() - previous.getFlooredX();
        int dy = current.getFlooredY() - previous.getFlooredY();
        int dz = current.getFlooredZ() - previous.getFlooredZ();

        // dy must be -1, 0, or +1
        if (Math.abs(dy) > 1) return false;

        // Horizontal distance must be 2-5 blocks (1-4 block gaps)
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        if (horizDist < 1.5 || horizDist > 5.5) return false;

        // Ascending parkour (dy=+1) requires distance <= 3 (Baritone limit)
        if (dy == 1 && horizDist > 3.5) return false;

        // Source must have 3-block headroom for jumping
        int sx = previous.getFlooredX(), sy = previous.getFlooredY(), sz = previous.getFlooredZ();
        if (!BlockClassifier.isTraversable(materials.getMaterial(sx, sy + 2, sz))) return false;

        // Landing must be standable
        if (!WalkFlat.isStandable(current, materials)) return false;

        // Validate the jump arc: intermediate columns must be clear
        return validateArc(previous, current, dx, dy, dz, materials);
    }

    @Override
    public double computeCost(PathPosition current, PathPosition previous,
                              MaterialProvider materials, MovementConfig config) {
        int dx = current.getFlooredX() - previous.getFlooredX();
        int dz = current.getFlooredZ() - previous.getFlooredZ();
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        int gap = Math.max(0, (int) Math.round(horizDist) - 1);

        // Sprint-jump cost: base per block + jump hunger penalty + sprint hunger
        return config.getSprintJumpBase() + gap * config.getSprintJumpPerGap()
                + config.getJumpPenalty() + horizDist * config.getHungerSprintCost();
    }

    @Override
    public ExecutionHint executionHint(PathPosition current, PathPosition previous) {
        int dx = current.getFlooredX() - previous.getFlooredX();
        int dz = current.getFlooredZ() - previous.getFlooredZ();
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        int gap = Math.max(0, (int) Math.round(horizDist) - 1);
        return ExecutionHint.sprintJump(gap);
    }

    private boolean validateArc(PathPosition src, PathPosition dst,
                                int dx, int dy, int dz, MaterialProvider materials) {
        int steps = Math.max(Math.abs(dx), Math.abs(dz));
        int srcY = src.getFlooredY();

        // Check each intermediate column (excluding source and destination)
        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            int ix = src.getFlooredX() + (int) Math.round(dx * t);
            int iz = src.getFlooredZ() + (int) Math.round(dz * t);

            // The entity passes through at the source Y level during the jump
            // Check feet, head, and above-head at source level
            // (conservative: the entity is airborne and may be higher, but
            // we must ensure at minimum the source-level columns are clear)
            for (int yOff = 0; yOff <= 2; yOff++) {
                if (!BlockClassifier.isTraversable(materials.getMaterial(ix, srcY + yOff, iz))) {
                    return false;
                }
            }
        }
        return true;
    }
}
