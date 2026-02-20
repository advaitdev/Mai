package me.advait.mai.npc;

import me.advait.mai.Mai;
import me.advait.mai.util.LocationUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class HumanoidUtil {

    public static boolean jump(LivingEntity entity, double height) {
        if (entity.isOnGround()) {
            entity.setVelocity(entity.getVelocity().add(new Vector(0, height, 0)));
            return true;
        }
        return false;
    }

    public static boolean pileUp(LivingEntity entity) {
        HumanoidUtil.jump(entity, 0.5);
        AtomicBoolean didPlace = new AtomicBoolean(false);
        Bukkit.getScheduler().runTaskLater(JavaPlugin.getPlugin(Mai.class), () -> {
            didPlace.set(HumanoidUtil.placeBlockUnderFeet(entity, Material.STONE));
        }, 6);
        return didPlace.get();
    }

    public static boolean bridgeTowardsTarget(LivingEntity entity, Location target, Material material) {
        Location entityLocation = entity.getLocation();
        if (entityLocation.getY() == target.getY() && entityLocation.distance(target) > 1) {
            LocationUtil.faceLocation(entity, target);
            Location frontLocation = getBlockInFrontAtFootLevel(entity).getLocation();
            LocationUtil.faceLocation(entity, frontLocation);
            if (canPlaceBlock(entity, frontLocation)) {
                placeBlock(entity, frontLocation, material);
                return true;
            }
        }
        return false;
    }

    public static boolean mineTowardsTarget(LivingEntity entity, Location target, ItemStack tool) {
        Location entityLocation = entity.getLocation();
        if (entityLocation.getY() == target.getY() && entityLocation.distance(target) > 1) {
            Vector directionToTarget = target.toVector().subtract(entityLocation.toVector()).normalize();
            Location frontLocation = entityLocation.clone().add(directionToTarget);
            LocationUtil.faceLocation(entity, frontLocation);
            if (frontLocation.getBlock().getType().isSolid()) {
                return mineBlock(entity, frontLocation, tool);
            }
        }
        return false;
    }

    private static boolean placeBlock(LivingEntity entity, Location location, Material material) {
        if (canPlaceBlock(entity, location)) {
            location.getBlock().setType(material);
            location.getWorld().playSound(location, Sound.BLOCK_STONE_PLACE, 1, 1);
            return true;
        }
        return false;
    }

    public static boolean placeBlockUnderFeet(LivingEntity entity, Material material) {
        Location belowLocation = entity.getLocation().clone().subtract(0, 1, 0);
        if (canPlaceBlock(entity, belowLocation)) {
            LocationUtil.faceLocation(entity, belowLocation);
            entity.swingMainHand();
            belowLocation.getBlock().setType(material);
            belowLocation.getWorld().playSound(belowLocation, Sound.BLOCK_STONE_PLACE, 1, 1);
            return true;
        }
        return false;
    }

    public static boolean canPlaceBlock(LivingEntity entity, Location location) {
        if (location.distance(entity.getLocation()) <= 5) {
            if (location.getBlock().getType().isAir() || !location.getBlock().getType().isSolid() || location.getBlock().isLiquid()) {
                List<BlockFace> placeableBlockFaces = new LinkedList<>();
                placeableBlockFaces.add(BlockFace.UP);
                placeableBlockFaces.add(BlockFace.DOWN);
                placeableBlockFaces.add(BlockFace.NORTH);
                placeableBlockFaces.add(BlockFace.SOUTH);
                placeableBlockFaces.add(BlockFace.EAST);
                placeableBlockFaces.add(BlockFace.WEST);
                for (BlockFace blockFace : placeableBlockFaces) {
                    if (location.getBlock().getRelative(blockFace).getType().isSolid() && !location.getBlock().getRelative(blockFace).isLiquid()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Starts mining the block at the given location with the given tool.
     * Uses a custom break-progress runnable (no Citizens BlockBreaker).
     */
    public static boolean mineBlock(LivingEntity entity, Location location, ItemStack tool) {
        Block block = location.getBlock();
        if (!block.getType().isSolid()) {
            return false;
        }
        LocationUtil.faceLocation(entity, location);
        MannequinBlockBreaker.start(Mai.getInstance(), entity, block, tool, null);
        return true;
    }

    public static Block getBlockInFrontAtFootLevel(Entity entity) {
        Location location = entity.getLocation();
        Vector direction = location.getDirection();
        int dx = (int) Math.round(direction.getX());
        int dz = (int) Math.round(direction.getZ());
        Block standingBlock = location.getBlock().getRelative(0, -1, 0);
        return standingBlock.getRelative(dx, 0, dz);
    }

}
