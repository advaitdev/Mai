package me.advait.mai.brain.action.mechanic.building;

import me.advait.mai.brain.action.result.HumanoidActionResult;
import me.advait.mai.util.DurabilityUtil;
import me.advait.mai.util.LocationUtil;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.CompletableFuture;

/**
 * Tick-based block breaker for mannequins. Progresses break damage each tick,
 * then breaks the block and handles drops/durability.
 */
public final class MannequinBlockBreaker extends BukkitRunnable {

    private final LivingEntity entity;
    private final Block block;
    private final ItemStack tool;
    private final CompletableFuture<HumanoidActionResult> resultFuture;

    private double progress = 0;
    private final double progressPerTick;

    private MannequinBlockBreaker(LivingEntity entity, Block block, ItemStack tool,
                                   CompletableFuture<HumanoidActionResult> resultFuture, int breakTicks) {
        this.entity = entity;
        this.block = block;
        this.tool = tool;
        this.resultFuture = resultFuture;
        this.progressPerTick = breakTicks <= 0 ? 1 : 1.0 / breakTicks;
    }

    public static void start(JavaPlugin plugin, LivingEntity entity, Block block, ItemStack tool,
                             CompletableFuture<HumanoidActionResult> resultFuture) {
        int breakTicks = computeBreakTicks(block, tool);
        new MannequinBlockBreaker(entity, block, tool, resultFuture, breakTicks)
                .runTaskTimer(plugin, 0L, 1L);
    }

    private static int computeBreakTicks(Block block, ItemStack tool) {
        float hardness = block.getType().getHardness();
        if (hardness < 0) return Integer.MAX_VALUE;
        if (hardness == 0) return 1;
        double base = hardness * 30;
        double speed = 1.0;
        if (tool != null && !tool.getType().isAir()) {
            speed = getDestroySpeed(block.getType(), tool);
        }
        return (int) Math.max(1, base / speed);
    }

    private static double getDestroySpeed(Material blockType, ItemStack tool) {
        Material toolType = tool.getType();
        String block = blockType.name();
        String toolName = toolType.name();

        if ((block.contains("LOG") || block.contains("WOOD")) && toolName.contains("AXE")) return 4;
        if (block.contains("STONE") && toolName.contains("PICKAXE")) return 4;
        if (block.contains("DIRT") && toolName.contains("SHOVEL")) return 4;
        if (toolName.contains("PICKAXE") || toolName.contains("AXE") || toolName.contains("SHOVEL")) return 2;
        return 1;
    }

    @Override
    public void run() {
        if (entity == null || !entity.isValid() || !block.getWorld().equals(entity.getWorld())) {
            complete(false, "Entity is no longer valid.");
            cancel();
            return;
        }
        if (!LocationUtil.isBlockTargetable(entity.getLocation(), block)) {
            complete(false, "Block is too far away to reach.");
            cancel();
            return;
        }
        if (tool != null && entity.getEquipment() != null) {
            ItemStack main = entity.getEquipment().getItemInMainHand();
            if (main == null || !main.equals(tool)) {
                complete(false, "Tool is no longer being held.");
                cancel();
                return;
            }
        }

        entity.swingMainHand();
        progress += progressPerTick;

        if (progress >= 1.0) {
            block.breakNaturally(tool);
            if (tool != null && tool.getType().getMaxDurability() > 0) {
                DurabilityUtil.decreaseToolDurability(tool);
                if (entity.getEquipment() != null) {
                    entity.getEquipment().setItemInMainHand(tool);
                }
            }
            complete(true, "Block mined successfully.");
            cancel();
        }
    }

    private void complete(boolean success, String message) {
        if (resultFuture != null) {
            resultFuture.complete(new HumanoidActionResult(success, message));
        }
    }
}
