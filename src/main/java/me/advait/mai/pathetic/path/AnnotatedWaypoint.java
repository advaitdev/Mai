package me.advait.mai.pathetic.path;

import me.advait.mai.pathetic.movement.ExecutionHint;
import me.advait.mai.pathetic.movement.MovementType;
import org.bukkit.util.Vector;

/**
 * A waypoint in an annotated path, enriched with the movement type
 * and execution hint for how to reach it from the previous waypoint.
 *
 * @param x    centered X coordinate
 * @param y    floored Y coordinate (feet level)
 * @param z    centered Z coordinate
 * @param type the movement type used to reach this waypoint (null for the first)
 * @param hint the execution hint for the movement executor (null for the first)
 */
public record AnnotatedWaypoint(double x, double y, double z,
                                MovementType type, ExecutionHint hint) {

    public Vector toVector() {
        return new Vector(x, y, z);
    }
}
