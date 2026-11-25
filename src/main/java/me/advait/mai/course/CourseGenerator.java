package me.advait.mai.course;

import me.advait.mai.Mai;
import me.advait.mai.pathetic.movement.MovementExecutors;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.util.BiomeSearchResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

/**
 * Cuts courses out of freshly generated vanilla terrain: finds a biome in the
 * seeded source world ({@code course.source.world}, created on first use),
 * picks a start-finish line through it that fits the mode, and captures the
 * box around it.
 *
 * <p>Modes: {@code cross} (as much up-and-down as the biome offers, little
 * water), {@code climb} / {@code descend} (finish well above / below the
 * start), {@code water} (dry start and finish with open water between).
 */
final class CourseGenerator {

    /** Terrain kept around the line on every side. */
    private static final int MARGIN = 12;

    private CourseGenerator() {}

    static CompletableFuture<Course> generate(CommandSender sender, String name, String biomeName, String mode, int length) {
        CompletableFuture<Course> out = new CompletableFuture<>();
        try {
            Biome biome = Registry.BIOME.get(NamespacedKey.minecraft(biomeName.toLowerCase(Locale.ROOT)));
            if (biome == null) throw new IllegalArgumentException("unknown biome " + biomeName);
            String m = mode != null ? mode.toLowerCase(Locale.ROOT) : defaultMode(biomeName);
            if (!List.of("cross", "climb", "descend", "water").contains(m)) throw new IllegalArgumentException("unknown mode " + mode);
            attempt(sender, sourceWorld(), new Random(name.hashCode()), name, biome, biomeName, m, length, 1, out);
        } catch (RuntimeException e) {
            out.completeExceptionally(e);
        }
        return out;
    }

    /** Spots tried (each a fresh biome search from another origin) before giving up. */
    private static final int ATTEMPTS = 6;

    private static void attempt(CommandSender sender, World world, Random rnd, String name, Biome biome, String biomeName,
                                String mode, int length, int n, CompletableFuture<Course> out) {
        Location origin = new Location(world, rnd.nextInt(16000) - 8000, 64, rnd.nextInt(16000) - 8000);
        sender.sendMessage("Looking for " + biomeName + " near " + origin.getBlockX() + ", " + origin.getBlockZ()
                + " (try " + n + " of " + ATTEMPTS + ")...");
        BiomeSearchResult found = world.locateNearestBiome(origin, 6000, biome);
        if (found == null) {
            out.completeExceptionally(new IllegalStateException("no " + biomeName + " within 6000 blocks"));
            return;
        }
        Location center = found.getLocation();
        // Nudge into the biome: the search returns its edge.
        int cx = center.getBlockX() + Integer.signum(center.getBlockX() - origin.getBlockX()) * length / 3;
        int cz = center.getBlockZ() + Integer.signum(center.getBlockZ() - origin.getBlockZ()) * length / 3;
        int r = length / 2 + MARGIN + 16;
        List<CompletableFuture<?>> loads = new ArrayList<>();
        for (int x = (cx - r) >> 4; x <= (cx + r) >> 4; x++) {
            for (int z = (cz - r) >> 4; z <= (cz + r) >> 4; z++) loads.add(world.getChunkAtAsync(x, z, true));
        }
        CompletableFuture.allOf(loads.toArray(new CompletableFuture[0])).whenComplete((v, ex) ->
                Bukkit.getScheduler().runTask(Mai.getInstance(), () -> {
                    if (ex != null) {
                        out.completeExceptionally(ex);
                        return;
                    }
                    try {
                        out.complete(pick(world, name, biomeName, mode, length, cx, cz));
                    } catch (IllegalStateException e) {
                        if (n < ATTEMPTS) attempt(sender, world, rnd, name, biome, biomeName, mode, length, n + 1, out);
                        else out.completeExceptionally(e);
                    } catch (RuntimeException e) {
                        out.completeExceptionally(e);
                    }
                }));
    }

    private static String defaultMode(String biome) {
        String b = biome.toLowerCase(Locale.ROOT);
        if (b.contains("river") || b.contains("swamp") || b.contains("beach") || b.contains("ocean")) return "water";
        if (b.contains("hills") || b.contains("slopes") || b.contains("peaks") || b.contains("meadow")
                || b.contains("badlands") || b.contains("grove")) return "climb";
        return "cross";
    }

    static World sourceWorld() {
        var cfg = Mai.getInstance().getSettingsFile().getConfiguration();
        String name = cfg.getString("course.source.world", "mai-terrain");
        World w = Bukkit.getWorld(name);
        if (w != null) return w;
        w = new WorldCreator(name).type(WorldType.NORMAL).seed(cfg.getLong("course.source.seed", 20261121L))
                .generateStructures(true).createWorld();
        if (w == null) throw new IllegalStateException("could not create the source world " + name);
        w.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        w.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
        return w;
    }

    private record Column(int y, boolean water, boolean standable) {}

    private static Column column(World w, int x, int z) {
        Block top = w.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        Material t = top.getType();
        boolean water = t == Material.WATER || t == Material.BUBBLE_COLUMN || t == Material.KELP
                || t == Material.KELP_PLANT || t == Material.SEAGRASS || t == Material.TALL_SEAGRASS
                || (t == Material.LILY_PAD && top.getRelative(0, -1, 0).getType() == Material.WATER)
                || (top.getBlockData() instanceof org.bukkit.block.data.Waterlogged wl && wl.isWaterlogged());
        boolean standable = !water && t.isSolid() && t != Material.LAVA && !Tag.LOGS.isTagged(t)
                && !Tag.LEAVES.isTagged(t) && !t.name().contains("FENCE") && !t.name().contains("WALL")
                && passable(top.getRelative(0, 1, 0)) && passable(top.getRelative(0, 2, 0));
        return new Column(top.getY(), water, standable);
    }

    private static boolean passable(Block b) {
        return b.isPassable() && !b.isLiquid();
    }

    private static Course pick(World w, String name, String biome, String mode, int length, int cx, int cz) {
        double bestScore = Double.NEGATIVE_INFINITY;
        int[] best = null;
        for (int d = 0; d < 24; d++) {
            double ang = d * Math.PI / 12;
            for (int len = length - 8; len <= length + 8; len += 8) {
                int ax = cx - (int) Math.round(Math.cos(ang) * len / 2), az = cz - (int) Math.round(Math.sin(ang) * len / 2);
                int bx = cx + (int) Math.round(Math.cos(ang) * len / 2), bz = cz + (int) Math.round(Math.sin(ang) * len / 2);
                Column a = column(w, ax, az), b = column(w, bx, bz);
                if (!a.standable() || !b.standable() || Math.abs(a.y() - b.y()) > 30) continue;
                int water = 0, prev = a.y();
                double rough = 0;
                for (int i = 1; i <= len; i++) {
                    Column c = column(w, ax + (int) Math.round((bx - ax) * i / (double) len),
                            az + (int) Math.round((bz - az) * i / (double) len));
                    if (c.water()) water++;
                    else {
                        rough += Math.min(4, Math.abs(c.y() - prev));
                        prev = c.y();
                    }
                }
                int dy = b.y() - a.y();
                double score = switch (mode) {
                    case "climb" -> dy >= 4 && water <= len / 6 ? dy * 3 + rough * 0.5 : Double.NEGATIVE_INFINITY;
                    case "descend" -> dy <= -4 && water <= len / 6 ? -dy * 3 + rough * 0.5 : Double.NEGATIVE_INFINITY;
                    case "water" -> water >= 6 ? water + rough * 0.2 : Double.NEGATIVE_INFINITY;
                    default -> water <= len / 6 ? rough - water : Double.NEGATIVE_INFINITY;
                };
                if (score > bestScore) {
                    bestScore = score;
                    best = new int[]{ax, a.y(), az, bx, b.y(), bz, len, water};
                }
            }
        }
        if (best == null) throw new IllegalStateException("no start-finish line in this " + biome + " fits mode " + mode);
        int ax = best[0], ay = best[1], az = best[2], bx = best[3], by = best[4], bz = best[5];
        int minX = Math.min(ax, bx) - MARGIN, maxX = Math.max(ax, bx) + MARGIN;
        int minZ = Math.min(az, bz) - MARGIN, maxZ = Math.max(az, bz) + MARGIN;
        int floor = Integer.MAX_VALUE, top = Math.max(ay, by) + 6;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                floor = Math.min(floor, w.getHighestBlockYAt(x, z, HeightMap.OCEAN_FLOOR));
                top = Math.max(top, w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING) + 3);
            }
        }
        int minY = Math.max(w.getMinHeight(), Math.max(floor - 3, Math.min(ay, by) - 24));
        int maxY = Math.min(w.getMaxHeight() - 1, Math.min(top, minY + 127));
        Location start = standOn(w, ax, ay, az), end = standOn(w, bx, by, bz);
        start.setYaw((float) Math.toDegrees(Math.atan2(-(bx - ax), bz - az)));
        String desc = String.format("%s, %s, %d blocks%s", biome.replace('_', ' '), switch (mode) {
            case "climb" -> "climbing " + (by - ay);
            case "descend" -> "descending " + (ay - by);
            case "water" -> best[7] + " blocks of water to cross";
            default -> "rough ground";
        }, best[6], "");
        Mai.getInstance().getLogger().info(String.format("[Course] GENERATED name=%s biome=%s mode=%s start=(%d,%d,%d) end=(%d,%d,%d) box=%dx%dx%d score=%.1f",
                name, biome, mode, ax, ay, az, bx, by, bz, maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1, bestScore));
        return CourseArena.capture(name, desc, w, minX, minY, minZ, maxX, maxY, maxZ, start, end,
                (int) Math.max(600, start.distance(end) * 30));
    }

    /** Feet position standing on the top of block (x, y, z), centered. */
    private static Location standOn(World w, int x, int y, int z) {
        return new Location(w, x + 0.5, MovementExecutors.collisionTop(w.getBlockAt(x, y, z)), z + 0.5);
    }
}
