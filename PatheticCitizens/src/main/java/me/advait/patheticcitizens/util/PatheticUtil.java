package me.advait.patheticcitizens.util;

import de.metaphoriker.pathetic.api.pathing.result.Path;
import de.metaphoriker.pathetic.api.pathing.result.PathfinderResult;
import de.metaphoriker.pathetic.api.wrapper.PathPosition;
import de.metaphoriker.pathetic.bukkit.mapper.BukkitMapper;
import io.papermc.paper.util.Tick;
import me.advait.patheticcitizens.PatheticCitizens;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
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
                    locations.get().offer(BukkitMapper.toLocation(pathPosition));
                });
            } else {
                locations.set(null);
            }
        });
        return locations.get();
    }


    /**
     * Determines if the second parameter passed is a "subpath" of the first; meaning, all of the positions in the shorter
     * path exist in the longer path.
     *
     * @param longer The longer path.
     * @param shorter The shorter path.
     */
    public static boolean isSubpathEquivalent(Path longer, Path shorter) {
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

    public static void sendDebugPath(Player player, Location location) {
        Particle.DustOptions dustOptions = new Particle.DustOptions(Color.WHITE, 1.0F);

        AtomicInteger totalTimeInTicks = new AtomicInteger(20);
        Bukkit.getScheduler().runTaskTimer(PatheticCitizens.getInstance(), task -> {
            if (totalTimeInTicks.get() == 0) {
                task.cancel();
                return;
            }
            player.spawnParticle(Particle.DUST, location, 20, dustOptions);
            totalTimeInTicks.getAndDecrement();
        }, 0, 1);

    }

}
