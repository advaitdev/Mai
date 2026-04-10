package me.advait.mai.pathetic;

import org.bukkit.Material;

/**
 * Centralized block property lookups for pathfinding. All methods are
 * pure functions of {@link Material} — safe for async use.
 *
 * <p>Property answers are precomputed once at class init into arrays
 * indexed by {@link Material#ordinal()}. Each lookup is then a single
 * array read instead of a switch dispatch + a few method calls. The hot
 * A* loop hits these methods millions of times per pathfinding request,
 * so the precompute pays for itself many times over.
 *
 * <p>Adding a new property: add a new {@code static final} array,
 * populate it in the static initializer, and expose a getter.
 */
public final class BlockClassifier {

    private static final int N = Material.values().length;

    private static final boolean[] TRAVERSABLE = new boolean[N];
    private static final boolean[] CLIMBABLE = new boolean[N];
    private static final boolean[] LIQUID = new boolean[N];
    private static final boolean[] LAVA = new boolean[N];
    private static final boolean[] DANGEROUS = new boolean[N];
    private static final boolean[] SOLID = new boolean[N];
    private static final double[] SLIPPERINESS = new double[N];
    private static final double[] SPEED_MULTIPLIER = new double[N];

    static {
        for (Material m : Material.values()) {
            int i = m.ordinal();
            TRAVERSABLE[i]      = computeTraversable(m);
            CLIMBABLE[i]        = computeClimbable(m);
            LIQUID[i]           = computeLiquid(m);
            LAVA[i]             = (m == Material.LAVA);
            DANGEROUS[i]        = computeDangerous(m);
            SOLID[i]            = computeSolid(m);
            SLIPPERINESS[i]     = computeSlipperiness(m);
            SPEED_MULTIPLIER[i] = computeSpeedMultiplier(m);
        }
    }

    private BlockClassifier() {}

    // =========================================================================
    // Public hot-path lookups (single array reads)
    // =========================================================================

    /** Whether an entity can pass through this material without collision. */
    public static boolean isTraversable(Material material) {
        return TRAVERSABLE[material.ordinal()];
    }

    /** Whether this block can be climbed (ladders, vines). */
    public static boolean isClimbable(Material material) {
        return CLIMBABLE[material.ordinal()];
    }

    /** Whether this block is a liquid. */
    public static boolean isLiquid(Material material) {
        return LIQUID[material.ordinal()];
    }

    /** Whether this is lava specifically. */
    public static boolean isLava(Material material) {
        return LAVA[material.ordinal()];
    }

    /** Vanilla block slipperiness values. */
    public static double slipperiness(Material material) {
        return SLIPPERINESS[material.ordinal()];
    }

    /**
     * Walking speed multiplier when standing ON TOP of this block.
     * Applied as a cost multiplier during pathfinding.
     */
    public static double speedMultiplier(Material material) {
        return SPEED_MULTIPLIER[material.ordinal()];
    }

    /** Whether walking on this block is dangerous (causes damage without sneaking). */
    public static boolean isDangerous(Material material) {
        return DANGEROUS[material.ordinal()];
    }

    /** Whether the block should be treated as a solid surface to stand on. */
    public static boolean isSolid(Material material) {
        return SOLID[material.ordinal()];
    }

    // =========================================================================
    // Compute helpers (run once at class init, never on the hot path)
    // =========================================================================

    private static boolean computeTraversable(Material material) {
        if (material.isAir()) return true;
        if (material.isSolid()) return false;
        // Non-solid blocks that still have collision boxes
        if (material.name().endsWith("_LEAVES")) return false;
        return switch (material) {
            case COBWEB, POWDER_SNOW, SWEET_BERRY_BUSH -> false;
            default -> true;
        };
    }

    private static boolean computeClimbable(Material material) {
        return switch (material) {
            case LADDER, VINE, TWISTING_VINES, TWISTING_VINES_PLANT,
                 WEEPING_VINES, WEEPING_VINES_PLANT -> true;
            default -> false;
        };
    }

    private static boolean computeLiquid(Material material) {
        return material == Material.WATER || material == Material.LAVA;
    }

    private static double computeSlipperiness(Material material) {
        return switch (material) {
            case ICE, PACKED_ICE, FROSTED_ICE -> 0.98;
            case BLUE_ICE -> 0.989;
            case SLIME_BLOCK -> 0.8;
            default -> 0.6;
        };
    }

    private static double computeSpeedMultiplier(Material material) {
        return switch (material) {
            case SOUL_SAND, SOUL_SOIL -> 0.4;
            case HONEY_BLOCK -> 0.4;
            default -> 1.0;
        };
    }

    private static boolean computeDangerous(Material material) {
        return switch (material) {
            case MAGMA_BLOCK, CACTUS -> true;
            default -> false;
        };
    }

    private static boolean computeSolid(Material material) {
        if (material.isSolid()) return true;
        // Leaves have collision but Material.isSolid() returns false
        if (material.name().endsWith("_LEAVES")) return true;
        return false;
    }
}
