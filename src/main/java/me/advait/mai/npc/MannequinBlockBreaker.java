package me.advait.mai.npc;

import me.advait.mai.brain.action.result.HumanoidActionMessage;
import me.advait.mai.brain.action.result.HumanoidActionResult;
import me.advait.mai.util.DurabilityUtil;
import me.advait.mai.util.LocationUtil;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.CompletableFuture;

/**
 * Custom block breaker for mannequins (no Citizens). Ticks break progress each tick,
 * then breaks the block and drops items.
 */
public final class MannequinBlockBreaker extends BukkitRunnable {

    private final LivingEntity entity;
    private final Block block;
    private final ItemStack tool;
    private final CompletableFuture<HumanoidActionResult> resultFuture;

    private double progress = 0;
    private final double progressPerTick;

    private MannequinBlockBreaker(LivingEntity entity, Block block, ItemStack tool,
                                   CompletableFuture<HumanoidActionResult> resultFuture,
                                   int breakTicks) {
        this.entity = entity;
        this.block = block;
        this.tool = tool;
        this.resultFuture = resultFuture;
        this.progressPerTick = breakTicks <= 0 ? 1 : 1.0 / breakTicks;
    }

    /**
     * Start a block breaker that runs every tick. If resultFuture is non-null, it is completed when the block is broken.
     */
    public static void start(JavaPlugin plugin, LivingEntity entity, Block block, ItemStack tool,
                             CompletableFuture<HumanoidActionResult> resultFuture) {
        int breakTicks = computeBreakTicks(block, tool);
        MannequinBlockBreaker run = new MannequinBlockBreaker(entity, block, tool, resultFuture, breakTicks);
        run.runTaskTimer(plugin, 0L, 1L);
    }

    private static int computeBreakTicks(Block block, ItemStack tool) {
        float hardness = block.getType().getHardness();
        if (hardness < 0) return Integer.MAX_VALUE;
        if (hardness == 0) return 1;
        // Base time: hardness * 30 is roughly vanilla (with hand). Tool speed multiplies.
        double base = hardness * 30;
        double speed = 1.0;
        if (tool != null && !tool.getType().isAir()) {
            speed = getDestroySpeed(block.getType(), tool);
        }
        int ticks = (int) Math.max(1, base / speed);
        return ticks;
    }

    private static double getDestroySpeed(org.bukkit.Material blockType, ItemStack tool) {
        // Approximate tool speed multiplier (correct tool = faster)
        org.bukkit.Material toolType = tool.getType();
        if (blockType.name().contains("LOG") || blockType.name().contains("WOOD") && toolType.name().contains("AXE")) return 4;
        if (blockType.name().contains("STONE") && toolType.name().contains("PICKAXE")) return 4;
        if (blockType.name().contains("DIRT") && toolType.name().contains("SHOVEL")) return 4;
        if (toolType.name().contains("PICKAXE")) return 2;
        if (toolType.name().contains("AXE")) return 2;
        if (toolType.name().contains("SHOVEL")) return 2;
        return 1;
    }

    @Override
    public void run() {
        if (entity == null || !entity.isValid() || !block.getWorld().equals(entity.getWorld())) {
            complete(false, HumanoidActionMessage.NPC_IS_NULL);
            cancel();
            return;
        }
        if (!LocationUtil.isBlockTargetable(entity.getLocation(), block)) {
            complete(false, HumanoidActionMessage.MINE_MESSAGE_FAILURE_TOO_FAR);
            cancel();
            return;
        }
        if (tool != null && entity.getEquipment() != null) {
            ItemStack main = entity.getEquipment().getItemInMainHand();
            if (main == null || !main.equals(tool)) {
                complete(false, HumanoidActionMessage.MINE_MESSAGE_FAILURE_TOOL_NOT_HELD);
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
            complete(true, HumanoidActionMessage.MINE_MESSAGE_SUCCESS);
            cancel();
        }
    }

    private void complete(boolean success, String message) {
        if (resultFuture != null) {
            resultFuture.complete(new HumanoidActionResult(success, message));
        }
    }

}
