package me.advait.mai.body;

import de.bsommerfeld.pathetic.bukkit.provider.FailingNavigationPointProvider;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The humanoid's right click, under a player's rules. Every block placement
 * and bucket use goes through here:
 * <ul>
 *   <li>The view is turned onto the exact point being clicked, and a ray
 *       from the eyes along the view must hit that block and face first
 *       ({@code Player.pick}), within the 4.5-block interaction range.
 *   <li>A placed block must go into a replaceable cell and not overlap any
 *       entity's hitbox, the bot's own included ({@code Level.isUnobstructed}).
 *   <li>Using an item starts vanilla's 4-tick right-click delay
 *       ({@code Minecraft.rightClickDelay}).
 * </ul>
 * Anything that fails a check doesn't happen, and the caller is told so.
 */
public final class Hands {

    /** Player block interaction range ({@code Attributes.BLOCK_INTERACTION_RANGE} default). */
    public static final double REACH = 4.5;
    /** Ticks between right clicks ({@code Minecraft.rightClickDelay}). */
    public static final int USE_DELAY = 4;

    private static final Map<UUID, Integer> LAST_USE = new ConcurrentHashMap<>();

    private Hands() {}

    /** Whether the right-click delay has passed since this entity's last use. */
    public static boolean ready(LivingEntity e) {
        Integer last = LAST_USE.get(e.getUniqueId());
        return last == null || Bukkit.getCurrentTick() - last >= USE_DELAY;
    }

    private static void used(LivingEntity e) {
        LAST_USE.put(e.getUniqueId(), Bukkit.getCurrentTick());
        e.swingMainHand();
    }

    /** Turns the view onto {@code point}. */
    public static void aim(LivingEntity e, Vector point) {
        Location eye = e.getEyeLocation();
        double dx = point.getX() - eye.getX(), dy = point.getY() - eye.getY(), dz = point.getZ() - eye.getZ();
        double h = Math.sqrt(dx * dx + dz * dz);
        Body.of(e).look(Body.yawOf(dx, dz), (float) -Math.toDegrees(Math.atan2(dy, h)));
    }

    /** What the eyes see along the current view, within reach. */
    public static RayTraceResult pick(LivingEntity e, FluidCollisionMode fluids) {
        Location eye = e.getEyeLocation();
        return e.getWorld().rayTraceBlocks(eye, eye.getDirection(), REACH, fluids, true);
    }

    /** Center of a block face, nudged a hair into the block so a ray aimed at it lands on that face. */
    public static Vector faceCenter(Block block, BlockFace face) {
        return new Vector(block.getX() + 0.5 + face.getModX() * 0.499,
                block.getY() + 0.5 + face.getModY() * 0.499,
                block.getZ() + 0.5 + face.getModZ() * 0.499);
    }

    /**
     * Whether, aiming at {@code point} on {@code face} of {@code block}, the
     * eyes would actually hit that face first. Doesn't turn the view.
     */
    public static boolean canSee(LivingEntity e, Block block, BlockFace face, Vector point) {
        Location eye = e.getEyeLocation();
        Vector dir = point.clone().subtract(eye.toVector());
        if (dir.length() > REACH) return false;
        RayTraceResult hit = e.getWorld().rayTraceBlocks(eye, dir.normalize(), REACH, FluidCollisionMode.NEVER, true);
        return hit != null && block.equals(hit.getHitBlock()) && hit.getHitBlockFace() == face;
    }

    /**
     * Places the held block against {@code face} of {@code against}: aims at
     * the face, checks the eyes hit it, that the cell beyond is free of
     * blocks and hitboxes, and the click delay. Decrements the held stack.
     */
    public static boolean placeBlock(LivingEntity e, Block against, BlockFace face) {
        ItemStack held = e.getEquipment() != null ? e.getEquipment().getItemInMainHand() : null;
        if (held == null || !held.getType().isBlock() || held.getType().isAir() || held.getAmount() <= 0) return false;
        if (!ready(e)) return false;
        Block target = against.getRelative(face);
        if (!replaceable(target)) return false;
        if (obstructed(target)) return false;
        Vector point = faceCenter(against, face);
        if (!canSee(e, against, face, point)) return false;

        aim(e, point);
        target.setType(held.getType());
        held.setAmount(held.getAmount() - 1);
        e.getEquipment().setItemInMainHand(held.getAmount() > 0 ? held : null);
        used(e);
        invalidate(target);
        return true;
    }

    /**
     * Empties the held water bucket where the eyes look along the current
     * view: into the cell in front of the block face hit, like a player's
     * click. The held item becomes an empty bucket.
     */
    public static Block emptyBucket(LivingEntity e) {
        ItemStack held = e.getEquipment() != null ? e.getEquipment().getItemInMainHand() : null;
        if (held == null || held.getType() != Material.WATER_BUCKET || !ready(e)) return null;
        RayTraceResult hit = pick(e, FluidCollisionMode.SOURCE_ONLY);
        if (hit == null || hit.getHitBlock() == null || hit.getHitBlockFace() == null) return null;
        Block target = hit.getHitBlock().getType() == Material.WATER
                ? hit.getHitBlock() : hit.getHitBlock().getRelative(hit.getHitBlockFace());
        if (!replaceable(target) && target.getType() != Material.WATER) return null;
        target.setType(Material.WATER);
        e.getEquipment().setItemInMainHand(new ItemStack(Material.BUCKET));
        used(e);
        invalidate(target);
        return target;
    }

    /** Fills the held empty bucket from the water source the eyes look at. */
    public static boolean fillBucket(LivingEntity e) {
        ItemStack held = e.getEquipment() != null ? e.getEquipment().getItemInMainHand() : null;
        if (held == null || held.getType() != Material.BUCKET || !ready(e)) return false;
        RayTraceResult hit = pick(e, FluidCollisionMode.SOURCE_ONLY);
        if (hit == null || hit.getHitBlock() == null || hit.getHitBlock().getType() != Material.WATER) return false;
        Block water = hit.getHitBlock();
        if (!(water.getBlockData() instanceof org.bukkit.block.data.Levelled lv) || lv.getLevel() != 0) return false;
        water.setType(Material.AIR);
        if (held.getAmount() > 1) {
            held.setAmount(held.getAmount() - 1);
            e.getEquipment().setItemInMainHand(held);
        } else {
            e.getEquipment().setItemInMainHand(new ItemStack(Material.WATER_BUCKET));
        }
        used(e);
        invalidate(water);
        return true;
    }

    /** Air, a fluid, or a block a placement replaces (grass, flowers, snow layer...). */
    public static boolean replaceable(Block b) {
        return b.getType().isAir() || b.isLiquid() || b.isReplaceable();
    }

    /** Whether any living entity's hitbox overlaps the block cell. */
    public static boolean obstructed(Block b) {
        BoundingBox cell = BoundingBox.of(b);
        for (Entity other : b.getWorld().getNearbyEntities(cell)) {
            if (other instanceof LivingEntity && other.getBoundingBox().overlaps(cell)) return true;
        }
        return false;
    }

    private static void invalidate(Block b) {
        FailingNavigationPointProvider.invalidateChunk(b.getWorld().getUID(), b.getX() >> 4, b.getZ() >> 4);
    }

    public static void forget(LivingEntity e) {
        LAST_USE.remove(e.getUniqueId());
    }
}
