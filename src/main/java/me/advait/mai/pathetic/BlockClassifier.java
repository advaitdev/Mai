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
    private static final boolean[] BREAKABLE = new boolean[N];
    private static final double[] SLIPPERINESS = new double[N];
    private static final double[] SPEED_MULTIPLIER = new double[N];
    private static final float[] HARDNESS = new float[N];
    private static final double[] PLACEMENT_VALUE = new double[N];

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
            HARDNESS[i]         = computeHardness(m);
            BREAKABLE[i]        = computeBreakable(m);
            PLACEMENT_VALUE[i]  = computePlacementValue(m);
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

    /** Whether the bot may break this block during pathing (solid, finite hardness, not protected). */
    public static boolean isBreakable(Material material) {
        return BREAKABLE[material.ordinal()];
    }

    /** Vanilla block hardness (≥0); negative means unbreakable. */
    public static float hardness(Material material) {
        return HARDNESS[material.ordinal()];
    }

    /**
     * Relative "value" of placing this block, used for the placement cost's
     * scarcity term. Throwaway blocks (cobblestone/dirt/netherrack) are ~1;
     * ores/metals/gems are very high so the pathfinder avoids wasting them.
     */
    public static double placementValue(Material material) {
        return PLACEMENT_VALUE[material.ordinal()];
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

    private static float computeHardness(Material material) {
        try {
            return material.getHardness();
        } catch (IllegalArgumentException e) {
            // Non-block materials (items) throw; treat as unbreakable.
            return -1f;
        }
    }

    /**
     * Breakable = a solid block with finite hardness that isn't protected.
     * Protected blocks (containers, spawners, command/structure/portal blocks)
     * are excluded so the bot never destroys something valuable or load-bearing
     * to a build while pathing.
     */
    private static boolean computeBreakable(Material material) {
        if (!computeSolid(material)) return false;
        if (computeHardness(material) < 0) return false;   // bedrock, barrier, etc.
        return switch (material) {
            case BARRIER, COMMAND_BLOCK, CHAIN_COMMAND_BLOCK, REPEATING_COMMAND_BLOCK,
                 STRUCTURE_BLOCK, JIGSAW, END_PORTAL_FRAME, SPAWNER,
                 CHEST, TRAPPED_CHEST, ENDER_CHEST, BARREL, SHULKER_BOX,
                 HOPPER, DISPENSER, DROPPER, FURNACE, BLAST_FURNACE, SMOKER,
                 BEACON, CONDUIT -> false;
            default -> true;
        };
    }

    /**
     * Placement value used for the scarcity-weighted placement cost. Cheap,
     * mass-produced blocks are ~1; tiered storage/ore blocks scale up steeply
     * so the planner only spends them when nothing cheaper is on hand.
     */
    private static double computePlacementValue(Material material) {
        String n = material.name();
        // Common throwaway blocks Baritone treats as free-ish.
        if (material == Material.COBBLESTONE || material == Material.DIRT
                || material == Material.NETHERRACK || material == Material.COBBLED_DEEPSLATE
                || material == Material.GRAVEL || material == Material.SAND) return 1.0;
        if (material == Material.DIAMOND_BLOCK || material == Material.EMERALD_BLOCK
                || material == Material.NETHERITE_BLOCK) return 4000.0;
        if (material == Material.GOLD_BLOCK) return 400.0;
        if (material == Material.IRON_BLOCK || material == Material.LAPIS_BLOCK
                || material == Material.REDSTONE_BLOCK) return 300.0;
        if (n.contains("DIAMOND") || n.contains("EMERALD") || n.contains("NETHERITE")) return 2000.0;
        if (n.contains("GOLD")) return 250.0;
        if (n.contains("IRON")) return 150.0;
        if (n.endsWith("_PLANKS") || n.contains("LOG") || n.contains("WOOD")) return 2.0;
        if (n.contains("STONE") || n.contains("DEEPSLATE") || n.contains("TUFF")
                || n.contains("ANDESITE") || n.contains("DIORITE") || n.contains("GRANITE")) return 3.0;
        // Default: an ordinary building block — cheap but not throwaway.
        return 5.0;
    }
}
