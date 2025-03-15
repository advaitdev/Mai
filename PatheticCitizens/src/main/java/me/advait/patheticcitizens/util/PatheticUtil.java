package me.advait.patheticcitizens.util;

import de.metaphoriker.pathetic.api.pathing.result.Path;
import de.metaphoriker.pathetic.api.pathing.result.PathfinderResult;
import de.metaphoriker.pathetic.api.wrapper.PathPosition;
import de.metaphoriker.pathetic.bukkit.mapper.BukkitMapper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class PatheticUtil {



    public static @Nullable Queue<Location> toLocationQueue(CompletionStage<PathfinderResult> patheticPath) {
        try {
            CompletableFuture<PathfinderResult> future = patheticPath.toCompletableFuture();
            PathfinderResult result = future.join();  // *Synchronously* waits for the result

            Queue<Location> locationQueue = new LinkedList<>();

            if (result.successful()) {
                Path path = result.getPath();
                for (PathPosition pathPosition : path) {
                    Vector vector = BukkitMapper.toVector(pathPosition.toVector());
                    Location location = new Location(
                            Bukkit.getWorld(pathPosition.getPathEnvironment().getName()),
                            vector.getX(), vector.getY(), vector.getZ()
                    );
                    locationQueue.offer(location);
                }
            }

            return locationQueue;

        } catch (Exception e) {
            return null;
        }
    }

}
