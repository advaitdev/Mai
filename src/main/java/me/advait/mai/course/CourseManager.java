package me.advait.mai.course;

import me.advait.mai.Catalog;
import me.advait.mai.Mai;
import me.advait.mai.body.Body;
import me.advait.mai.body.Humanoid;
import me.advait.mai.brain.action.mechanic.movement.HumanoidWalkToAction;
import me.advait.mai.gui.menu.GUIManager;
import me.advait.mai.obstacle.ObstacleTester;
import me.advait.mai.util.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Courses on real terrain, run by people and by bots for a baseline: a
 * player plays a course ({@link #play}) as many times as they like, every
 * finished attempt is logged tick by tick and kept, and a bot runs the same
 * copy ({@link #runBot}) to compare against the player's times.
 *
 * <p>Both clocks start on the first tick the runner moves off the start
 * (a person takes as long as they like to look before moving; the bot's
 * path planning before its first step doesn't count either) and stop on the
 * first tick it stands on the finish block. Main thread only; one course is
 * in use at a time.
 */
public final class CourseManager {

    private static final CourseManager INSTANCE = new CourseManager();
    private static final String BOT_NAME = "course-bot";
    /** How near the finish block's center the bot's walk ends (inside {@link CourseArena#finished}'s 0.8). */
    private static final double FINISH_RADIUS = 0.7;
    /** Ticks a finished or failed attempt waits before putting the player back at the start. */
    private static final int RESET_DELAY = 40;
    /** No human attempt runs longer than this (5 minutes). */
    private static final int HUMAN_LIMIT = 6000;

    public static CourseManager getInstance() { return INSTANCE; }

    private File coursesDir, runsDir;
    private final Map<String, Course> courses = new TreeMap<>();
    private final Map<UUID, Draft> drafts = new HashMap<>();

    private HumanSession human;
    private boolean botBusy;
    private volatile boolean cancelBots;
    private CourseArena built;
    private BukkitTask ticker;

    private record Draft(String name, Location start) {}

    private CourseManager() {}

    // =========================================================================
    // Lifecycle and files
    // =========================================================================

    public void initialize() {
        coursesDir = new File(Mai.getInstance().getDataFolder(), "courses");
        runsDir = new File(Mai.getInstance().getDataFolder(), "course-runs");
        coursesDir.mkdirs();
        copyBundled();
        reload();
        World w = world();
        // Nothing in the course world may change between runs on its own.
        w.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
        w.setGameRule(GameRule.DO_FIRE_TICK, false);
        w.setTime(6000);
        clearLeftover();
        ticker = Bukkit.getScheduler().runTaskTimer(Mai.getInstance(), this::tick, 1L, 1L);
    }

    public void shutdown() {
        if (human != null) endHuman(true);
        if (ticker != null) ticker.cancel();
        hideGoal();
    }

    public int reload() {
        courses.clear();
        File[] files = coursesDir.listFiles((d, n) -> n.endsWith(".course"));
        if (files == null) return 0;
        for (File f : files) {
            try {
                Course c = Course.load(f);
                courses.put(c.name(), c);
            } catch (Exception e) {
                Mai.getInstance().getLogger().warning("Could not load course " + f.getName() + ": " + e.getMessage());
            }
        }
        return courses.size();
    }

    public List<String> names() { return new ArrayList<>(courses.keySet()); }

    private void copyBundled() {
        try {
            java.net.URL src = Mai.class.getProtectionDomain().getCodeSource().getLocation();
            try (java.util.jar.JarFile jar = new java.util.jar.JarFile(new File(src.toURI()))) {
                var entries = jar.entries();
                while (entries.hasMoreElements()) {
                    String name = entries.nextElement().getName();
                    if (!name.startsWith("courses/") || !name.endsWith(".course")) continue;
                    if (new File(coursesDir, name.substring("courses/".length())).exists()) continue;
                    Mai.getInstance().saveResource(name, false);
                }
            }
        } catch (Exception e) {
            Mai.getInstance().getLogger().warning("Could not copy bundled courses: " + e);
        }
    }

    World world() { return ObstacleTester.getInstance().world(); }

    private CourseResults results(Course c) { return new CourseResults(new File(runsDir, c.name())); }

    private File logFile(Course c, String runner) {
        String ts = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        return new File(new File(runsDir, c.name()), ts + "-" + runner + ".log");
    }

    private Course find(CommandSender sender, String name) {
        Course c = courses.get(name.toLowerCase(Locale.ROOT));
        if (c == null) Messages.sendMessage(sender, "&cNo course named " + name + ". Try &f/mai course list&c.");
        return c;
    }

    /** The arena for {@code c}, built fresh; the previous course is cleared first (they share the spot). */
    private CourseArena arena(Course c) {
        if (built != null && built.course() != c) {
            built.clear();
            built = null;
            marker().delete();
        }
        if (built == null) {
            built = newArena(c);
            writeMarker(c.name());
        }
        built.build();
        return built;
    }

    private CourseArena newArena(Course c) {
        FileConfiguration cfg = Mai.getInstance().getSettingsFile().getConfiguration();
        return new CourseArena(c, world(), cfg.getInt("course.origin.x", 3000), cfg.getInt("course.origin.z", 0));
    }

    /** Names the course standing in the world, so the next start can clear it (courses share one spot). */
    private File marker() { return new File(runsDir, "built.txt"); }

    private void writeMarker(String name) {
        try {
            runsDir.mkdirs();
            java.nio.file.Files.writeString(marker().toPath(), name);
        } catch (java.io.IOException ignored) {
        }
    }

    /** Clears whatever course the last session left standing. */
    private void clearLeftover() {
        try {
            if (!marker().exists()) return;
            Course c = courses.get(java.nio.file.Files.readString(marker().toPath()).trim());
            if (c != null) newArena(c).clear();
            marker().delete();
        } catch (java.io.IOException ignored) {
        }
    }

    private boolean busy(CommandSender sender) {
        if (human != null || botBusy) {
            Messages.sendMessage(sender, human != null
                    ? "&c" + human.player.getName() + " is playing a course right now."
                    : "&cA bot is running a course. Use &f/mai course cancel&c first.");
            return true;
        }
        return false;
    }

    // =========================================================================
    // Listing and stats
    // =========================================================================

    public void list(CommandSender sender) {
        if (courses.isEmpty()) {
            Messages.sendMessage(sender, "&fNo courses yet. Make one with &f/mai course new <name>&f.");
            return;
        }
        if (sender instanceof Player p) {
            GUIManager.getInstance().openGUI(new CourseMenu(p), p);
            return;
        }
        Messages.header(sender, "Courses (" + courses.size() + ")", "Click one to play it.");
        for (Course c : courses.values()) {
            CourseResults r = results(c);
            int you = r.best("human"), bot = r.best("bot");
            Component row = Component.text(c.name(), Messages.HIGHLIGHT)
                    .append(Component.text(": " + c.description(), Messages.TEXT))
                    .append(Component.text(" (human best " + time(you) + ", bot best " + time(bot) + ")", Messages.TEXT));
            sender.sendMessage(row.hoverEvent(HoverEvent.showText(Component.text("Play " + c.name(), Messages.TEXT)))
                    .clickEvent(ClickEvent.runCommand("/mai course play " + c.name())));
        }
    }

    public void stats(CommandSender sender, String name) {
        List<Course> which = new ArrayList<>();
        if (name != null) {
            Course c = find(sender, name);
            if (c == null) return;
            which.add(c);
        } else {
            which.addAll(courses.values());
        }
        int beaten = 0, compared = 0;
        for (Course c : which) {
            CourseResults r = results(c);
            List<Integer> humans = r.finishedTicks("human");
            int bot = r.best("bot"), you = r.best("human");
            String line = String.format("[Course] STATS name=%s human_best=%d human_runs=%d bot_best=%d bot_runs=%d beats=%s",
                    c.name(), you, humans.size(), bot, r.finishedTicks("bot").size(), beats(bot, humans));
            Mai.getInstance().getLogger().info(line);
            if (bot > 0 && you > 0) {
                compared++;
                if (bot < you) beaten++;
            }
            if (sender instanceof Player) {
                sender.sendMessage(Component.text(c.name() + " ", Messages.HIGHLIGHT)
                        .append(Component.text("human " + time(you) + " best of " + humans.size()
                                + ", bot " + time(bot) + (bot > 0 && !humans.isEmpty()
                                ? ", faster than " + beats(bot, humans) + " of the human runs" : ""), Messages.TEXT)));
            }
        }
        Messages.sendMessage(sender, "&fThe bot's best beats the human best on &e" + beaten + "&f of &e" + compared
                + "&f courses with both.");
    }

    /** Share of {@code humans} that {@code bot} is strictly faster than, as a percentage. */
    private static String beats(int bot, List<Integer> humans) {
        if (bot <= 0 || humans.isEmpty()) return "-";
        long n = humans.stream().filter(h -> bot < h).count();
        return Math.round(100.0 * n / humans.size()) + "%";
    }

    static String time(int ticks) {
        return ticks < 0 ? "none" : String.format("%.2fs", ticks / 20.0);
    }

    /** What the course menu shows for one course. Times are ticks, -1 for none. */
    record Info(Course course, Material icon, int yourBest, int yourRuns, int humanBest, String humanBestBy,
                int humanRuns, int botBest, int botRuns, String beats) {}

    List<Info> infos(Player viewer) {
        List<Info> out = new ArrayList<>();
        for (Course c : courses.values()) {
            int yourBest = -1, yourRuns = 0, humanBest = -1, humanRuns = 0, botBest = -1, botRuns = 0;
            String by = null;
            List<Integer> humans = new ArrayList<>();
            for (CourseResults.Attempt a : results(c).all()) {
                if (!a.finished()) continue;
                if (a.kind().equals("bot")) {
                    botRuns++;
                    if (botBest < 0 || a.ticks() < botBest) botBest = a.ticks();
                    continue;
                }
                humanRuns++;
                humans.add(a.ticks());
                if (humanBest < 0 || a.ticks() < humanBest) {
                    humanBest = a.ticks();
                    by = a.runner();
                }
                if (a.runner().equals(viewer.getName())) {
                    yourRuns++;
                    if (yourBest < 0 || a.ticks() < yourBest) yourBest = a.ticks();
                }
            }
            out.add(new Info(c, icon(c), yourBest, yourRuns, humanBest, by, humanRuns, botBest, botRuns,
                    beats(botBest, humans)));
        }
        return out;
    }

    /**
     * The menu item for a course: {@code course.menu.icons.<course name>},
     * else the entry for its name without the trailing number
     * ({@code forest-1} uses {@code forest}), else {@code course.menu.default_icon}.
     */
    private Material icon(Course c) {
        FileConfiguration cfg = Mai.getInstance().getSettingsFile().getConfiguration();
        for (String key : new String[]{"icons." + c.name(), "icons." + c.name().replaceAll("-\\d+$", ""), "default_icon"}) {
            String value = cfg.getString("course.menu." + key);
            Material m = value == null ? null : Material.matchMaterial(value);
            if (m != null && m.isItem()) return m;
        }
        return Material.GRASS_BLOCK;
    }

    boolean playing(Player p) { return human != null && human.player == p; }

    // =========================================================================
    // Making courses
    // =========================================================================

    public void newDraft(Player p, String name) {
        name = name.toLowerCase(Locale.ROOT);
        if (!name.matches("[a-z0-9_-]{1,32}")) {
            Messages.sendMessage(p, "&cUse letters, digits, - and _ for the name.");
            return;
        }
        if (!p.isOnGround()) {
            Messages.sendMessage(p, "&cStand on the ground where the course starts.");
            return;
        }
        Location start = p.getLocation();
        start.setX(start.getBlockX() + 0.5);
        start.setZ(start.getBlockZ() + 0.5);
        drafts.put(p.getUniqueId(), new Draft(name, start));
        Messages.sendMessage(p, "&aStart of &e" + name + "&a set here, facing where you look."
                + " &fWalk to the finish and run &e/mai course end&f.");
    }

    public void endDraft(Player p, int margin) {
        Draft d = drafts.get(p.getUniqueId());
        if (d == null) {
            Messages.sendMessage(p, "&cStart a course with &f/mai course new <name>&c first.");
            return;
        }
        if (!p.isOnGround() || p.getWorld() != d.start().getWorld()) {
            Messages.sendMessage(p, "&cStand on the finish block, in the same world as the start.");
            return;
        }
        Location end = p.getLocation();
        World w = end.getWorld();
        int minX = Math.min(d.start().getBlockX(), end.getBlockX()) - margin;
        int maxX = Math.max(d.start().getBlockX(), end.getBlockX()) + margin;
        int minZ = Math.min(d.start().getBlockZ(), end.getBlockZ()) - margin;
        int maxZ = Math.max(d.start().getBlockZ(), end.getBlockZ()) + margin;
        int lowY = Math.min(d.start().getBlockY(), end.getBlockY());
        int highY = Math.max(d.start().getBlockY(), end.getBlockY());
        int top = highY + 8;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                top = Math.max(top, w.getHighestBlockYAt(x, z, org.bukkit.HeightMap.MOTION_BLOCKING) + 2);
            }
        }
        int minY = Math.max(w.getMinHeight(), lowY - 10);
        int maxY = Math.min(Math.min(w.getMaxHeight() - 1, top), minY + 127);
        Course c = CourseArena.capture(d.name(), "made by " + p.getName(), w, minX, minY, minZ, maxX, maxY, maxZ,
                d.start(), end, defaultTimeout(d.start(), end));
        save(p, c);
        drafts.remove(p.getUniqueId());
    }

    private static int defaultTimeout(Location a, Location b) {
        return (int) Math.max(600, a.distance(b) * 30);
    }

    private void save(CommandSender sender, Course c) {
        try {
            c.save(new File(coursesDir, c.name() + ".course"));
            courses.put(c.name(), c);
            if (built != null && built.course().name().equals(c.name())) {
                built.clear();
                built = null;
            }
            String msg = String.format("[Course] SAVED name=%s size=%dx%dx%d distance=%.1f palette=%d source=%s",
                    c.name(), c.sizeX(), c.sizeY(), c.sizeZ(), c.distance(), c.palette().size(), c.source());
            Mai.getInstance().getLogger().info(msg);
            sender.sendMessage(Component.text("Saved course ", Messages.SUCCESS)
                    .append(Component.text(c.name(), Messages.HIGHLIGHT))
                    .append(Component.text(String.format(" (%.0f blocks start to finish). ", c.distance()), Messages.TEXT))
                    .append(Component.text("[Play it]", Messages.INFO)
                            .clickEvent(ClickEvent.runCommand("/mai course play " + c.name()))));
        } catch (Exception e) {
            Messages.sendMessage(sender, "&cCould not save the course: " + e.getMessage());
        }
    }

    public void delete(CommandSender sender, String name) {
        Course c = find(sender, name);
        if (c == null || busy(sender)) return;
        if (built != null && built.course() == c) {
            built.clear();
            built = null;
        }
        courses.remove(c.name());
        new File(coursesDir, c.name() + ".course").delete();
        Messages.sendMessage(sender, "&aDeleted course &e" + c.name() + "&a (its run logs stay in course-runs).");
    }

    public void generate(CommandSender sender, String name, String biome, String mode, int length) {
        CourseGenerator.generate(sender, name.toLowerCase(Locale.ROOT), biome, mode, length)
                .whenComplete((c, ex) -> Bukkit.getScheduler().runTask(Mai.getInstance(), () -> {
                    if (ex != null) {
                        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                        Messages.sendMessage(sender, "&cCould not generate " + name + ": " + cause.getMessage());
                        Mai.getInstance().getLogger().info("[Course] GENERATE_FAILED name=" + name + " reason=" + cause.getMessage());
                        return;
                    }
                    save(sender, c);
                }));
    }

    // =========================================================================
    // Human runs
    // =========================================================================

    private enum Phase { READY, RUNNING, DONE }

    private final class HumanSession {
        final Player player;
        final Course course;
        final CourseArena arena;
        final SavedState saved;
        Phase phase = Phase.READY;
        int tick, resetIn = -1;
        boolean dirty;
        Location last;
        CourseLog log;

        HumanSession(Player player, Course course, CourseArena arena, SavedState saved) {
            this.player = player;
            this.course = course;
            this.arena = arena;
            this.saved = saved;
        }
    }

    public void play(Player p, String name) {
        Course c = find(p, name);
        if (c == null) return;
        // Picking another course mid-course switches to it; the way back stays the original one.
        SavedState saved = null;
        if (human != null && human.player == p) {
            saved = human.saved;
            endHuman(false);
        }
        if (busy(p)) return;
        CourseArena arena = arena(c);
        human = new HumanSession(p, c, arena, saved != null ? saved : new SavedState(p));
        resetHuman();
        CourseResults r = results(c);
        p.sendMessage(Component.text("Course ", Messages.TEXT).append(Component.text(c.name(), Messages.HIGHLIGHT))
                .append(Component.text(": " + c.description() + ". The clock starts when you move and stops when"
                        + " you stand on the finish (the glowing block under the beam). Human best: " + time(r.best("human"))
                        + ", bot best: " + time(r.best("bot")) + ".", Messages.TEXT)));
        p.sendMessage(Component.text("Swap-hands key (F) restarts, sneak + F leaves, ", Messages.TEXT)
                .append(Component.text("/mai course", Messages.HIGHLIGHT)
                        .clickEvent(ClickEvent.runCommand("/mai course")))
                .append(Component.text(" picks another course.", Messages.TEXT)));
    }

    public void retry(Player p) {
        if (human == null || human.player != p) {
            Messages.sendMessage(p, "&cYou're not on a course.");
            return;
        }
        if (human.phase == Phase.RUNNING) human.log.finish("RESTARTED", "restart", human.tick);
        resetHuman();
    }

    public void quit(Player p) {
        if (human == null || human.player != p) {
            Messages.sendMessage(p, "&cYou're not on a course.");
            return;
        }
        endHuman(true);
        Messages.sendMessage(p, "&aLeft the course.");
    }

    public void discard(Player p) {
        Course c = human != null && human.player == p ? human.course : lastPlayed.get(p.getUniqueId());
        if (c == null) {
            Messages.sendMessage(p, "&cNo recent course to discard a run from.");
            return;
        }
        CourseResults.Attempt a = results(c).discardLast(p.getName());
        Messages.sendMessage(p, a == null ? "&cYou have no runs on " + c.name() + "."
                : "&aDiscarded your " + time(a.ticks()) + " run on &e" + c.name() + "&a.");
    }

    private final Map<UUID, Course> lastPlayed = new HashMap<>();

    /** Puts the player back on the start, ready: full health and hunger, no effects, adventure mode. */
    private void resetHuman() {
        HumanSession s = human;
        Player p = s.player;
        if (s.dirty) {
            s.arena.build();
            s.dirty = false;
        }
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        p.setGameMode(GameMode.ADVENTURE);
        p.setFlying(false);
        p.setAllowFlight(false);
        p.setHealth(p.getMaxHealth());
        p.setFoodLevel(20);
        p.setSaturation(20);
        p.setFireTicks(0);
        p.setFallDistance(0);
        p.teleport(s.arena.start());
        p.setVelocity(new org.bukkit.util.Vector());
        s.phase = Phase.READY;
        s.resetIn = -1;
        s.tick = 0;
        s.last = s.arena.start();
        s.log = null;
        p.showTitle(Title.title(Component.empty(), Component.text("Ready", Messages.HIGHLIGHT),
                Title.Times.times(java.time.Duration.ZERO, java.time.Duration.ofMillis(700), java.time.Duration.ofMillis(200))));
    }

    private void endHuman(boolean restore) {
        HumanSession s = human;
        if (s == null) return;
        human = null;
        if (s.phase == Phase.RUNNING && s.log != null) s.log.finish("ABANDONED", "left", s.tick);
        lastPlayed.put(s.player.getUniqueId(), s.course);
        if (restore) s.saved.restore(s.player);
        s.player.sendActionBar(Component.empty());
    }

    private void tick() {
        HumanSession s = human;
        if (s != null) tickHuman(s);
        boolean inUse = built != null && (human != null || botBusy);
        if (!inUse) hideGoal();
        else if (goalFor != built) showGoal(built);
        if (inUse && Bukkit.getCurrentTick() % 4 == 0) {
            Location end = built.end();
            built.world().spawnParticle(Particle.HAPPY_VILLAGER, end.clone().add(0, 0.2, 0), 6, 0.3, 0.1, 0.3, 0);
        }
    }

    // The finish, visible from anywhere on the course: a glowing shell around the
    // finish block and a glowing beam above it (display entities, so they collide
    // with nothing, are outlined through terrain and are never saved).

    private final List<BlockDisplay> goal = new ArrayList<>();
    private CourseArena goalFor;

    private void showGoal(CourseArena arena) {
        hideGoal();
        goalFor = arena;
        FileConfiguration cfg = Mai.getInstance().getSettingsFile().getConfiguration();
        if (!cfg.getBoolean("course.goal.glow", true)) return;
        BlockData data;
        try {
            data = Bukkit.createBlockData(cfg.getString("course.goal.block", "lime_stained_glass"));
        } catch (IllegalArgumentException e) {
            data = Material.LIME_STAINED_GLASS.createBlockData();
        }
        Color color;
        try {
            color = Color.fromRGB(Integer.parseInt(cfg.getString("course.goal.color", "55FF55").replace("#", ""), 16));
        } catch (IllegalArgumentException e) {
            color = Color.LIME;
        }
        Location end = arena.end();
        double top = end.getY(), bottom = Math.ceil(top) - 1;
        // The finish block, wrapped a little larger so the shell never z-fights with it.
        goal.add(display(new Location(end.getWorld(), end.getBlockX() - 0.01, bottom - 0.01, end.getBlockZ() - 0.01),
                data, color, 1.02f, (float) (top - bottom + 0.02)));
        double height = cfg.getDouble("course.goal.beam_height", 24);
        if (height > 0) {
            // Starts above head height, so it never hides the landing.
            goal.add(display(new Location(end.getWorld(), end.getX() - 0.1, top + 2.5, end.getZ() - 0.1),
                    data, color, 0.2f, (float) height));
        }
    }

    private static BlockDisplay display(Location corner, BlockData data, Color glow, float width, float height) {
        return corner.getWorld().spawn(corner, BlockDisplay.class, d -> {
            d.setBlock(data);
            d.setPersistent(false);
            d.setGlowing(true);
            d.setGlowColorOverride(glow);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(16f);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(width, height, width), new AxisAngle4f()));
        });
    }

    private void hideGoal() {
        for (BlockDisplay d : goal) d.remove();
        goal.clear();
        goalFor = null;
    }

    /** {@code 41m} and an arrow toward the finish relative to where the player looks. */
    private static String toGoal(Location from, Location end) {
        double dx = end.getX() - from.getX(), dz = end.getZ() - from.getZ();
        double rel = Math.toDegrees(Math.atan2(-dx, dz)) - from.getYaw();
        int i = Math.floorMod(Math.round((float) rel / 45f), 8);
        return String.format("%.0fm %s", Math.hypot(dx, dz), ARROWS[i]);
    }

    private static final String[] ARROWS = {"\u2191", "\u2197", "\u2192", "\u2198", "\u2193", "\u2199", "\u2190", "\u2196"};

    private void tickHuman(HumanSession s) {
        Player p = s.player;
        if (!p.isOnline()) {
            endHuman(false);
            return;
        }
        if (p.getWorld() != s.arena.world()) {
            // Teleported away by something else: let them go, but undo the course state.
            endHuman(false);
            s.saved.restoreExceptLocation(p);
            return;
        }
        p.setFoodLevel(20);
        Location loc = p.getLocation();
        switch (s.phase) {
            case READY -> {
                Location start = s.arena.start();
                double moved = Math.hypot(loc.getX() - start.getX(), loc.getZ() - start.getZ());
                if (moved < 1e-3 && Math.abs(loc.getY() - start.getY()) < 1e-3) {
                    p.sendActionBar(Component.text("Move to start the clock", Messages.HIGHLIGHT)
                            .append(Component.text("   finish " + toGoal(loc, s.arena.end()), Messages.TEXT)));
                    return;
                }
                s.phase = Phase.RUNNING;
                s.log = new CourseLog(logFile(s.course, p.getName()));
                s.log.header(s.course, "human:" + p.getName(), start, s.arena.end(), false);
                runningTick(s, loc);
            }
            case RUNNING -> runningTick(s, loc);
            case DONE -> {
                if (--s.resetIn <= 0) resetHuman();
            }
        }
    }

    private void runningTick(HumanSession s, Location loc) {
        Player p = s.player;
        s.tick++;
        s.log.tick(s.tick, loc, loc.getX() - s.last.getX(), loc.getY() - s.last.getY(), loc.getZ() - s.last.getZ(),
                p.isOnGround(), keys(p) + String.format(" hp=%.1f %s", p.getHealth(), Body.surface(p)));
        s.last = loc;
        p.sendActionBar(Component.text(time(s.tick), Messages.HIGHLIGHT)
                .append(Component.text("   finish " + toGoal(loc, s.arena.end()), Messages.TEXT)));
        String out = s.arena.outOfBounds(loc);
        if (out != null) failHuman(s, out);
        else if (s.arena.finished(loc, p.isOnGround())) finishHuman(s);
        else if (s.tick >= HUMAN_LIMIT) failHuman(s, "too_slow");
    }

    /** The player's keys this tick and view, in the bot log's format. */
    private static String keys(Player p) {
        Input in = p.getCurrentInput();
        StringBuilder k = new StringBuilder();
        if (in.isForward()) k.append('W');
        if (in.isBackward()) k.append('S');
        if (in.isLeft()) k.append('A');
        if (in.isRight()) k.append('D');
        if (k.isEmpty()) k.append('-');
        if (in.isJump()) k.append("+jump");
        if (p.isSprinting()) k.append("+sprint");
        if (in.isSneak()) k.append("+sneak");
        Location l = p.getLocation();
        return String.format("keys=%s yaw=%.2f pitch=%.2f", k, l.getYaw(), l.getPitch());
    }

    private void finishHuman(HumanSession s) {
        Player p = s.player;
        s.phase = Phase.DONE;
        s.resetIn = RESET_DELAY;
        CourseResults r = results(s.course);
        int before = r.best("human");
        s.log.finish("FINISHED", "arrived", s.tick);
        r.add(p.getName(), "human", "FINISHED", s.tick, s.log.file());
        int bot = r.best("bot");
        Mai.getInstance().getLogger().info(String.format("[Course] RESULT name=%s runner=human:%s status=FINISHED ticks=%d human_best=%d bot_best=%d",
                s.course.name(), p.getName(), s.tick, r.best("human"), bot));
        boolean record = before < 0 || s.tick < before;
        p.showTitle(Title.title(Component.text(time(s.tick), Messages.SUCCESS),
                Component.text(record ? "New best" : "Best " + time(before), Messages.TEXT),
                Title.Times.times(java.time.Duration.ZERO, java.time.Duration.ofMillis(1500), java.time.Duration.ofMillis(300))));
        p.playSound(p.getLocation(), record ? Sound.ENTITY_PLAYER_LEVELUP : Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.2f);
        p.sendMessage(Component.text("Finished in " + time(s.tick) + " (" + s.tick + " ticks)", Messages.SUCCESS)
                .append(Component.text(record ? ", a new best" : ", best " + time(before), Messages.TEXT))
                .append(Component.text(bot > 0 ? ". Bot best " + time(bot) + ". " : ". ", Messages.TEXT))
                .append(Component.text("[Discard]", Messages.ERROR)
                        .hoverEvent(HoverEvent.showText(Component.text("Don't count this run", Messages.TEXT)))
                        .clickEvent(ClickEvent.runCommand("/mai course discard"))));
    }

    private void failHuman(HumanSession s, String reason) {
        s.phase = Phase.DONE;
        s.resetIn = RESET_DELAY / 2;
        s.log.finish("FAILED", reason, s.tick);
        s.player.sendMessage(Component.text(switch (reason) {
            case "fell" -> "You fell off. Back to the start.";
            case "left_course" -> "You left the course. Back to the start.";
            case "died" -> "That would have killed you. Back to the start.";
            default -> "Out of time. Back to the start.";
        }, Messages.ERROR));
    }

    // Hooks for CourseListener.

    boolean inCourse(Player p) { return human != null && human.player == p; }

    /** F restarts; sneak + F leaves. */
    void onSwapHands(Player p) {
        if (p.isSneaking()) quit(p);
        else retry(p);
    }

    void onQuit(Player p) { if (inCourse(p)) endHuman(true); }

    void onInteract(Player p) { if (inCourse(p)) human.dirty = true; }

    /** Returns true to cancel the damage. A fatal hit ends the attempt instead of killing. */
    boolean onDamage(Player p, double finalDamage) {
        if (!inCourse(p)) return false;
        if (human.phase != Phase.RUNNING) return true;
        if (finalDamage >= p.getHealth()) {
            failHuman(human, "died");
            return true;
        }
        return false;
    }

    // =========================================================================
    // Hunts: the bot chases a replay of a person's best run
    // =========================================================================

    private static final java.util.regex.Pattern REPLAY =
            java.util.regex.Pattern.compile("^t=\\d+ x=(\\S+) y=(\\S+) z=(\\S+) .*? yaw=(\\S+) pitch=(\\S+)");

    /** Positions and view of the fastest finished human run, one entry per tick; null if there is none. */
    private List<double[]> bestHumanRun(Course c) {
        CourseResults.Attempt best = null;
        for (CourseResults.Attempt a : results(c).all()) {
            if (a.finished() && a.kind().equals("human") && !a.log().isEmpty() && (best == null || a.ticks() < best.ticks())) best = a;
        }
        if (best == null) return null;
        List<double[]> out = new ArrayList<>();
        try {
            for (String line : java.nio.file.Files.readAllLines(new File(new File(runsDir, c.name()), best.log()).toPath())) {
                java.util.regex.Matcher m = REPLAY.matcher(line);
                if (!m.find()) continue;
                out.add(new double[]{Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)), Double.parseDouble(m.group(3)),
                        Double.parseDouble(m.group(4)), Double.parseDouble(m.group(5))});
            }
        } catch (java.io.IOException | NumberFormatException e) {
            return null;
        }
        return out.size() < 20 ? null : out;
    }

    public void hunt(CommandSender sender, String name, int lead) {
        Course c = find(sender, name);
        if (c == null) return;
        leaveOwnCourse(sender);
        if (busy(sender)) return;
        runHunts(sender, List.of(c), lead);
    }

    public void huntAll(CommandSender sender, int lead) {
        leaveOwnCourse(sender);
        if (busy(sender)) return;
        runHunts(sender, new ArrayList<>(courses.values()), lead);
    }

    private void runHunts(CommandSender sender, List<Course> runs, int lead) {
        botBusy = true;
        cancelBots = false;
        Player watcher = sender instanceof Player p ? p : null;
        SavedState saved = watcher != null ? new SavedState(watcher) : null;
        if (watcher != null) watcher.setGameMode(GameMode.SPECTATOR);
        int[] caught = {0}, total = {0};
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (Course c : runs) {
            chain = chain.thenCompose(v -> cancelBots ? CompletableFuture.completedFuture(null)
                    : later(5).thenCompose(x -> runHunt(sender, c, lead, watcher)).thenAccept(r -> {
                        if (r != null) total[0]++;
                        if (Boolean.TRUE.equals(r)) caught[0]++;
                    }));
        }
        chain.whenComplete((v, ex) -> later(watcher != null ? 30 : 0).thenRun(() -> {
            botBusy = false;
            if (ex != null) Mai.getInstance().getLogger().log(java.util.logging.Level.WARNING, "Course hunts aborted", ex);
            Mai.getInstance().getLogger().info(String.format("[Course] HUNT_BATCH caught=%d total=%d lead=%d", caught[0], total[0], lead));
            if (watcher != null && watcher.isOnline()) saved.restore(watcher);
        }));
    }

    /**
     * A Ghost replays the fastest human run of the course tick for tick (a
     * player body moved to the recorded positions; nothing drives it), and
     * {@code lead} ticks after its first step a Hunter spawns at the start
     * and goes after it with the attack behavior. Caught once in swing
     * reach; escaped if the Ghost finishes first. Completes with true
     * (caught), false (escaped or failed) or null (no human run to replay).
     */
    private CompletableFuture<Boolean> runHunt(CommandSender sender, Course c, int lead, Player watcher) {
        List<double[]> run = bestHumanRun(c);
        if (run == null) {
            Mai.getInstance().getLogger().info("[Course] HUNT name=" + c.name() + " status=SKIPPED reason=no_human_run");
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<String> outcome = new CompletableFuture<>();
        CompletableFuture<Boolean> done = new CompletableFuture<>();
        CourseArena arena = arena(c);
        Location start = arena.start();
        World world = start.getWorld();
        if (watcher != null && watcher.isOnline()) {
            Location eye = start.clone().add(start.getDirection().multiply(-4)).add(0, 3, 0);
            eye.setPitch(25);
            watcher.teleport(eye);
        }
        Player ghost = me.advait.mai.body.fake.FakePlayers.spawn("Ghost", UUID.randomUUID(), start);
        ghost.setGravity(false);
        ghost.setInvulnerable(true);
        Humanoid[] hunter = {null};
        CourseLog log = new CourseLog(logFile(c, "hunt"));
        log.header(c, "hunt lead=" + lead, start, arena.end(), true);
        int[] tick = {0}, hunterTicks = {0};
        double[] gap = {Double.NaN, Double.MAX_VALUE};
        Location[] last = {start};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(Mai.getInstance(), () -> {
            if (outcome.isDone()) return;
            int i = tick[0]++;
            if (!ghost.isValid()) {
                if (i > 40) outcome.complete("ghost_gone");
                return;
            }
            if (i >= run.size()) {
                outcome.complete("ESCAPED");
                return;
            }
            double[] r = run.get(i);
            ghost.teleport(new Location(world, r[0], r[1], r[2], (float) r[3], (float) r[4]));
            if (hunter[0] == null && i >= lead) {
                Humanoid h = Catalog.getInstance().registerTemporary("Hunter", start);
                hunter[0] = h;
                h.getInventory().addItem(new org.bukkit.inventory.ItemStack(Material.IRON_SWORD));
                me.advait.mai.assistant.Assistant.of(h).setBehavior(new me.advait.mai.assistant.CombatBehavior(
                        h, ghost, me.advait.mai.assistant.CombatBehavior.Style.SMART));
                return;
            }
            if (hunter[0] == null) return;
            LivingEntity e = hunter[0].peekEntity();
            if (e == null || (!e.isValid() && hunterTicks[0] < 40)) return;
            if (e.isDead() || !e.isValid()) {
                outcome.complete("hunter_died");
                return;
            }
            int t = ++hunterTicks[0];
            Location loc = e.getLocation();
            Body b = Body.of(e);
            gap[0] = loc.distance(ghost.getLocation());
            gap[1] = Math.min(gap[1], gap[0]);
            log.tick(t, loc, loc.getX() - last[0].getX(), loc.getY() - last[0].getY(), loc.getZ() - last[0].getZ(), e.isOnGround(),
                    b.lastTick() + String.format(" hp=%.1f hc=%s %s gap=%.2f %s", e.getHealth(), b.collision(), Body.surface(e), gap[0],
                            me.advait.mai.assistant.Assistant.of(hunter[0]).activity()));
            last[0] = loc;
            String out = arena.outOfBounds(loc);
            if (out != null) outcome.complete("hunter_" + out);
            else if (me.advait.mai.assistant.Motor.reachDistance(e, ghost) <= 3.0) outcome.complete("CAUGHT");
        }, 1L, 1L);
        outcome.whenComplete((r, ex) -> Bukkit.getScheduler().runTask(Mai.getInstance(), () -> {
            task.cancel();
            boolean caught = "CAUGHT".equals(r);
            try {
                if (hunter[0] != null) {
                    me.advait.mai.assistant.Assistant.of(hunter[0]).stopAll();
                    me.advait.mai.assistant.Assistant.forget(hunter[0].getUuid());
                    hunter[0].getActionAgent().cancelAll();
                    Catalog.getInstance().unregister(hunter[0]);
                }
                me.advait.mai.body.fake.FakePlayers.remove(ghost);
            } catch (Exception ignored) {
            }
            String status = caught ? "CAUGHT" : "ESCAPED".equals(r) ? "ESCAPED" : "FAILED";
            log.finish(status, r == null ? "error" : r.toLowerCase(Locale.ROOT), hunterTicks[0]);
            String line = String.format("[Course] HUNT name=%s status=%s ticks=%d lead=%d human_ticks=%d gap=%.1f closest=%.1f reason=%s",
                    c.name(), status, hunterTicks[0], lead, run.size(), gap[0], gap[1], r == null ? "error" : r.toLowerCase(Locale.ROOT));
            Mai.getInstance().getLogger().info(line);
            if (sender instanceof Player) {
                sender.sendMessage(Component.text(c.name() + " ", Messages.HIGHLIGHT)
                        .append(caught ? Component.text("caught your best run after " + time(hunterTicks[0]), Messages.SUCCESS)
                                : Component.text("your best run got away (" + String.format("%.1f", gap[0]) + " blocks ahead)", Messages.ERROR)));
            }
            done.complete(caught);
        }));
        return done;
    }

    // =========================================================================
    // Bot runs
    // =========================================================================

    public void bot(CommandSender sender, String name, int times) {
        Course c = find(sender, name);
        if (c == null) return;
        leaveOwnCourse(sender);
        if (busy(sender)) return;
        List<Course> runs = new ArrayList<>();
        for (int i = 0; i < Math.max(1, times); i++) runs.add(c);
        runBots(sender, runs);
    }

    public void botAll(CommandSender sender, int times) {
        leaveOwnCourse(sender);
        if (busy(sender)) return;
        List<Course> runs = new ArrayList<>();
        for (Course c : courses.values()) for (int i = 0; i < Math.max(1, times); i++) runs.add(c);
        runBots(sender, runs);
    }

    /** A player who starts a bot run while on a course leaves it first, so they can watch. */
    private void leaveOwnCourse(CommandSender sender) {
        if (sender instanceof Player p && human != null && human.player == p) endHuman(true);
    }

    public void cancel(CommandSender sender) {
        cancelBots = true;
        Messages.sendMessage(sender, botBusy ? "&aStopping after the current bot run." : "&fNo bot run to cancel.");
    }

    private void runBots(CommandSender sender, List<Course> runs) {
        botBusy = true;
        cancelBots = false;
        // A player who starts the run watches it in spectator mode, then goes back.
        Player watcher = sender instanceof Player p ? p : null;
        SavedState saved = watcher != null ? new SavedState(watcher) : null;
        if (watcher != null) watcher.setGameMode(GameMode.SPECTATOR);
        int[] finished = {0};
        long t0 = System.currentTimeMillis();
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (Course c : runs) {
            chain = chain.thenCompose(v -> cancelBots ? CompletableFuture.completedFuture(null)
                    : later(5).thenCompose(x -> runBot(sender, c, watcher)).thenAccept(ok -> { if (ok) finished[0]++; }));
        }
        chain.whenComplete((v, ex) -> later(watcher != null ? 30 : 0).thenRun(() -> {
            botBusy = false;
            if (ex != null) Mai.getInstance().getLogger().log(java.util.logging.Level.WARNING, "Course bot runs aborted", ex);
            Mai.getInstance().getLogger().info(String.format("[Course] BATCH finished=%d total=%d elapsed_ms=%d",
                    finished[0], runs.size(), System.currentTimeMillis() - t0));
            if (watcher != null && watcher.isOnline()) saved.restore(watcher);
        }));
    }

    private CompletableFuture<Void> later(long ticks) {
        CompletableFuture<Void> f = new CompletableFuture<>();
        if (ticks <= 0) {
            Bukkit.getScheduler().runTask(Mai.getInstance(), () -> f.complete(null));
        } else {
            Bukkit.getScheduler().runTaskLater(Mai.getInstance(), () -> f.complete(null), ticks);
        }
        return f;
    }

    /** One bot attempt; completes with whether it finished. */
    private CompletableFuture<Boolean> runBot(CommandSender sender, Course c, Player watcher) {
        CompletableFuture<String> outcome = new CompletableFuture<>();
        CompletableFuture<Boolean> done = new CompletableFuture<>();
        CourseArena arena = arena(c);
        Location start = arena.start();
        if (watcher != null && watcher.isOnline()) {
            Location eye = start.clone().add(start.getDirection().multiply(-4)).add(0, 3, 0);
            eye.setPitch(25);
            watcher.teleport(eye);
        }
        Humanoid h = Catalog.getInstance().registerTemporary(BOT_NAME, start);
        CourseLog log = new CourseLog(logFile(c, "bot"));
        log.header(c, "bot", start, arena.end(), true);
        int[] tick = {0}, moved = {-1};
        UUID[] body = {null};
        Location[] last = {start};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(Mai.getInstance(), () -> {
            if (outcome.isDone()) return;
            tick[0]++;
            LivingEntity e = h.peekEntity();
            if (e == null || (!e.isValid() && tick[0] < 40)) return;
            if (body[0] == null) body[0] = e.getUniqueId();
            if (e.isDead() || !e.isValid() || !e.getUniqueId().equals(body[0])) {
                outcome.complete("died");
                return;
            }
            Location loc = e.getLocation();
            if (moved[0] < 0) {
                if (Math.hypot(loc.getX() - start.getX(), loc.getZ() - start.getZ()) < 1e-3
                        && Math.abs(loc.getY() - start.getY()) < 0.05) {
                    if (tick[0] > 400) outcome.complete("never_moved");
                    return;
                }
                moved[0] = tick[0];
            }
            int t = tick[0] - moved[0] + 1;
            Body b = Body.of(e);
            log.tick(t, loc, loc.getX() - last[0].getX(), loc.getY() - last[0].getY(), loc.getZ() - last[0].getZ(),
                    e.isOnGround(), b.lastTick() + String.format(" hp=%.1f hc=%s %s", e.getHealth(), b.collision(), Body.surface(e)));
            last[0] = loc;
            String out = arena.outOfBounds(loc);
            if (out != null) outcome.complete(out);
            else if (arena.finished(loc, e.isOnGround())) outcome.complete("FINISHED:" + t);
            else if (t >= c.timeoutTicks()) outcome.complete("timeout");
        }, 1L, 1L);
        // The clock stops on the first tick the feet are over the finish block, for a person and the bot
        // alike, so the bot runs onto it like a person does instead of braking to stand on its center.
        h.getActionAgent().addActions(new HumanoidWalkToAction(h, arena.end(), FINISH_RADIUS)).whenComplete((res, ex) -> {
            if (ex != null) outcome.complete("no_path");
            else if (res != null && !res.success()) {
                String msg = res.message() == null ? "" : res.message().toLowerCase(Locale.ROOT);
                if (msg.contains("path") || msg.contains("unreachable")) outcome.complete("no_path");
            }
        });
        outcome.whenComplete((r, ex) -> Bukkit.getScheduler().runTask(Mai.getInstance(), () -> {
            task.cancel();
            boolean ok = r != null && r.startsWith("FINISHED:");
            int ticks = ok ? Integer.parseInt(r.substring(9)) : Math.max(0, tick[0] - Math.max(moved[0], 0));
            String reason = ok ? "arrived" : r == null ? "error" : r;
            try {
                h.getActionAgent().cancelAll();
                Catalog.getInstance().unregister(h);
            } catch (Exception ignored) {
            }
            log.finish(ok ? "FINISHED" : "FAILED", reason, ticks);
            CourseResults res = results(c);
            res.add("bot", "bot", ok ? "FINISHED" : "FAILED", ticks, log.file());
            int you = res.best("human");
            List<Integer> humans = res.finishedTicks("human");
            String line = String.format("[Course] RESULT name=%s runner=bot status=%s ticks=%d reason=%s human_best=%d beats=%s",
                    c.name(), ok ? "FINISHED" : "FAILED", ticks, reason, you, ok ? beats(ticks, humans) : "-");
            Mai.getInstance().getLogger().info(line);
            if (sender instanceof Player) {
                sender.sendMessage(Component.text(c.name() + " ", Messages.HIGHLIGHT)
                        .append(ok ? Component.text("bot finished in " + time(ticks), Messages.SUCCESS)
                                : Component.text("bot failed: " + reason.replace('_', ' '), Messages.ERROR))
                        .append(Component.text(you > 0 ? " (human best " + time(you) + ")" : "", Messages.TEXT))
                        .hoverEvent(HoverEvent.showText(Component.text(line, Messages.TEXT))));
            }
            done.complete(ok);
        }));
        return done;
    }
}
