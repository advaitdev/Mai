package me.advait.mai.obstacle;

import me.advait.mai.Catalog;
import me.advait.mai.Mai;
import me.advait.mai.body.Humanoid;
import me.advait.mai.brain.action.mechanic.movement.HumanoidWalkToAction;
import me.advait.mai.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Orchestrates obstacle-course test runs: parses {@code .obstacle} files, builds
 * arenas, spawns a humanoid, dispatches a walk-to, watches each tick for
 * success/failure, and tears everything down. Drives both the console and
 * in-game command surfaces ({@link ObstacleCommand}).
 *
 * <p>Single-instance, main-thread only. Exactly one run (or batch) may be in
 * flight at a time; a second request is rejected with a clear message.
 */
public final class ObstacleTester {

    /** Seed files copied into the runtime dir on first load when it's empty. */
    private static final String[] SEEDS = {
            "walk-flat", "stair-up", "stair-down", "gap-1", "gap-2", "gap-3",
            "pillar-jump", "corner-turn"
    };

    /** Fixed tester name; Mannequin custom names are capped at 16 chars. */
    private static final String HUMANOID_NAME = "obstacle-test";

    /** Ticks to wait between batch runs for the prior run's async tasks to drain. */
    private static final long SETTLE_TICKS = 5L;

    private static final ObstacleTester INSTANCE = new ObstacleTester();

    public static ObstacleTester getInstance() { return INSTANCE; }

    private File obstaclesDir;
    private World arenaWorld;
    private final Map<String, ObstacleFile> cache = new TreeMap<>();

    private volatile RunHandle active;
    private volatile boolean batchRunning = false;
    private volatile boolean cancelRequested = false;
    private volatile ArenaBuilder inspectArena;

    private record RunHandle(String stem, CompletableFuture<Result> future) {}

    private ObstacleTester() {}

    // =========================================================================
    // Lifecycle
    // =========================================================================

    public void initialize() {
        obstaclesDir = new File(Mai.getInstance().getDataFolder(), "obstacles");
        if (!obstaclesDir.exists()) obstaclesDir.mkdirs();
        copySeedsIfEmpty();
        reloadCache();
        arenaWorld = resolveOrCreateWorld();
    }

    /**
     * Resolves the arena world: an already-loaded world of the configured name
     * is used as-is; otherwise, if auto-create is on, a dedicated world is
     * created/loaded with the configured generator type. Falls back to the
     * default world if creation is disabled or fails.
     */
    private World resolveOrCreateWorld() {
        FileConfiguration cfg = Mai.getInstance().getSettingsFile().getConfiguration();
        String name = cfg.getString("obstacle.world.name", "obstacles");

        World existing = Bukkit.getWorld(name);
        if (existing != null) return existing;

        if (!cfg.getBoolean("obstacle.world.auto_create", true)) {
            Mai.getInstance().getLogger().warning(
                    "Obstacle world '" + name + "' not loaded and auto_create is off; using default world.");
            return Bukkit.getWorlds().getFirst();
        }

        String type = cfg.getString("obstacle.world.type", "VOID").toUpperCase(Locale.ROOT);
        WorldCreator wc = new WorldCreator(name).generateStructures(false);
        switch (type) {
            case "FLAT" -> wc.type(WorldType.FLAT);
            case "NORMAL" -> wc.type(WorldType.NORMAL);
            default -> wc.generator(new VoidGenerator());   // VOID
        }

        World world = wc.createWorld();
        if (world == null) {
            Mai.getInstance().getLogger().warning(
                    "Failed to create obstacle world '" + name + "'; using default world.");
            return Bukkit.getWorlds().getFirst();
        }
        // Keep the test world static and quiet.
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        Mai.getInstance().getLogger().info("Obstacle world ready: '" + name + "' (" + type + ")");
        return world;
    }

    private void copySeedsIfEmpty() {
        File[] existing = obstaclesDir.listFiles((d, n) -> n.endsWith(".obstacle"));
        if (existing != null && existing.length > 0) return;
        for (String seed : SEEDS) {
            try {
                Mai.getInstance().saveResource("obstacles/" + seed + ".obstacle", false);
            } catch (IllegalArgumentException e) {
                Mai.getInstance().getLogger().warning("Missing seed obstacle resource: " + seed);
            }
        }
    }

    /** Re-parses every {@code .obstacle} file from disk into the cache. */
    public int reloadCache() {
        cache.clear();
        File[] files = obstaclesDir.listFiles((d, n) -> n.endsWith(".obstacle"));
        if (files == null) return 0;
        for (File f : files) {
            try {
                ObstacleFile obs = ObstacleParser.parse(f.toPath());
                cache.put(obs.stem(), obs);
            } catch (ObstacleParser.ParseException e) {
                Mai.getInstance().getLogger().warning("Failed to parse " + f.getName() + ": " + e.getMessage());
            }
        }
        return cache.size();
    }

    public List<String> stems() { return new ArrayList<>(cache.keySet()); }

    // =========================================================================
    // Command entry points
    // =========================================================================

    public void list(CommandSender sender) {
        if (cache.isEmpty()) {
            Messages.sendMessage(sender, "&7No obstacles found in " + obstaclesDir.getPath());
            return;
        }
        Messages.sendMessage(sender, "&6Obstacles (&e" + cache.size() + "&6):");
        for (ObstacleFile obs : cache.values()) {
            Messages.sendMessage(sender, "&e  " + obs.stem() + " &7- " + obs.description());
        }
    }

    public void reload(CommandSender sender) {
        int n = reloadCache();
        Messages.sendMessage(sender, "&aReloaded &e" + n + "&a obstacle file(s).");
    }

    public void cancel(CommandSender sender) {
        cancelRequested = true;
        boolean did = false;
        RunHandle handle = active;
        if (handle != null) {
            handle.future().complete(Result.error("cancelled"));
            did = true;
        }
        if (inspectArena != null) {
            inspectArena.teardown();
            inspectArena = null;
            did = true;
        }
        Messages.sendMessage(sender, did ? "&aCancelled the running test." : "&7Nothing to cancel.");
    }

    public void runSingle(CommandSender sender, String stem) {
        if (busy(sender)) return;
        ObstacleFile obs = cache.get(stem.toLowerCase(Locale.ROOT));
        if (obs == null) obs = cache.get(stem);
        if (obs == null) {
            Messages.sendMessage(sender, "&cNo obstacle named '&e" + stem + "&c'. Try &f/mai test list&c.");
            return;
        }
        cancelRequested = false;
        Messages.sendMessage(sender, "&7Running obstacle '&e" + obs.stem() + "&7'...");
        startRun(sender, obs);
    }

    public void runAll(CommandSender sender) {
        if (busy(sender)) return;
        if (cache.isEmpty()) {
            Messages.sendMessage(sender, "&cNo obstacles to run.");
            return;
        }
        cancelRequested = false;
        batchRunning = true;
        List<ObstacleFile> all = new ArrayList<>(cache.values());
        long startMs = System.currentTimeMillis();
        int[] counts = new int[Result.Status.values().length];

        Messages.sendMessage(sender, "&7Running &e" + all.size() + "&7 obstacles...");

        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (ObstacleFile obs : all) {
            chain = chain.thenCompose(v -> {
                if (cancelRequested) return CompletableFuture.completedFuture(null);
                // Settle before building: let the previous run's in-flight async
                // pathfind finish first, so its snapshot re-caching can't land
                // after this run's build/invalidate and serve stale block data.
                return delayTicks(SETTLE_TICKS)
                        .thenCompose(x -> startRun(sender, obs))
                        .thenAccept(r -> counts[r.status().ordinal()]++);
            });
        }
        chain.whenComplete((v, ex) -> {
            batchRunning = false;
            long elapsed = System.currentTimeMillis() - startMs;
            int pass = counts[Result.Status.PASS.ordinal()];
            int fail = counts[Result.Status.FAIL.ordinal()];
            int timeout = counts[Result.Status.TIMEOUT.ordinal()];
            int error = counts[Result.Status.ERROR.ordinal()];
            int total = pass + fail + timeout + error;
            emit(sender, String.format(
                    "[Obstacle] BATCH passed=%d failed=%d timeout=%d error=%d total=%d elapsed_ms=%d",
                    pass, fail, timeout, error, total, elapsed));
        });
    }

    /** Build the arena and teleport the player in, without spawning/running. */
    public void teleport(CommandSender sender, String stem, org.bukkit.entity.Player player) {
        if (active != null) {
            Messages.sendMessage(sender, "&cA test is running — cancel it first.");
            return;
        }
        ObstacleFile obs = cache.get(stem.toLowerCase(Locale.ROOT));
        if (obs == null) obs = cache.get(stem);
        if (obs == null) {
            Messages.sendMessage(sender, "&cNo obstacle named '&e" + stem + "&c'.");
            return;
        }
        if (inspectArena != null) inspectArena.teardown();
        World world = arenaWorld();
        if (world == null) {
            Messages.sendMessage(sender, "&cArena world not found (check settings.yml).");
            return;
        }
        int[] origin = arenaXZ();
        ArenaBuilder arena = new ArenaBuilder(obs, world, origin[0], origin[1]);
        arena.build();
        inspectArena = arena;
        Location view = arena.startLocation().add(0, 1, 0);
        player.teleport(view);
        Messages.sendMessage(sender, "&aBuilt '&e" + obs.stem() + "&a' and teleported you to the start.");
        Messages.sendMessage(sender, "&7Run &f/mai test cancel&7 to remove the arena.");
    }

    /** Stocks the bot's inventory with the fixture's tool/blocks before the run. */
    private void equipBot(Humanoid humanoid, ObstacleFile obs) {
        if (obs.botTool() != null) {
            humanoid.getInventory().addItem(new org.bukkit.inventory.ItemStack(obs.botTool()));
        }
        if (obs.botBlock() != null && obs.botBlockCount() > 0) {
            int remaining = obs.botBlockCount();
            while (remaining > 0) {
                int stack = Math.min(remaining, obs.botBlock().getMaxStackSize());
                humanoid.getInventory().addItem(new org.bukkit.inventory.ItemStack(obs.botBlock(), stack));
                remaining -= stack;
            }
        }
    }

    private CompletableFuture<Void> delayTicks(long ticks) {
        CompletableFuture<Void> f = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskLater(Mai.getInstance(), () -> f.complete(null), ticks);
        return f;
    }

    private boolean busy(CommandSender sender) {
        if (active != null || batchRunning) {
            Messages.sendMessage(sender, "&cA test is already running. Use &f/mai test cancel&c first.");
            return true;
        }
        return false;
    }

    // =========================================================================
    // Core run loop
    // =========================================================================

    private CompletableFuture<Result> startRun(CommandSender sender, ObstacleFile obs) {
        // A leftover inspection arena shares the origin; remove it first.
        if (inspectArena != null) {
            inspectArena.teardown();
            inspectArena = null;
        }

        CompletableFuture<Result> future = new CompletableFuture<>();
        final int[] ticks = {0};

        World world = arenaWorld();
        if (world == null) {
            emitResult(sender, obs.stem(), Result.error("no_arena_world"), 0, null);
            future.complete(Result.error("no_arena_world"));
            return future;
        }

        int[] origin = arenaXZ();
        ArenaBuilder arena = new ArenaBuilder(obs, world, origin[0], origin[1]);
        ObstacleLog log = new ObstacleLog(obs);

        final Humanoid[] humanoidRef = {null};
        // Always complete on the main thread: the pathfinder resolves no_path
        // from an async thread, and teardown (entity ops, block writes) plus
        // batch chaining must run on the main thread.
        Consumer<Result> resolve = r -> {
            if (Bukkit.isPrimaryThread()) future.complete(r);
            else Bukkit.getScheduler().runTask(Mai.getInstance(), () -> future.complete(r));
        };

        try {
            arena.build();
            Location startLoc = arena.startLocation();
            Humanoid humanoid = Catalog.getInstance().register(HUMANOID_NAME, startLoc);
            humanoidRef[0] = humanoid;
            equipBot(humanoid, obs);
            Location target = arena.endLocation();
            log.header(obs, arena, startLoc, target);

            BukkitTask watcher = Bukkit.getScheduler().runTaskTimer(Mai.getInstance(), () -> {
                if (future.isDone()) return;
                ticks[0]++;
                LivingEntity ent = humanoid.getEntity();
                if (ent == null) return; // briefly null right after spawn
                Location loc = ent.getLocation();
                log.tick(ticks[0], loc, ent.isOnGround(), humanoid.getActionAgent().getCurrentActionName());

                if (ent.isDead() || !ent.isValid()) { resolve.accept(Result.fail("died")); return; }
                if (loc.getY() < arena.floorY() - 2) { resolve.accept(Result.fail("fell")); return; }
                if (overlapsEnd(loc, arena)) { resolve.accept(Result.pass()); return; }
                if (ticks[0] >= obs.timeoutTicks()) { resolve.accept(Result.timeout()); }
            }, 1L, 1L);

            active = new RunHandle(obs.stem(), future);

            future.whenComplete((r, ex) -> {
                Humanoid h = humanoidRef[0];
                Result rr = r != null ? r : Result.error(ex == null ? "unknown" : ex.getMessage());
                // Teardown must never strand the harness: any failure here is
                // swallowed by the CompletableFuture, so guard each step and
                // always clear `active`.
                try {
                    watcher.cancel();
                    if (h != null) {
                        h.getActionAgent().cancelAll();
                        Catalog.getInstance().unregister(h);
                    }
                    arena.teardown();
                } catch (Exception teardownEx) {
                    Mai.getInstance().getLogger().warning(
                            "Teardown error for " + obs.stem() + ": " + teardownEx);
                } finally {
                    active = null;
                    emitResult(sender, obs.stem(), rr, ticks[0], h == null ? null : h.getUuid());
                    log.finish(rr, ticks[0]);
                }
            });

            humanoid.getActionAgent().addActions(new HumanoidWalkToAction(humanoid, target))
                    .whenComplete((res, ex) -> {
                        if (future.isDone()) return;
                        if (ex != null) { resolve.accept(Result.fail("no_path")); return; }
                        // The watcher owns success; only map terminal pathing
                        // failures here so we don't wait out the full timeout.
                        if (res != null && !res.success()) {
                            String msg = res.message() == null ? "" : res.message().toLowerCase(Locale.ROOT);
                            if (msg.contains("path") || msg.contains("unreachable")) {
                                resolve.accept(Result.fail("no_path"));
                            }
                        }
                    });
        } catch (Exception e) {
            // Build/spawn/dispatch failed. Tear down whatever exists.
            if (!future.isDone()) {
                Humanoid h = humanoidRef[0];
                if (h != null) Catalog.getInstance().unregister(h);
                arena.teardown();
                active = null;
                Result rr = Result.error(e.getClass().getSimpleName() + ":" + e.getMessage());
                emitResult(sender, obs.stem(), rr, ticks[0], h == null ? null : h.getUuid());
                log.finish(rr, ticks[0]);
                future.complete(rr);
            }
        }

        return future;
    }

    /** Feet AABB (0.6 wide) overlaps the END column, with feet in its Y window. */
    private boolean overlapsEnd(Location loc, ArenaBuilder arena) {
        double half = 0.3;
        int cx = arena.endBlockX(), cz = arena.endBlockZ();
        boolean xOverlap = loc.getX() + half > cx && loc.getX() - half < cx + 1;
        boolean zOverlap = loc.getZ() + half > cz && loc.getZ() - half < cz + 1;
        if (!xOverlap || !zOverlap) return false;
        double feetY = loc.getY();
        int base = arena.floorY() + arena.endHeight();
        return feetY > base - 0.001 && feetY <= base + 2 + 0.001;
    }

    // =========================================================================
    // Output + config
    // =========================================================================

    /** Emits the result line once to console (always) and to a player sender. */
    private void emitResult(CommandSender sender, String stem, Result result, int ticks, java.util.UUID uuid) {
        emit(sender, result.line(stem, ticks, uuid));
    }

    private void emit(CommandSender sender, String line) {
        Mai.getInstance().getLogger().info(line);
        if (sender instanceof org.bukkit.entity.Player) sender.sendMessage(line);
    }

    private World arenaWorld() {
        return arenaWorld != null ? arenaWorld : Bukkit.getWorlds().getFirst();
    }

    private int[] arenaXZ() {
        FileConfiguration cfg = Mai.getInstance().getSettingsFile().getConfiguration();
        return new int[]{
                cfg.getInt("obstacle.arena_origin.x", 0),
                cfg.getInt("obstacle.arena_origin.z", 0)
        };
    }
}
