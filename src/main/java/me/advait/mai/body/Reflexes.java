package me.advait.mai.body;

import me.advait.mai.Catalog;
import me.advait.mai.brain.action.mechanic.movement.runnable.HumanoidWalkToRunnable;
import me.advait.mai.pathetic.debug.PathDebugLog;
import me.advait.mai.pathetic.movement.types.BridgePlace;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Survival reflexes for falls nobody planned: knocked off an edge, a missed
 * jump, the floor broken underneath. Every tick, a falling humanoid checks
 * how the fall ends, and if it would hurt or kill:
 * <ul>
 *   <li><b>Water-bucket clutch</b>: with a bucket and solid ground below,
 *       look down and empty it on the ground once it's in reach (the same
 *       click a planned {@code water_clutch} makes).
 *   <li><b>Block clutch</b>: when the fall is lethal or bottomless, place a
 *       block in the cell under the feet against any side it can see (often
 *       the edge it just fell off), so it lands a block down instead.
 * </ul>
 * All clicks go through {@link Hands}: reach, line of sight to the face,
 * the 4-tick click delay. Runs before the movement controllers each tick;
 * planned falls the path already handles are left alone.
 */
public final class Reflexes {

    /** Vanilla: damage = ceil(fallDistance - safe_fall_distance (3)). */
    private static final double SAFE_FALL = 3.0;
    /** How far below to look for ground. */
    private static final int SCAN_DEPTH = 64;
    private static final double EYE = 1.62;

    private Reflexes() {}

    public static void start(Plugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, Reflexes::tickAll, 1L, 1L);
    }

    /** Movements that leave the ground on purpose; a fall during one is planned. */
    private static final java.util.Set<String> PLANNED_AIRBORNE = java.util.Set.of(
            "sprint_jump", "fall_safe", "fall_unsafe", "fall_into_water", "water_clutch",
            "pillar_up", "step_up", "step_down", "mine_step_up");

    /** Bodies a reflex is driving this tick; their walk controller stands down. */
    private static final java.util.Set<java.util.UUID> ACTIVE = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** Where each body last stood on solid ground: what an unplanned fall steers back toward. */
    private static final java.util.Map<java.util.UUID, org.bukkit.Location> LAST_GROUND = new java.util.concurrent.ConcurrentHashMap<>();

    /** Bodies a reflex took over since their walk last checked: the path is stale. */
    private static final java.util.Set<java.util.UUID> RESCUED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Whether a reflex has the controls of this humanoid this tick. */
    public static boolean isActive(Humanoid h) {
        LivingEntity e = h.peekEntity();
        return e != null && ACTIVE.contains(e.getUniqueId());
    }

    /**
     * Whether a reflex took over this humanoid since the last call (and
     * clears it). A rescue leaves the bot off its path, so the walk replans
     * instead of following the old waypoints back over the void.
     */
    public static boolean takeRescued(Humanoid h) {
        LivingEntity e = h.peekEntity();
        return e != null && RESCUED.remove(e.getUniqueId());
    }

    private static void tickAll() {
        ACTIVE.clear();
        for (Humanoid h : Catalog.getInstance().getAllHumanoids()) {
            LivingEntity e = h.peekEntity();
            if (e == null || !e.isValid() || e.isDead()) continue;
            tick(h, e);
        }
    }

    /** Water a reflex clutch placed, to scoop back up after landing. */
    private static final java.util.Map<java.util.UUID, Block> PUDDLES = new java.util.concurrent.ConcurrentHashMap<>();

    private static void tick(Humanoid h, LivingEntity e) {
        Block puddle = PUDDLES.get(e.getUniqueId());
        if (puddle != null && (e.isOnGround() || e.isInWater()) && Hands.ready(e)) {
            if (puddle.getType() != Material.WATER || e.getLocation().distanceSquared(puddle.getLocation().add(0.5, 0, 0.5)) > 9) {
                PUDDLES.remove(e.getUniqueId());
            } else {
                Hands.aim(e, Hands.faceCenter(puddle, BlockFace.DOWN).setY(puddle.getY() + 0.5));
                if (Hands.fillBucket(e)) {
                    PathDebugLog.event("REFLEX_WATER_PICKUP");
                    PUDDLES.remove(e.getUniqueId());
                }
            }
        }
        if (e.isOnGround() && !e.isInWater()) LAST_GROUND.put(e.getUniqueId(), e.getLocation());
        if (e.isOnGround() || e.isInWater()) return;
        String moving = HumanoidWalkToRunnable.activeMovement(h);
        if (moving != null && PLANNED_AIRBORNE.contains(moving)) return;

        double feet = e.getLocation().getY();
        Block ground = groundBelow(e);
        // Still rising over solid ground: nothing to save yet. Over void, a
        // chain clutch has to start while still going up.
        if (e.getVelocity().getY() >= 0 && ground != null) return;
        double fall = e.getFallDistance() + (ground == null ? Double.MAX_VALUE : feet - (ground.getY() + 1));
        double damage = Math.ceil(fall - SAFE_FALL);
        boolean lethal = ground == null || damage >= e.getHealth();

        if (ground != null && damage >= 2 && hasItem(h, Material.WATER_BUCKET)) {
            ACTIVE.add(e.getUniqueId());
            RESCUED.add(e.getUniqueId());
            steerBack(e, 90f);
            double above = feet - (ground.getY() + 1);
            if (above > 0.05 && above <= Hands.REACH - EYE - 0.05 && Hands.ready(e)) {
                hold(h, Material.WATER_BUCKET);
                Body.of(e).look(e.getLocation().getYaw(), 90f);
                Block water = Hands.emptyBucket(e);
                PathDebugLog.event("REFLEX_WATER_CLUTCH above=%.2f fall=%.1f ok=%s", above, fall, water != null);
                if (water != null) PUDDLES.put(e.getUniqueId(), water);
            }
            return;
        }
        if (lethal) {
            ACTIVE.add(e.getUniqueId());
            RESCUED.add(e.getUniqueId());
            if (Hands.ready(e) && BridgePlace.ensurePlaceableInHand(h) != null) {
                if (!chainClutch(e)) blockClutch(h, e);
            }
            steerBack(e, Float.NaN);
        }
    }

    /**
     * Chain clutch: predict where the fall crosses the level the bot fell
     * from (vanilla air physics, no input) and build toward that point
     * along that level, one block per click: each tick place the block
     * nearest the landing point that attaches to an existing block's side
     * and that the eyes can see. Started while still rising, a few clicks
     * reach out to where a knockback carries the bot. False when there is
     * no level to build on (already below it, or no known ground).
     */
    private static boolean chainClutch(LivingEntity e) {
        org.bukkit.Location home = LAST_GROUND.get(e.getUniqueId());
        org.bukkit.Location loc = e.getLocation();
        if (home == null || !home.getWorld().equals(loc.getWorld())) return false;
        int level = (int) Math.floor(home.getY() + 0.001);
        if (loc.getY() < level - 0.05) return false;

        double x = loc.getX(), y = loc.getY(), z = loc.getZ();
        var v = e.getVelocity();
        double vx = v.getX(), vy = v.getY(), vz = v.getZ();
        boolean crossed = false;
        for (int k = 0; k < 80; k++) {
            x += vx; y += vy; z += vz;
            vx *= 0.91; vz *= 0.91;
            vy = (vy - 0.08) * 0.98;
            if (vy < 0 && y <= level) { crossed = true; break; }
        }
        if (!crossed) return false;
        int by = level - 1;
        int tx = (int) Math.floor(x), tz = (int) Math.floor(z);
        int hx = home.getBlockX(), hz = home.getBlockZ();

        java.util.List<int[]> cells = new java.util.ArrayList<>();
        for (int cx = Math.min(hx, tx) - 1; cx <= Math.max(hx, tx) + 1; cx++) {
            for (int cz = Math.min(hz, tz) - 1; cz <= Math.max(hz, tz) + 1; cz++) {
                cells.add(new int[]{cx, cz});
            }
        }
        cells.sort(java.util.Comparator.comparingInt(c -> (c[0] - tx) * (c[0] - tx) + (c[1] - tz) * (c[1] - tz)));
        for (int[] c : cells) {
            Block cell = e.getWorld().getBlockAt(c[0], by, c[1]);
            if (!Hands.replaceable(cell)) continue;
            for (BlockFace side : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                Block against = cell.getRelative(side);
                if (!against.getType().isSolid()) continue;
                if (Hands.placeBlock(e, against, side.getOppositeFace())) {
                    PathDebugLog.event("REFLEX_CHAIN_CLUTCH at (%d,%d,%d) landing=(%d,%d) feet=%.2f",
                            c[0], by, c[1], tx, tz, loc.getY());
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Air control back toward the last ground: face it and hold W, which
     * eats into the fling (like a player pulling back toward the edge).
     * {@code pitch} NaN keeps the current pitch.
     */
    private static void steerBack(LivingEntity e, float pitch) {
        org.bukkit.Location home = LAST_GROUND.get(e.getUniqueId());
        org.bukkit.Location loc = e.getLocation();
        float p = Float.isNaN(pitch) ? loc.getPitch() : pitch;
        if (home == null || !home.getWorld().equals(loc.getWorld())) {
            Body.of(e).look(loc.getYaw(), p);
            return;
        }
        double dx = home.getX() - loc.getX(), dz = home.getZ() - loc.getZ();
        boolean far = dx * dx + dz * dz > 0.04;
        float yaw = far ? Body.yawOf(dx, dz) : loc.getYaw();
        Body.of(e).look(yaw, p).keys(far ? 1 : 0, 0).sprint(far);
    }

    /** Top solid block under the hitbox center, or null (void) within the scan depth. */
    private static Block groundBelow(LivingEntity e) {
        Block b = e.getLocation().getBlock();
        for (int i = 0; i < SCAN_DEPTH && b.getY() > e.getWorld().getMinHeight(); i++) {
            b = b.getRelative(BlockFace.DOWN);
            if (b.getType().isSolid() || b.isLiquid()) return b.isLiquid() ? null : b;
        }
        return null;
    }

    private static final BlockFace[] SIDES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.DOWN};

    /**
     * Places a block in a cell under the hitbox (center first, then the
     * corners; this level, then the next one down) against any solid
     * neighbour whose face the eyes can see.
     */
    private static void blockClutch(Humanoid h, LivingEntity e) {
        var loc = e.getLocation();
        double[][] offsets = {{0, 0}, {-0.29, -0.29}, {-0.29, 0.29}, {0.29, -0.29}, {0.29, 0.29}};
        for (int down = 1; down <= 2; down++) {
            int cy = (int) Math.floor(loc.getY()) - down;
            for (double[] o : offsets) {
                Block cell = e.getWorld().getBlockAt(
                        (int) Math.floor(loc.getX() + o[0]), cy, (int) Math.floor(loc.getZ() + o[1]));
                if (!Hands.replaceable(cell)) continue;
                for (BlockFace side : SIDES) {
                    Block against = cell.getRelative(side);
                    if (!against.getType().isSolid()) continue;
                    if (Hands.placeBlock(e, against, side.getOppositeFace())) {
                        PathDebugLog.event("REFLEX_BLOCK_CLUTCH at (%d,%d,%d) feet=%.2f",
                                cell.getX(), cell.getY(), cell.getZ(), loc.getY());
                        return;
                    }
                }
            }
        }
    }

    private static boolean hasItem(Humanoid h, Material m) {
        if (h.getEquipment() != null && h.getEquipment().getItemInMainHand().getType() == m) return true;
        return h.getInventory().contains(m);
    }

    private static void hold(Humanoid h, Material m) {
        if (h.getEquipment() == null || h.getEquipment().getItemInMainHand().getType() == m) return;
        ItemStack[] inv = h.getInventory().getContents();
        for (int i = 0; i < inv.length; i++) {
            if (inv[i] != null && inv[i].getType() == m) {
                h.setItemInMainHand(i);
                return;
            }
        }
    }
}
