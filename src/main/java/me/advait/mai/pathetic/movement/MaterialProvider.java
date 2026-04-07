package me.advait.mai.pathetic.movement;

import de.bsommerfeld.pathetic.api.provider.NavigationPoint;
import de.bsommerfeld.pathetic.api.provider.NavigationPointProvider;
import de.bsommerfeld.pathetic.api.pathing.context.EnvironmentContext;
import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import de.bsommerfeld.pathetic.bukkit.provider.BukkitNavigationPoint;
import org.bukkit.Material;
import org.bukkit.World;

/**
 * Abstracts block material lookups so movement types work with both:
 * - NavigationPointProvider (async pathfinding, thread-safe chunk snapshots)
 * - Direct World access (main-thread path annotation)
 */
@FunctionalInterface
public interface MaterialProvider {

    /** Returns the material at the given block position. */
    Material getMaterial(int x, int y, int z);

    /** Convenience: get material at a PathPosition's floored coordinates. */
    default Material getMaterial(PathPosition pos) {
        return getMaterial(pos.getFlooredX(), pos.getFlooredY(), pos.getFlooredZ());
    }

    /** Creates a provider backed by Pathetic's async-safe navigation point system. */
    static MaterialProvider fromNavigationProvider(NavigationPointProvider provider, EnvironmentContext env) {
        return (x, y, z) -> {
            PathPosition pos = PathPosition.of(x, y, z);
            NavigationPoint point = provider.getNavigationPoint(pos, env);
            if (point instanceof BukkitNavigationPoint bukkit) {
                return bukkit.getMaterial();
            }
            return Material.AIR;
        };
    }

    /** Creates a provider backed by direct World block access (main thread only). */
    static MaterialProvider fromWorld(World world) {
        return (x, y, z) -> world.getBlockAt(x, y, z).getType();
    }
}
