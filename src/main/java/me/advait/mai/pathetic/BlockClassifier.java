package me.advait.mai.pathetic;

import org.bukkit.Material;

/**
 * Centralized block property lookups for pathfinding.
 * All methods are pure functions of Material — safe for async use.
 */
public final class BlockClassifier {

    private BlockClassifier() {}

    /** Whether an entity can pass through this material without collision. */
    public static boolean isTraversable(Material material) {
        if (material.isAir()) return true;
        if (material.isSolid()) return false;
        // Non-solid blocks that still have collision boxes
        if (material.name().endsWith("_LEAVES")) return false;
        return switch (material) {
            case COBWEB, POWDER_SNOW, SWEET_BERRY_BUSH -> false;
            default -> true;
        };
    }

    /** Whether this block can be climbed (ladders, vines). */
    public static boolean isClimbable(Material material) {
        return switch (material) {
            case LADDER, VINE, TWISTING_VINES, TWISTING_VINES_PLANT,
                 WEEPING_VINES, WEEPING_VINES_PLANT -> true;
            default -> false;
        };
    }

    /** Whether this block is a liquid. */
    public static boolean isLiquid(Material material) {
        return material == Material.WATER || material == Material.LAVA;
    }

    /** Whether this is lava specifically. */
    public static boolean isLava(Material material) {
        return material == Material.LAVA;
    }

    /** Vanilla block slipperiness values. */
    public static double slipperiness(Material material) {
        return switch (material) {
            case ICE, PACKED_ICE, FROSTED_ICE -> 0.98;
            case BLUE_ICE -> 0.989;
            case SLIME_BLOCK -> 0.8;
            default -> 0.6;
        };
    }

    /**
     * Walking speed multiplier when standing ON TOP of this block.
     * Applied as a cost multiplier during pathfinding.
     */
    public static double speedMultiplier(Material material) {
        return switch (material) {
            case SOUL_SAND, SOUL_SOIL -> 0.4;
            case HONEY_BLOCK -> 0.4;
            default -> 1.0;
        };
    }

    /** Whether walking on this block is dangerous (causes damage without sneaking). */
    public static boolean isDangerous(Material material) {
        return switch (material) {
            case MAGMA_BLOCK, CACTUS -> true;
            default -> false;
        };
    }

    /** Whether the block should be treated as a solid surface to stand on. */
    public static boolean isSolid(Material material) {
        if (material.isSolid()) return true;
        // Leaves have collision but Material.isSolid() returns false
        if (material.name().endsWith("_LEAVES")) return true;
        return false;
    }
}
