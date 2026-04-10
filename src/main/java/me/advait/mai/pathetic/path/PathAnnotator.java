package me.advait.mai.pathetic.path;

import de.bsommerfeld.pathetic.api.pathing.result.Path;
import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import me.advait.mai.pathetic.PathContext;
import me.advait.mai.pathetic.capabilities.HumanoidCapabilities;
import me.advait.mai.pathetic.config.MovementConfig;
import me.advait.mai.pathetic.movement.ExecutionHint;
import me.advait.mai.pathetic.movement.MaterialProvider;
import me.advait.mai.pathetic.movement.MovementRegistry;
import me.advait.mai.pathetic.movement.MovementType;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Converts a raw Pathetic path into an AnnotatedPath with movement type
 * information for each waypoint. Runs on the main server thread.
 */
public final class PathAnnotator {

    private final MovementRegistry registry;

    public PathAnnotator(MovementRegistry registry) {
        this.registry = registry;
    }

    /**
     * Annotates a raw path with movement types and execution hints.
     * MUST be called on the main server thread (uses World block access).
     *
     * @param rawPath the pathfinding result
     * @param world   the world the path is in
     * @param caps    the capability snapshot used for this pathfinding run
     * @param config  movement configuration
     * @return annotated path with centered X/Z and floored Y
     */
    public AnnotatedPath annotate(Path rawPath, World world, HumanoidCapabilities caps, MovementConfig config) {
        MaterialProvider materials = MaterialProvider.fromWorld(world);
        PathContext ctx = new PathContext(caps, config, materials);
        List<AnnotatedWaypoint> waypoints = new ArrayList<>();

        PathPosition previous = null;
        for (PathPosition pos : rawPath) {
            MovementType type = null;
            ExecutionHint hint = null;

            if (previous != null) {
                type = registry.classify(pos, previous, ctx).orElse(null);
                if (type != null) {
                    hint = type.executionHint(pos, previous);
                }
            }

            waypoints.add(new AnnotatedWaypoint(
                    pos.getCenteredX(),
                    pos.getFlooredY(),
                    pos.getCenteredZ(),
                    type,
                    hint
            ));

            previous = pos;
        }

        return new AnnotatedPath(Collections.unmodifiableList(waypoints));
    }
}
