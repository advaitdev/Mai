package me.advait.mai.pathetic.strategy;

import de.bsommerfeld.pathetic.api.pathing.INeighborStrategy;
import de.bsommerfeld.pathetic.api.wrapper.PathVector;
import me.advait.mai.pathetic.config.MovementConfig;
import me.advait.mai.pathetic.movement.types.SprintJump;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Custom neighbor strategy that includes standard 3D neighbors
 * plus extended offsets for parkour jumps and multi-block falls.
 *
 * <p>Offset counts:
 * <ul>
 *   <li>Standard: 26 (3x3x3 cube minus center)</li>
 *   <li>Parkour: every (dx, dz) within jump reach outside the 3x3, dy +1 to -3 (~330)</li>
 *   <li>Falls: 9 horizontal combos x (dy -2 to -maxFall) (~171 at maxFall=20)</li>
 * </ul>
 */
public final class HumanoidNeighborStrategy implements INeighborStrategy {

    /** Deepest drop offered to the planner (water landings, bucket clutches). */
    public static final int LONG_DROP = 64;

    private final List<PathVector> offsets;

    public HumanoidNeighborStrategy(MovementConfig config) {
        List<PathVector> list = new ArrayList<>();

        // Standard 26 neighbors (3x3x3 cube minus center)
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x != 0 || y != 0 || z != 0) {
                        list.add(new PathVector(x, y, z));
                    }
                }
            }
        }

        // Parkour offsets: every landing within sprint-jump reach in any
        // direction (straight, diagonal, angled like 2+1 or 3+2), one block
        // up to three down. SprintJump.matches decides which are possible.
        if (config.isParkourEnabled()) {
            for (int dy = 1; dy >= -SprintJump.MAX_DROP; dy--) {
                double reach = SprintJump.maxReach(dy);
                int r = (int) Math.ceil(reach);
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) continue;   // cube and falls
                        if (Math.sqrt(dx * dx + dz * dz) > reach) continue;
                        list.add(new PathVector(dx, dy, dz));
                    }
                }
            }
        }

        // Fall offsets: multi-block falls (dy -2 to -maxFall)
        // Down to LONG_DROP even past max_fall_height: a drop into water or
        // a water-bucket clutch is harmless at any height. Each fall type
        // enforces its own limit.
        int maxFall = Math.max(config.getMaxFallHeight(), LONG_DROP);
        for (int dy = -2; dy >= -maxFall; dy--) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    list.add(new PathVector(dx, dy, dz));
                }
            }
        }

        this.offsets = Collections.unmodifiableList(list);
    }

    @Override
    public Iterable<PathVector> getOffsets() {
        return offsets;
    }
}
