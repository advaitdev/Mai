package me.advait.mai.npc.trait.pathfinder;

import de.metaphoriker.pathetic.api.pathing.result.Path;
import de.metaphoriker.pathetic.api.wrapper.PathPosition;

import java.util.Iterator;

public class PatheticUtil {

    public static boolean isSubPathEquivalent(Path longer, Path shorter) {
        int lengthDifference = longer.length() - shorter.length();
        if (lengthDifference < 0) return false; // If the "shorter" path is somehow longer, the actual path was 100% recalculated

        // Iterate over the paths, starting from the trimmed position in the longer path
        Iterator<PathPosition> longerIterator = longer.iterator();
        Iterator<PathPosition> shorterIterator = shorter.iterator();

        // Skip the first "lengthDifference" positions of the longer path
        for (int i = 0; i < lengthDifference; i++) {
            if (longerIterator.hasNext()) {
                longerIterator.next();
            } else {
                return false;
            }
        }

        while (shorterIterator.hasNext() && longerIterator.hasNext()) {
            if (!longerIterator.next().equals(shorterIterator.next())) {
                return false;
            }
        }

        // If we exhaust both iterators without mismatch, the paths are equivalent
        return !shorterIterator.hasNext() && !longerIterator.hasNext();
    }


}
