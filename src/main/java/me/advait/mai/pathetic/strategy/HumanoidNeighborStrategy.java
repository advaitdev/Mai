package me.advait.mai.pathetic.strategy;

import de.bsommerfeld.pathetic.api.pathing.INeighborStrategy;
import de.bsommerfeld.pathetic.api.wrapper.PathVector;
import me.advait.mai.pathetic.config.MovementConfig;

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
 *   <li>Parkour: 4 cardinal dirs x distances 2-5 x dy {-1,0,+1} (~36-48)</li>
 *   <li>Falls: 9 horizontal combos x (dy -2 to -maxFall) (~171 at maxFall=20)</li>
 * </ul>
 */
public final class HumanoidNeighborStrategy implements INeighborStrategy {

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

        // Parkour offsets: cardinal sprint-jumps across 1-4 block gaps
        if (config.isParkourEnabled()) {
            int maxReach = config.getMaxParkourReach();
            int[][] cardinals = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

            for (int[] dir : cardinals) {
                for (int dist = 2; dist <= maxReach + 1; dist++) {
                    int dx = dir[0] * dist;
                    int dz = dir[1] * dist;
                    for (int dy = -1; dy <= 1; dy++) {
                        // Skip if already covered by 3x3x3 cube
                        if (Math.abs(dx) <= 1 && Math.abs(dy) <= 1 && Math.abs(dz) <= 1) continue;
                        list.add(new PathVector(dx, dy, dz));
                    }
                }
            }
        }

        // Fall offsets: multi-block falls (dy -2 to -maxFall)
        int maxFall = config.getMaxFallHeight();
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
