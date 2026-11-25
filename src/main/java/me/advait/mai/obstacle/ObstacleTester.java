package me.advait.mai.obstacle;

import me.advait.mai.Catalog;
import me.advait.mai.Mai;
import me.advait.mai.body.Humanoid;
import me.advait.mai.brain.action.mechanic.movement.HumanoidWalkToAction;
import me.advait.mai.util.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
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
        copyMissingBundledFixtures();
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

    /**
     * Copies every {@code obstacles/*.obstacle} bundled in the plugin jar that
     * isn't already in the runtime dir. Never overwrites, so local edits to a
     * fixture survive upgrades while newly bundled fixtures still arrive.
     */
    private void copyMissingBundledFixtures() {
        int copied = 0;
        try {
            java.net.URL src = Mai.class.getProtectionDomain().getCodeSource().getLocation();
            try (java.util.jar.JarFile jar = new java.util.jar.JarFile(new File(src.toURI()))) {
                var entries = jar.entries();
                while (entries.hasMoreElements()) {
                    String name = entries.nextElement().getName();
                    if (!name.startsWith("obstacles/") || !name.endsWith(".obstacle")) continue;
                    File out = new File(obstaclesDir, name.substring("obstacles/".length()));
                    if (out.exists()) continue;
                    Mai.getInstance().saveResource(name, false);
                    copied++;
                }
            }
        } catch (Exception e) {
            Mai.getInstance().getLogger().warning("Could not copy bundled obstacles: " + e);
        }
        if (copied > 0) {
            Mai.getInstance().getLogger().info("Copied " + copied + " bundled obstacle fixture(s).");
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
            Messages.sendMessage(sender, "&fNo obstacles found in " + obstaclesDir.getPath());
            return;
        }
        Messages.header(sender, "Obstacles (" + cache.size() + ")", "Click one to run it.");
        for (ObstacleFile obs : cache.values()) {
            sender.sendMessage(Component.text(obs.stem(), Messages.HIGHLIGHT)
                    .append(Component.text(": " + obs.description(), Messages.TEXT))
                    .hoverEvent(HoverEvent.showText(Component.text("Run " + obs.stem(), Messages.TEXT)))
                    .clickEvent(ClickEvent.runCommand("/mai test run " + obs.stem())));
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
        Messages.sendMessage(sender, did ? "&aCancelled the running test." : "&fNothing to cancel.");
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
        Messages.sendMessage(sender, "&fRunning &e" + obs.stem() + "&f...");
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

        Messages.sendMessage(sender, "&fRunning &e" + all.size() + "&f obstacles...");

        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (ObstacleFile obs : all) {
            chain = chain.thenCompose(v -> {
                if (cancelRequested) return CompletableFuture.completedFuture(null);
                // Settle before building: let the previous run's in-flight async
                // pathfind finish first, so its snapshot re-caching can't land
                // after this run's build/invalidate and serve stale block data.
                return delayTicks(SETTLE_TICKS)
                        .thenCompose(x -> startRun(sender, obs))
                        .thenAccept(r -> {
                            counts[r.status().ordinal()]++;
                            showProgress(sender, counts, all.size(), obs.stem());
                        });
            });
        }
        chain.whenComplete((v, ex) -> {
            batchRunning = false;
            if (ex != null) {
                Mai.getInstance().getLogger().log(java.util.logging.Level.WARNING,
                        "Obstacle batch aborted early", ex);
            }
            long elapsed = System.currentTimeMillis() - startMs;
            int pass = counts[Result.Status.PASS.ordinal()];
            int fail = counts[Result.Status.FAIL.ordinal()];
            int timeout = counts[Result.Status.TIMEOUT.ordinal()];
            int error = counts[Result.Status.ERROR.ordinal()];
            int total = pass + fail + timeout + error;
            Mai.getInstance().getLogger().info(String.format(
                    "[Obstacle] BATCH passed=%d failed=%d timeout=%d error=%d total=%d elapsed_ms=%d",
                    pass, fail, timeout, error, total, elapsed));
            if (sender instanceof Player p) {
                p.sendActionBar(Component.empty());
                showSummary(p, pass, fail, timeout, error, total, elapsed);
            }
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
        Messages.sendMessage(sender, "&fRun &f/mai test cancel&f to remove the arena.");
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
        for (ObstacleFile.Item item : obs.botItems()) {
            int remaining = item.count();
            while (remaining > 0) {
                int stack = Math.min(remaining, item.material().getMaxStackSize());
                humanoid.getInventory().addItem(new org.bukkit.inventory.ItemStack(item.material(), stack));
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
    // Chase benchmark
    // =========================================================================

    /**
     * A Runner walks the course start to end; {@code lead} ticks later a
     * Hunter spawns at the start and chases it with the real attack behavior
     * (the same pathing, retargeting and close-range steering a player being
     * hunted faces). The Hunter catches the Runner once it is within swing
     * reach. A perfect chaser walking the same path catches it about
     * {@code lead} ticks after the Runner arrives, or sooner; {@code excess}
     * is how much later than that it was.
     */
    public void chase(CommandSender sender, String stem, int lead) {
        if (busy(sender)) return;
        ObstacleFile obs = cache.get(stem.toLowerCase(Locale.ROOT));
        if (obs == null) obs = cache.get(stem);
        if (obs == null) {
            Messages.sendMessage(sender, "&cNo obstacle named '&e" + stem + "&c'.");
            return;
        }
        cancelRequested = false;
        Messages.sendMessage(sender, "&fChasing through &e" + obs.stem() + "&f...");
        startChase(sender, obs, lead);
    }

    /** Chases every course the bot can finish (skips impossible and knockback courses). */
    public void chaseAll(CommandSender sender, int lead) {
        if (busy(sender)) return;
        List<ObstacleFile> all = new ArrayList<>();
        for (ObstacleFile o : cache.values()) if (!o.expectNoPath() && o.knockback() == null) all.add(o);
        cancelRequested = false;
        batchRunning = true;
        long startMs = System.currentTimeMillis();
        int[] caught = {0, 0};
        long[] excess = {0};
        Messages.sendMessage(sender, "&fChasing through &e" + all.size() + "&f courses...");
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (ObstacleFile obs : all) {
            chain = chain.thenCompose(v -> cancelRequested ? CompletableFuture.completedFuture(null)
                    : delayTicks(SETTLE_TICKS).thenCompose(x -> startChase(sender, obs, lead))
                    .thenAccept(r -> {
                        if (r[0] >= 0) { caught[0]++; excess[0] += Math.max(0, r[1]); } else caught[1]++;
                    }));
        }
        chain.whenComplete((v, ex) -> {
            batchRunning = false;
            String line = String.format("[Obstacle] CHASE_BATCH caught=%d lost=%d total_excess=%d elapsed_ms=%d",
                    caught[0], caught[1], excess[0], System.currentTimeMillis() - startMs);
            Mai.getInstance().getLogger().info(line);
            if (sender instanceof Player) {
                sender.sendMessage(Component.text("Chase results: ", Messages.HEADER)
                        .append(Component.text(caught[0] + " caught", Messages.SUCCESS))
                        .append(Component.text(", ", Messages.TEXT))
                        .append(Component.text(caught[1] + " lost", caught[1] > 0 ? Messages.ERROR : Messages.TEXT))
                        .append(Component.text(", " + excess[0] + " ticks behind a perfect chase in total", Messages.TEXT)));
            }
        });
    }

    /** Completes with {catch tick or -1, excess ticks}. */
    private CompletableFuture<int[]> startChase(CommandSender sender, ObstacleFile obs, int lead) {
        if (inspectArena != null) {
            inspectArena.teardown();
            inspectArena = null;
        }
        CompletableFuture<int[]> future = new CompletableFuture<>();
        World world = arenaWorld();
        int[] origin = arenaXZ();
        ArenaBuilder arena = new ArenaBuilder(obs, world, origin[0], origin[1]);
        arena.build();
        Location start = arena.startLocation();
        Humanoid runner = Catalog.getInstance().registerTemporary("Runner", start);
        equipBot(runner, obs);
        Humanoid[] hunter = {null};
        int[] ticks = {0};
        int[] runnerArrived = {-1};
        int[] runnerMoved = {-1};
        CompletableFuture<Result> done = new CompletableFuture<>();
        active = new RunHandle(obs.stem(), done);
        int timeout = obs.timeoutTicks() + lead + 200;

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(Mai.getInstance(), () -> {
            if (done.isDone()) return;
            ticks[0]++;
            LivingEntity re = runner.peekEntity();
            // Briefly null or not yet valid right after spawn (first run after a restart).
            if (re == null || (!re.isValid() && ticks[0] < 40)) return;
            if (re.isDead() || !re.isValid() || re.getLocation().getY() < arena.floorY() - 2) {
                done.complete(Result.fail(re.isDead() ? "runner_died" : !re.isValid() ? "runner_gone" : "runner_fell"));
                return;
            }
            if (runnerArrived[0] < 0 && overlapsEnd(re.getLocation(), arena)) runnerArrived[0] = ticks[0];
            // The head start counts from the Runner's first step, not its
            // first pathfind.
            if (runnerMoved[0] < 0 && (runnerArrived[0] >= 0 || re.getLocation().distanceSquared(start) > 1.0)) {
                runnerMoved[0] = ticks[0];
            }
            if (hunter[0] == null && runnerMoved[0] >= 0 && ticks[0] == runnerMoved[0] + lead) {
                Humanoid h = Catalog.getInstance().registerTemporary(HUMANOID_NAME, start);
                hunter[0] = h;
                equipBot(h, obs);
                h.getInventory().addItem(new org.bukkit.inventory.ItemStack(org.bukkit.Material.IRON_SWORD));
                me.advait.mai.assistant.Assistant.of(h).setBehavior(new me.advait.mai.assistant.CombatBehavior(
                        h, re, me.advait.mai.assistant.CombatBehavior.Style.SMART));
            }
            if (hunter[0] != null) {
                LivingEntity he = hunter[0].peekEntity();
                if (he == null || he.isDead() || he.getLocation().getY() < arena.floorY() - 2) {
                    done.complete(Result.fail("hunter_fell"));
                    return;
                }
                if (me.advait.mai.brain.action.mechanic.movement.runnable.HumanoidWalkToRunnable.isDebugMode()) {
                    Location hl = he.getLocation(), rl = re.getLocation();
                    Mai.getInstance().getLogger().info(String.format(
                            "[Chase] t=%d hunter=(%.2f,%.2f,%.2f) v=%.3f %s %s | runner=(%.2f,%.2f,%.2f) %s",
                            ticks[0], hl.getX(), hl.getY(), hl.getZ(),
                            Math.hypot(he.getVelocity().getX(), he.getVelocity().getZ()),
                            me.advait.mai.body.Body.of(he).lastTick(),
                            me.advait.mai.assistant.Assistant.of(hunter[0]).activity(),
                            rl.getX(), rl.getY(), rl.getZ(), me.advait.mai.body.Body.of(re).lastTick()));
                }
                if (me.advait.mai.assistant.Motor.reachDistance(he, re) <= 3.0) {
                    done.complete(Result.pass());
                    return;
                }
            }
            if (ticks[0] >= timeout) done.complete(Result.timeout());
        }, 1L, 1L);

        runner.getActionAgent().addActions(new HumanoidWalkToAction(runner, arena.endLocation()));

        done.whenComplete((r, ex) -> Bukkit.getScheduler().runTask(Mai.getInstance(), () -> {
            task.cancel();
            boolean caught = r != null && r.status() == Result.Status.PASS;
            int arrive = runnerArrived[0];
            // A perfect chaser covers the Runner's route in the Runner's time,
            // starting `lead` ticks after it started moving.
            int excess = caught && arrive >= 0 ? ticks[0] - (arrive + lead) : caught ? 0 : -1;
            // A catch before the Runner had even moved isn't a chase.
            if (caught && hunter[0] == null) excess = 0;
            try {
                for (Humanoid h : new Humanoid[]{runner, hunter[0]}) {
                    if (h == null) continue;
                    me.advait.mai.assistant.Assistant.of(h).stopAll();
                    me.advait.mai.assistant.Assistant.forget(h.getUuid());
                    h.getActionAgent().cancelAll();
                    Catalog.getInstance().unregister(h);
                }
                arena.teardown();
            } finally {
                active = null;
            }
            String reason = r == null ? "error" : r.reason();
            String line = String.format("[Obstacle] CHASE name=%s status=%s ticks=%d runner_arrived=%d lead=%d excess=%d reason=%s",
                    obs.stem(), caught ? "CAUGHT" : "LOST", ticks[0], arrive, lead, excess, reason);
            Mai.getInstance().getLogger().info(line);
            if (sender instanceof Player && !(batchRunning && caught && excess <= 10)) {
                sender.sendMessage(Component.text(obs.stem() + " ", Messages.HIGHLIGHT)
                        .append(caught ? Component.text("caught", Messages.SUCCESS) : Component.text("lost: " + reason.replace('_', ' '), Messages.ERROR))
                        .append(Component.text(" in " + ticks[0] + " ticks"
                                + (caught && arrive >= 0 ? " (" + excess + " ticks behind a perfect chase)" : ""), Messages.TEXT))
                        .hoverEvent(HoverEvent.showText(Component.text(line, Messages.TEXT))));
            }
            future.complete(new int[]{caught ? ticks[0] : -1, excess});
        }));
        return future;
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
        Consumer<Result> resolve = raw -> {
            // Negative fixtures: the course is impossible under vanilla
            // physics, so the correct behaviour is to refuse it up front.
            Result r = raw;
            if (obs.expectNoPath()) {
                if (raw.status() == Result.Status.FAIL && "no_path".equals(raw.reason())) {
                    r = new Result(Result.Status.PASS, "refused_impossible");
                } else if (raw.status() == Result.Status.PASS) {
                    r = Result.fail("expected_no_path");
                }
            }
            final Result fr = r;
            if (Bukkit.isPrimaryThread()) future.complete(fr);
            else Bukkit.getScheduler().runTask(Mai.getInstance(), () -> future.complete(fr));
        };

        try {
            arena.build();
            Location startLoc = arena.startLocation();
            Humanoid humanoid = Catalog.getInstance().registerTemporary(HUMANOID_NAME, startLoc);
            humanoidRef[0] = humanoid;
            equipBot(humanoid, obs);
            Location target = arena.endLocation();
            log.header(obs, arena, startLoc, target);

            // The body this run started with. Humanoid.getEntity() quietly
            // respawns a dead body, so watch this one: a death (or any new
            // body) ends the run as "died" instead of restarting it.
            final java.util.UUID[] body = {null};
            BukkitTask watcher = Bukkit.getScheduler().runTaskTimer(Mai.getInstance(), () -> {
                if (future.isDone()) return;
                ticks[0]++;
                LivingEntity ent = humanoid.peekEntity();
                if (ent == null) return; // briefly null right after spawn
                if (body[0] == null) body[0] = ent.getUniqueId();
                if (ent.isDead() || !ent.isValid() || !ent.getUniqueId().equals(body[0])) {
                    resolve.accept(Result.fail("died"));
                    return;
                }
                Location loc = ent.getLocation();
                double[] kb = obs.knockback();
                if (kb != null && ticks[0] == (int) kb[0]) {
                    // External shove (a hit, an explosion): tests the reflexes.
                    ent.setVelocity(new org.bukkit.util.Vector(kb[1], kb[2], kb[3]));
                    me.advait.mai.pathetic.debug.PathDebugLog.event("KNOCKBACK v=(%.2f,%.2f,%.2f)", kb[1], kb[2], kb[3]);
                }
                log.tick(ticks[0], loc, ent.getVelocity(), ent.isOnGround(),
                        humanoid.getActionAgent().getCurrentActionName(),
                        me.advait.mai.body.Body.of(ent).lastTick() + String.format(" hp=%.1f hc=%s", ent.getHealth(),
                                me.advait.mai.body.Body.of(ent).collision()));

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
        } catch (Exception | LinkageError e) {
            // Build/spawn/dispatch failed. Tear down whatever exists. LinkageError
            // is caught too: a library/server incompatibility (e.g. a removed
            // method) must surface as an ERROR result, not silently end a batch.
            Mai.getInstance().getLogger().log(java.util.logging.Level.WARNING,
                    "Obstacle run " + obs.stem() + " crashed", e);
            if (!future.isDone()) {
                Humanoid h = humanoidRef[0];
                try {
                    if (h != null) Catalog.getInstance().unregister(h);
                    arena.teardown();
                } catch (Exception | LinkageError teardownEx) {
                    Mai.getInstance().getLogger().warning(
                            "Teardown after crash failed for " + obs.stem() + ": " + teardownEx);
                }
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

    /**
     * Logs the machine-parseable result line to the console and shows a player
     * sender a readable row. During a batch, a player only sees the runs that
     * didn't pass (the action bar tracks progress).
     */
    private void emitResult(CommandSender sender, String stem, Result result, int ticks, java.util.UUID uuid) {
        String line = result.line(stem, ticks, uuid);
        Mai.getInstance().getLogger().info(line);
        if (!(sender instanceof Player)) return;
        if (batchRunning && result.status() == Result.Status.PASS) return;
        sender.sendMessage(resultRow(stem, result, ticks, line));
    }

    private static Component resultRow(String stem, Result result, int ticks, String line) {
        String verb = switch (result.status()) {
            case PASS -> "passed";
            case FAIL -> "failed";
            case TIMEOUT -> "timed out";
            case ERROR -> "errored";
        };
        Component row = Component.text(stem + " ", Messages.HIGHLIGHT)
                .append(Component.text(verb, statusColor(result.status())));
        if (result.status() != Result.Status.PASS && result.status() != Result.Status.TIMEOUT) {
            row = row.append(Component.text(": " + result.reason().replace('_', ' '), statusColor(result.status())));
        }
        return row.append(Component.text(" in " + ticks + " ticks (" + String.format("%.1fs", ticks / 20.0) + ")",
                        Messages.TEXT))
                .hoverEvent(HoverEvent.showText(Component.text(line, Messages.TEXT)
                        .append(Component.newline())
                        .append(Component.text("Click to run again", Messages.HIGHLIGHT))))
                .clickEvent(ClickEvent.runCommand("/mai test run " + stem));
    }

    private static TextColor statusColor(Result.Status s) {
        return switch (s) {
            case PASS -> Messages.SUCCESS;
            case FAIL, ERROR -> Messages.ERROR;
            case TIMEOUT -> Messages.HEADER;
        };
    }

    /** Action-bar progress during a batch: counts and the run that just finished. */
    private static void showProgress(CommandSender sender, int[] counts, int total, String last) {
        if (!(sender instanceof Player p)) return;
        int done = 0;
        for (int c : counts) done += c;
        int bad = done - counts[Result.Status.PASS.ordinal()];
        p.sendActionBar(Component.text("Test " + done + "/" + total + ": ", Messages.TEXT)
                .append(Component.text(counts[Result.Status.PASS.ordinal()] + " passed", Messages.SUCCESS))
                .append(bad > 0 ? Component.text(", " + bad + " not passed", Messages.ERROR) : Component.empty())
                .append(Component.text(" (" + last + ")", Messages.TEXT)));
    }

    private static void showSummary(Player p, int pass, int fail, int timeout, int error, int total, long elapsedMs) {
        boolean clean = pass == total;
        Messages.header(p, "Test Results");
        Component counts = Component.text(pass + " passed", Messages.SUCCESS);
        if (fail > 0) counts = counts.append(Component.text(", ", Messages.TEXT))
                .append(Component.text(fail + " failed", Messages.ERROR));
        if (timeout > 0) counts = counts.append(Component.text(", ", Messages.TEXT))
                .append(Component.text(timeout + " timed out", Messages.HEADER));
        if (error > 0) counts = counts.append(Component.text(", ", Messages.TEXT))
                .append(Component.text(error + " errored", Messages.ERROR));
        p.sendMessage(counts.append(Component.text(" out of " + total + " in " + Messages.duration(elapsedMs), Messages.TEXT)));
        if (!clean) p.sendMessage(Component.text("Click a failed test above to run it again.", Messages.TEXT));
        p.playSound(p.getLocation(), clean ? org.bukkit.Sound.ENTITY_PLAYER_LEVELUP : org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS,
                0.6f, clean ? 1.2f : 0.7f);
    }

    public World world() { return arenaWorld(); }

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
