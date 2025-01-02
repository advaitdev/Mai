package me.advait.mai.npc;

import me.advait.mai.Mai;
import net.citizensnpcs.api.ai.tree.BehaviorStatus;
import net.citizensnpcs.api.npc.BlockBreaker;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.util.PlayerAnimation;
import net.citizensnpcs.util.Util;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class HumanoidUtil {

    public static boolean jump(NPC npc, double height) {
        if (npc.getEntity().isOnGround()) {
            npc.getEntity().setVelocity(npc.getEntity().getVelocity().add(new Vector(0, height, 0)));
            return true;
        }
        return false;
    }

    public static boolean pileUp(NPC npc) {
        HumanoidUtil.jump(npc, 0.5);
        AtomicBoolean didPlace = new AtomicBoolean(false);
        Bukkit.getScheduler().runTaskLater(JavaPlugin.getPlugin(Mai.class), () -> {
            didPlace.set(HumanoidUtil.placeBlockUnderFeet(npc, Material.STONE));
        }, 6);
        return didPlace.get();
    }

    public static boolean bridgeTowardsTarget(NPC npc, Location target, Material material) {
        Location npcLocation = npc.getEntity().getLocation();
        if (npcLocation.getY() == target.getY() && npcLocation.distance(target) > 1) {
            Util.faceLocation(npc.getEntity(), target);
            Location frontLocation = getBlockInFrontAtFootLevel(npc.getEntity()).getLocation();
            Util.faceLocation(npc.getEntity(), frontLocation);
            if (canPlaceBlock(npc, frontLocation)) {
                placeBlock(npc, frontLocation, material);
                return true;
            }
        }
        return false;
    }

    public static boolean mineTowardsTarget(NPC npc, Location target, ItemStack tool) {
        Location npcLocation = npc.getEntity().getLocation();
        if (npcLocation.getY() == target.getY() && npcLocation.distance(target) > 1) {
            Vector directionToTarget = target.toVector().subtract(npcLocation.toVector()).normalize();
            Location frontLocation = npcLocation.clone().add(directionToTarget);
            Util.faceLocation(npc.getEntity(), frontLocation);
            if (frontLocation.getBlock().getType().isSolid()) {
                return mineBlock(npc, frontLocation, tool);
            }
        }
        return false;
    }

    private static boolean placeBlock(NPC npc, Location location, Material material) {
        if (canPlaceBlock(npc, location)) {
            location.getBlock().setType(material);
            location.getWorld().playSound(location, Sound.BLOCK_STONE_PLACE, 1, 1);
            return true;
        }
        return false;
    }

    public static boolean placeBlockUnderFeet(NPC npc, Material material) {
        Player entity = (Player) npc.getEntity();
        Location belowLocation = entity.getLocation().clone().subtract(0, 1, 0);
        if (canPlaceBlock(npc, belowLocation)) {
            // Util.face(npc.getEntity(), entity.getLocation().getYaw(), 90);
            Util.faceLocation(npc.getEntity(), belowLocation);
            PlayerAnimation.ARM_SWING.play(entity);
            belowLocation.getBlock().setType(material);
            belowLocation.getWorld().playSound(belowLocation, Sound.BLOCK_STONE_PLACE, 1, 1);
            return true;
        } else {
            Bukkit.broadcastMessage("Could not place block! " + belowLocation.getBlock());
        }
        return false;
    }

    public static boolean canPlaceBlock(NPC npc, Location location) {
        if (location.distance(npc.getEntity().getLocation()) <= 5) {
            if (location.getBlock().getType().isAir() || !location.getBlock().getType().isSolid() || location.getBlock().isLiquid()) {
                List<BlockFace> placeableBlockFaces = new LinkedList<>();
                placeableBlockFaces.add(BlockFace.UP);
                placeableBlockFaces.add(BlockFace.DOWN);
                placeableBlockFaces.add(BlockFace.NORTH);
                placeableBlockFaces.add(BlockFace.SOUTH);
                placeableBlockFaces.add(BlockFace.EAST);
                placeableBlockFaces.add(BlockFace.WEST);
                for (BlockFace blockFace : placeableBlockFaces){
                    if (location.getBlock().getRelative(blockFace).getType().isSolid() && !location.getBlock().getRelative(blockFace).isLiquid()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean mineBlock(NPC npc, Location location, ItemStack tool) {
        BlockBreaker.BlockBreakerConfiguration blockBreakerConfig = new BlockBreaker.BlockBreakerConfiguration();
        blockBreakerConfig.item(tool);
        if (!location.getBlock().getType().isSolid()) {
            return false;
        }
        Util.faceLocation(npc.getEntity(), location);
        BlockBreaker blockBreaker = npc.getBlockBreaker(location.getBlock(), blockBreakerConfig);
        if (blockBreaker.shouldExecute()) {
            TaskRunnable run = new TaskRunnable(blockBreaker);
            run.taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(JavaPlugin.getPlugin(Mai.class), run, 0 ,1);
        }
        return true;
    }

    public static Block getBlockInFrontAtFootLevel(Entity entity) {
        // Get the player's current location
        Location location = entity.getLocation();

        // Get the direction the player is facing
        Vector direction = location.getDirection();

        // Convert the direction vector into block-relative coordinates
        int dx = (int) Math.round(direction.getX());
        int dz = (int) Math.round(direction.getZ());

        // Get the block the player is standing on
        Block standingBlock = location.getBlock().getRelative(0, -1, 0);

        // Get the block in front at foot level using the direction offsets
        Block blockInFront = standingBlock.getRelative(dx, 0, dz);

        return blockInFront;
    }


    private static class TaskRunnable implements Runnable {
        private int taskId;
        private final BlockBreaker breaker;

        public TaskRunnable(BlockBreaker breaker) {
            this.breaker = breaker;
        }

        @Override
        public void run() {
            if (breaker.run() != BehaviorStatus.RUNNING) {
                Bukkit.getScheduler().cancelTask(taskId);
                breaker.reset();
            }
        }
    }



}
