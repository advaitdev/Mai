package me.advait.patheticcitizens.util;

import de.metaphoriker.pathetic.api.pathing.result.Path;
import de.metaphoriker.pathetic.api.pathing.result.PathfinderResult;
import de.metaphoriker.pathetic.bukkit.mapper.BukkitMapper;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

public final class PatheticUtil {

    /**
     * Attempts to convert a Pathetic path into a queue of Bukkit locations.
     * @param patheticPath The CompletionStage (async) path returned by Pathetic.
     * @return A queue (FIFO) of the path as locations; null if no path was found.
     */
    public static @Nullable Deque<Location> toLocationQueue(CompletionStage<PathfinderResult> patheticPath) {
        AtomicReference<Deque<Location>> locations = new AtomicReference<>(new ArrayDeque<>());
        patheticPath.thenAccept(result -> {
            if (result.successful()) {
                Path path = result.getPath();
                path.forEach(pathPosition -> {
                    System.out.println("toLocationQueue DEBUG: " + BukkitMapper.toLocation(pathPosition));
                    locations.get().offer(BukkitMapper.toLocation(pathPosition));
                });
            } else {
                locations.set(null);
            }
        });
        return locations.get();
    }

}
