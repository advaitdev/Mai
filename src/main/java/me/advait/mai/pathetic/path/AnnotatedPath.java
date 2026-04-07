package me.advait.mai.pathetic.path;

import me.advait.mai.pathetic.movement.ExecutionHint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A path where each waypoint is annotated with how the executor should move.
 * Supports simplification that preserves movement type transitions.
 */
public record AnnotatedPath(List<AnnotatedWaypoint> waypoints) {

    public int size() { return waypoints.size(); }

    public AnnotatedWaypoint get(int index) { return waypoints.get(index); }

    /**
     * Simplifies the path by removing redundant waypoints on straight
     * segments of the same movement type.
     *
     * <p>Keeps waypoints where:
     * <ul>
     *   <li>The movement type changes</li>
     *   <li>The direction changes (not collinear)</li>
     *   <li>The movement involves a jump or special locomotion</li>
     * </ul>
     */
    public AnnotatedPath simplify() {
        if (waypoints.size() <= 2) return this;

        List<AnnotatedWaypoint> simplified = new ArrayList<>();
        simplified.add(waypoints.get(0));

        for (int i = 1; i < waypoints.size() - 1; i++) {
            AnnotatedWaypoint prev = waypoints.get(i - 1);
            AnnotatedWaypoint curr = waypoints.get(i);
            AnnotatedWaypoint next = waypoints.get(i + 1);

            boolean keep = false;

            // Keep if movement type changes at this point or the next
            String currKey = curr.type() != null ? curr.type().key() : null;
            String nextKey = next.type() != null ? next.type().key() : null;
            if (!Objects.equals(currKey, nextKey)) keep = true;

            // Keep if direction changes
            if (!isCollinear(prev, curr, next)) keep = true;

            // Keep if this is a special movement (not simple walk/sprint)
            if (curr.hint() != null) {
                ExecutionHint.Locomotion loc = curr.hint().locomotion();
                if (loc != ExecutionHint.Locomotion.WALK && loc != ExecutionHint.Locomotion.SPRINT) {
                    keep = true;
                }
                if (curr.hint().jumpAtStart()) keep = true;
            }

            if (keep) simplified.add(curr);
        }

        simplified.add(waypoints.get(waypoints.size() - 1));
        return new AnnotatedPath(Collections.unmodifiableList(simplified));
    }

    private static boolean isCollinear(AnnotatedWaypoint a, AnnotatedWaypoint b, AnnotatedWaypoint c) {
        double dx1 = b.x() - a.x(), dz1 = b.z() - a.z(), dy1 = b.y() - a.y();
        double dx2 = c.x() - b.x(), dz2 = c.z() - b.z(), dy2 = c.y() - b.y();

        double len1 = Math.sqrt(dx1 * dx1 + dz1 * dz1 + dy1 * dy1);
        double len2 = Math.sqrt(dx2 * dx2 + dz2 * dz2 + dy2 * dy2);
        if (len1 < 0.01 || len2 < 0.01) return true;

        // Normalize
        dx1 /= len1; dz1 /= len1; dy1 /= len1;
        dx2 /= len2; dz2 /= len2; dy2 /= len2;

        // Dot product close to 1 means same direction
        double dot = dx1 * dx2 + dz1 * dz2 + dy1 * dy2;
        return dot > 0.98;
    }
}
