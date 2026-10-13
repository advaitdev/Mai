package me.advait.mai.assistant;

import me.advait.mai.Catalog;
import me.advait.mai.Mai;
import me.advait.mai.body.Humanoid;
import me.advait.mai.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

/**
 * Headless PvP benchmark: two humanoids fight on a flat platform in the
 * obstacle world, one per {@link CombatBehavior.Style}, for a number of
 * rounds. Prints machine-parseable lines like the obstacle harness:
 *
 * <pre>
 * [Duel] RESULT round=1 winner=A(smart) ticks=212 A=hits:9,crits:4,dmg:20.0,hp:7.5 B=...
 * [Duel] SERIES rounds=10 A(smart)=8 B(basic)=2 draws=0
 * </pre>
 *
 * Sides swap every round so spawn position can't bias the result.
 */
public final class Duel {

    private static final int PLATFORM_RADIUS = 12;
    private static final int TIMEOUT_TICKS = 1200;
    private static final int ORIGIN_X = 400, ORIGIN_Z = 0, FLOOR_Y = 120;

    private static boolean running = false;

    private Duel() {}

    public static void start(CommandSender sender, int rounds, CombatBehavior.Style a, CombatBehavior.Style b, Material weapon) {
        if (running) {
            Messages.sendMessage(sender, "&cA duel is already running.");
            return;
        }
        World world = Bukkit.getWorld(Mai.getInstance().getSettingsFile().getConfiguration()
                .getString("obstacle.world.name", "obstacles"));
        if (world == null) world = Bukkit.getWorlds().getFirst();
        running = true;
        buildPlatform(world);
        int[] wins = new int[3];   // A, B, draw
        World w = world;
        // Let freshly loaded platform chunks finish loading entities first.
        Bukkit.getScheduler().runTaskLater(Mai.getInstance(),
                () -> runRound(sender, w, 1, rounds, a, b, weapon, wins), 40L);
    }

    private static void runRound(CommandSender sender, World world, int round, int rounds,
                                 CombatBehavior.Style a, CombatBehavior.Style b, Material weapon, int[] wins) {
        if (round > rounds) {
            String line = String.format("[Duel] SERIES rounds=%d A(%s)=%d B(%s)=%d draws=%d",
                    rounds, lc(a), wins[0], lc(b), wins[1], wins[2]);
            report(sender, line);
            clearPlatform(world);
            running = false;
            return;
        }
        boolean swap = round % 2 == 0;
        Location west = new Location(world, ORIGIN_X - 4 + 0.5, FLOOR_Y + 1, ORIGIN_Z + 0.5, -90, 0);
        Location east = new Location(world, ORIGIN_X + 4 + 0.5, FLOOR_Y + 1, ORIGIN_Z + 0.5, 90, 0);
        // The server ticks entities in spawn order, and the one that moves
        // first also swings first, so alternate who spawns first as well.
        Humanoid ha, hb;
        if ((round / 2) % 2 == 0) {
            ha = Catalog.getInstance().register("DuelA", swap ? east : west);
            hb = Catalog.getInstance().register("DuelB", swap ? west : east);
        } else {
            hb = Catalog.getInstance().register("DuelB", swap ? west : east);
            ha = Catalog.getInstance().register("DuelA", swap ? east : west);
        }
        for (Humanoid h : new Humanoid[]{ha, hb}) {
            h.getRawMannequin().getEquipment().setItemInMainHand(new ItemStack(weapon));
        }
        // Watch the original bodies: Humanoid.getEntity() respawns a dead
        // mannequin, so the live reference could hide a death.
        LivingEntity ea = ha.getRawMannequin(), eb = hb.getRawMannequin();
        CombatBehavior ca = new CombatBehavior(ha, hb.getRawMannequin(), a);
        CombatBehavior cb = new CombatBehavior(hb, ha.getRawMannequin(), b);
        Assistant.of(ha).setBehavior(ca);
        Assistant.of(hb).setBehavior(cb);

        int[] ticks = {0};
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(Mai.getInstance(), () -> {
            ticks[0]++;
            boolean aDown = ea == null || ea.isDead() || !ea.isValid() || ea.getLocation().getY() < FLOOR_Y - 3;
            boolean bDown = eb == null || eb.isDead() || !eb.isValid() || eb.getLocation().getY() < FLOOR_Y - 3;
            if (!aDown && !bDown && ticks[0] < TIMEOUT_TICKS) return;
            task[0].cancel();

            String winner;
            if (aDown && !bDown) { winner = "B(" + lc(b) + ")"; wins[1]++; }
            else if (bDown && !aDown) { winner = "A(" + lc(a) + ")"; wins[0]++; }
            else { winner = "draw"; wins[2]++; }
            report(sender, String.format("[Duel] RESULT round=%d winner=%s ticks=%d A=%s B=%s",
                    round, winner, ticks[0], stats(ca, ea), stats(cb, eb)));

            Assistant.of(ha).stopAll();
            Assistant.of(hb).stopAll();
            Assistant.forget(ha.getUuid());
            Assistant.forget(hb.getUuid());
            Catalog.getInstance().unregister(ha);
            Catalog.getInstance().unregister(hb);
            Bukkit.getScheduler().runTaskLater(Mai.getInstance(),
                    () -> runRound(sender, world, round + 1, rounds, a, b, weapon, wins), 20L);
        }, 1L, 1L);
    }

    private static String stats(CombatBehavior c, LivingEntity e) {
        double hp = e == null || e.isDead() ? 0 : e.getHealth();
        return String.format("hits:%d,crits:%d,sprint_hits:%d,swings:%d,dmg:%.1f,hp:%.1f",
                c.hits(), c.crits(), c.sprintHits(), c.swings(), c.damageDealt(), hp);
    }

    private static void report(CommandSender sender, String line) {
        Mai.getInstance().getLogger().info(line);
        if (!(sender instanceof org.bukkit.command.ConsoleCommandSender)) Messages.sendMessage(sender, "&7" + line);
    }

    private static String lc(CombatBehavior.Style s) {
        return s.name().toLowerCase();
    }

    private static void buildPlatform(World world) {
        for (int cx = (ORIGIN_X - PLATFORM_RADIUS) >> 4; cx <= (ORIGIN_X + PLATFORM_RADIUS) >> 4; cx++) {
            for (int cz = (ORIGIN_Z - PLATFORM_RADIUS) >> 4; cz <= (ORIGIN_Z + PLATFORM_RADIUS) >> 4; cz++) {
                world.getChunkAt(cx, cz);
                world.addPluginChunkTicket(cx, cz, Mai.getInstance());
            }
        }
        forPlatform(world, Material.SMOOTH_STONE, Material.AIR);
    }

    private static void clearPlatform(World world) {
        forPlatform(world, Material.AIR, Material.AIR);
    }

    private static void forPlatform(World world, Material floor, Material above) {
        for (int x = -PLATFORM_RADIUS; x <= PLATFORM_RADIUS; x++) {
            for (int z = -PLATFORM_RADIUS; z <= PLATFORM_RADIUS; z++) {
                world.getBlockAt(ORIGIN_X + x, FLOOR_Y, ORIGIN_Z + z).setType(floor, false);
                for (int y = 1; y <= 4; y++) {
                    world.getBlockAt(ORIGIN_X + x, FLOOR_Y + y, ORIGIN_Z + z).setType(above, false);
                }
            }
        }
    }
}
