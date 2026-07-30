package me.advait.mai.brain.action.mechanic.building;

import me.advait.mai.brain.action.result.HumanoidActionResult;
import me.advait.mai.util.DurabilityUtil;
import me.advait.mai.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.CompletableFuture;

/**
 * Tick-based block breaker for mannequins. Progresses break damage each tick,
 * then breaks the block and handles drops/durability.
 */
public final class MannequinBlockBreaker extends BukkitRunnable {

    /** Players within this distance of the block see the crack overlay. */
    private static final double CRACK_VIEW_DISTANCE_SQ = 64 * 64;

    private final LivingEntity entity;
    private final Block block;
    private final ItemStack tool;
    private final CompletableFuture<HumanoidActionResult> resultFuture;

    private double progress = 0;
    private final double progressPerTick;
    /** Last destroy stage (0-9) sent, so we only re-broadcast on change. */
    private int lastStage = -1;

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
            abort("Entity is no longer valid.");
            return;
        }
        if (!LocationUtil.isBlockTargetable(entity.getLocation(), block)) {
            abort("Block is too far away to reach.");
            return;
        }
        if (tool != null && entity.getEquipment() != null) {
            ItemStack main = entity.getEquipment().getItemInMainHand();
            if (main == null || !main.equals(tool)) {
                abort("Tool is no longer being held.");
                return;
            }
        }

        // Swing the arm and advance the visible block-cracking overlay each tick.
        entity.swingMainHand();
        progress += progressPerTick;
        broadcastCrack(Math.min(1.0, progress));

        if (progress >= 1.0) {
            block.breakNaturally(tool);
            // The bot's own edit bypasses Bukkit block events, so refresh the
            // pathfinder's static chunk-snapshot cache directly.
            de.bsommerfeld.pathetic.bukkit.provider.FailingNavigationPointProvider.invalidateChunk(
                    block.getWorld().getUID(), block.getX() >> 4, block.getZ() >> 4);
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

    /** Fails the break, clearing any partial crack overlay first, and stops ticking. */
    private void abort(String message) {
        clearCrack();
        complete(false, message);
        cancel();
    }

    /**
     * Shows the vanilla block-breaking cracks at the target by broadcasting the
     * current dig progress to nearby players, attributed to the mannequin so the
     * overlay tracks per-entity. Only re-sends when the destroy stage (0-9)
     * changes. A successful break clears the overlay client-side on its own; the
     * {@link #clearCrack()} path covers cancellation.
     */
    private void broadcastCrack(double prog) {
        int stage = (int) Math.floor(prog * 10.0);
        if (stage == lastStage) return;
        lastStage = stage;
        sendCrack((float) Math.max(0.0, Math.min(1.0, prog)));
    }

    /** Removes any crack overlay left at the target (progress 0). */
    private void clearCrack() {
        if (lastStage <= 0) return;
        lastStage = 0;
        sendCrack(0f);
    }

    private void sendCrack(float progress) {
        Location loc = block.getLocation();
        for (Player viewer : block.getWorld().getPlayers()) {
            if (viewer.getLocation().distanceSquared(loc) <= CRACK_VIEW_DISTANCE_SQ) {
                viewer.sendBlockDamage(loc, progress, entity);
            }
        }
    }
}
