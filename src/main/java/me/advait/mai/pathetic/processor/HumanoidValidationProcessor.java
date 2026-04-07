package me.advait.mai.pathetic.processor;

import de.bsommerfeld.pathetic.api.pathing.processing.ValidationProcessor;
import de.bsommerfeld.pathetic.api.pathing.processing.context.EvaluationContext;
import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import me.advait.mai.pathetic.BlockClassifier;
import me.advait.mai.pathetic.movement.MaterialProvider;
import org.bukkit.Material;

/**
 * Validates that a path position is reachable for a 2-block-tall humanoid.
 *
 * <p>Handles standard walks, step-ups, multi-block falls, and parkour jumps.
 * Uses the thread-safe NavigationPointProvider for async pathfinding.
 */
public class HumanoidValidationProcessor implements ValidationProcessor {

    @Override
    public boolean isValid(EvaluationContext context) {
        PathPosition current = context.getCurrentPathPosition();
        PathPosition previous = context.getPreviousPathPosition();

        // Start node is always valid
        if (previous == null) return true;

        MaterialProvider materials = MaterialProvider.fromNavigationProvider(
                context.getNavigationPointProvider(), context.getEnvironmentContext());

        int dx = current.getFlooredX() - previous.getFlooredX();
        int dy = current.getFlooredY() - previous.getFlooredY();
        int dz = current.getFlooredZ() - previous.getFlooredZ();
        double horizDist = Math.sqrt(dx * dx + dz * dz);

        // Every position must be standable (solid below, passable feet+head)
        // EXCEPT for swim/climb positions handled by the cost processor
        Material atFeet = materials.getMaterial(current);
        if (BlockClassifier.isLiquid(atFeet) || BlockClassifier.isClimbable(atFeet)) {
            // For liquids/climbables, just need passable head space
            Material atHead = materials.getMaterial(
                    current.getFlooredX(), current.getFlooredY() + 1, current.getFlooredZ());
            return BlockClassifier.isTraversable(atHead) || BlockClassifier.isLiquid(atHead);
        }

        // Must be standable: solid below, passable at feet and head
        if (!isStandable(current, materials)) return false;

        // Parkour jumps (horizontal distance > 1.5): validate the arc
        if (horizDist > 1.5) {
            return validateParkourJump(previous, current, dx, dy, dz, materials);
        }

        // Multi-block falls (dy < -1): validate clear vertical path
        if (dy < -1) {
            return validateFall(previous, dy, materials);
        }

        // Step-up (dy=1): need 3-block headroom at source for jumping
        if (dy == 1) {
            Material aboveHead = materials.getMaterial(
                    previous.getFlooredX(), previous.getFlooredY() + 2, previous.getFlooredZ());
            return BlockClassifier.isTraversable(aboveHead);
        }

        return true;
    }

    private boolean isStandable(PathPosition pos, MaterialProvider materials) {
        Material below = materials.getMaterial(pos.getFlooredX(), pos.getFlooredY() - 1, pos.getFlooredZ());
        Material atFeet = materials.getMaterial(pos);
        Material atHead = materials.getMaterial(pos.getFlooredX(), pos.getFlooredY() + 1, pos.getFlooredZ());
        return BlockClassifier.isSolid(below)
                && BlockClassifier.isTraversable(atFeet)
                && BlockClassifier.isTraversable(atHead);
    }

    private boolean validateParkourJump(PathPosition src, PathPosition dst,
                                        int dx, int dy, int dz,
                                        MaterialProvider materials) {
        // Source must have 3-block headroom for jumping
        if (!BlockClassifier.isTraversable(materials.getMaterial(
                src.getFlooredX(), src.getFlooredY() + 2, src.getFlooredZ()))) {
            return false;
        }

        // Ascending parkour (dy=+1) limited to distance <= 3
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        if (dy == 1 && horizDist > 3.5) return false;

        // Check intermediate columns along the jump arc
        int steps = Math.max(Math.abs(dx), Math.abs(dz));
        int srcY = src.getFlooredY();

        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            int ix = src.getFlooredX() + (int) Math.round(dx * t);
            int iz = src.getFlooredZ() + (int) Math.round(dz * t);

            // Must be clear at feet, head, and above-head level
            for (int yOff = 0; yOff <= 2; yOff++) {
                if (!BlockClassifier.isTraversable(materials.getMaterial(ix, srcY + yOff, iz))) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean validateFall(PathPosition source, int dy, MaterialProvider materials) {
        // Every Y level between source and landing must be passable
        for (int y = -1; y >= dy; y--) {
            if (!BlockClassifier.isTraversable(materials.getMaterial(
                    source.getFlooredX(), source.getFlooredY() + y, source.getFlooredZ()))) {
                return false;
            }
        }
        return true;
    }
}
