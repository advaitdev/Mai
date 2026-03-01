package me.advait.mai.util;

import de.bsommerfeld.pathetic.api.pathing.result.Path;
import de.bsommerfeld.pathetic.api.wrapper.PathPosition;

import java.util.Iterator;

/**
 * Utility methods for working with Pathetic paths.
 */
public final class PatheticUtil {

    private PatheticUtil() {}

    /**
     * Determines if the second parameter passed is a "subpath" of the first; meaning, all of the positions in the shorter
     * path exist in the longer path.
     *
     * @param longer The longer path.
     * @param shorter The shorter path.
     * @return true if shorter is a subpath of longer
     */
    public static boolean isSubpathEquivalent(Path longer, Path shorter) {
        int lengthDifference = longer.length() - shorter.length();
        if (lengthDifference < 0) return false;

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

        return !shorterIterator.hasNext() && !longerIterator.hasNext();
    }
}
