package me.advait.mai.pathetic.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * All tunable pathfinding and movement parameters, loaded from settings.yml.
 * Costs are in ticks (20 ticks = 1 second), matching Baritone's ActionCosts convention.
 */
public final class MovementConfig {

    // --- Pathfinding ---
    private final int maxIterations;
    private final boolean parkourEnabled;
    private final int maxParkourReach;
    private final int maxFallHeight;
    private final boolean allowUnsafeFalls;

    // --- Costs (ticks) ---
    private final double walkFlat;
    private final double walkDiagonal;
    private final double sprintMultiplier;
    private final double stepUp;
    private final double stepDown;
    private final double fallSafeBase;
    private final double fallPerBlock;
    private final double fallDamagePenalty;
    private final double sprintJumpBase;
    private final double sprintJumpPerGap;
    private final double jumpPenalty;
    private final double ladderUp;
    private final double ladderDown;
    private final double swim;
    private final double lavaMultiplier;
    private final double soulSandMultiplier;
    private final double iceMultiplier;
    private final double sneakCost;

    // --- Physics ---
    private final double walkAcceleration;
    private final double sprintFactor;
    private final double sneakFactor;
    private final double airAcceleration;
    private final double defaultSlipperiness;
    private final double airDrag;
    private final double verticalDrag;
    private final double gravity;
    private final double jumpVelocity;
    private final double sprintJumpBoost;

    // --- Navigation ---
    private final double waypointRadius;
    private final int pathUpdateInterval;
    private final int stuckTimeout;
    private final double nearTargetDistance;

    // --- Pre-computed fall costs (Baritone gravity model) ---
    private final double[] fallTickCosts;

    public MovementConfig(FileConfiguration config) {
        ConfigurationSection pf = section(config, "movement.pathfinding");
        this.maxIterations = pf.getInt("max_iterations", 100_000);
        this.parkourEnabled = pf.getBoolean("parkour_enabled", true);
        this.maxParkourReach = pf.getInt("max_parkour_reach", 4);
        this.maxFallHeight = pf.getInt("max_fall_height", 20);
        this.allowUnsafeFalls = pf.getBoolean("allow_unsafe_falls", true);

        ConfigurationSection costs = section(config, "movement.costs");
        this.walkFlat = costs.getDouble("walk_flat", 4.63);
        this.walkDiagonal = costs.getDouble("walk_diagonal", 6.55);
        this.sprintMultiplier = costs.getDouble("sprint_multiplier", 0.77);
        this.stepUp = costs.getDouble("step_up", 6.63);
        this.stepDown = costs.getDouble("step_down", 4.63);
        this.fallSafeBase = costs.getDouble("fall_safe_base", 3.07);
        this.fallPerBlock = costs.getDouble("fall_per_block", 0.5);
        this.fallDamagePenalty = costs.getDouble("fall_damage_penalty", 10.0);
        this.sprintJumpBase = costs.getDouble("sprint_jump_base", 3.56);
        this.sprintJumpPerGap = costs.getDouble("sprint_jump_per_gap", 3.56);
        this.jumpPenalty = costs.getDouble("jump_penalty", 2.0);
        this.ladderUp = costs.getDouble("ladder_up", 8.51);
        this.ladderDown = costs.getDouble("ladder_down", 6.67);
        this.swim = costs.getDouble("swim", 9.09);
        this.lavaMultiplier = costs.getDouble("lava_multiplier", 4.0);
        this.soulSandMultiplier = costs.getDouble("soul_sand_multiplier", 2.0);
        this.iceMultiplier = costs.getDouble("ice_multiplier", 0.8);
        this.sneakCost = costs.getDouble("sneak", 15.38);

        ConfigurationSection phys = section(config, "movement.physics");
        this.walkAcceleration = phys.getDouble("walk_acceleration", 0.1);
        this.sprintFactor = phys.getDouble("sprint_factor", 1.3);
        this.sneakFactor = phys.getDouble("sneak_factor", 0.3);
        this.airAcceleration = phys.getDouble("air_acceleration", 0.02);
        this.defaultSlipperiness = phys.getDouble("default_slipperiness", 0.6);
        this.airDrag = phys.getDouble("air_drag", 0.91);
        this.verticalDrag = phys.getDouble("vertical_drag", 0.98);
        this.gravity = phys.getDouble("gravity", 0.08);
        this.jumpVelocity = phys.getDouble("jump_velocity", 0.42);
        this.sprintJumpBoost = phys.getDouble("sprint_jump_boost", 0.2);

        ConfigurationSection nav = section(config, "movement.navigation");
        this.waypointRadius = nav.getDouble("waypoint_radius", 0.5);
        this.pathUpdateInterval = nav.getInt("path_update_interval", 40);
        this.stuckTimeout = nav.getInt("stuck_timeout", 200);
        this.nearTargetDistance = nav.getDouble("near_target_distance", 2.0);

        this.fallTickCosts = computeFallCosts(maxFallHeight + 1);
    }

    /**
     * Pre-computes fall duration in ticks for each fall distance (0..maxBlocks).
     * Uses Minecraft's gravity model: velocity(t) = (0.98^t - 1) * -3.92
     */
    private static double[] computeFallCosts(int maxBlocks) {
        double[] costs = new double[maxBlocks + 1];
        costs[0] = 0;
        for (int n = 1; n <= maxBlocks; n++) {
            costs[n] = distanceToTicks(n);
        }
        return costs;
    }

    private static double velocity(int tick) {
        return (Math.pow(0.98, tick) - 1) * -3.92;
    }

    private static double distanceToTicks(double distance) {
        if (distance <= 0) return 0;
        double remaining = distance;
        int tick = 0;
        while (true) {
            double fallThisTick = velocity(tick);
            if (remaining <= fallThisTick) {
                return tick + remaining / fallThisTick;
            }
            remaining -= fallThisTick;
            tick++;
        }
    }

    /** Returns the time in ticks to fall the given number of blocks. */
    public double fallCostTicks(int blocks) {
        if (blocks < 0 || blocks >= fallTickCosts.length) {
            return distanceToTicks(blocks);
        }
        return fallTickCosts[blocks];
    }

    /** Returns expected fall damage for a given distance. */
    public static int fallDamage(int blocks) {
        return Math.max(0, blocks - 3);
    }

    private static ConfigurationSection section(FileConfiguration config, String path) {
        ConfigurationSection s = config.getConfigurationSection(path);
        if (s == null) {
            config.createSection(path);
            s = config.getConfigurationSection(path);
        }
        return s;
    }

    // --- Getters ---

    public int getMaxIterations() { return maxIterations; }
    public boolean isParkourEnabled() { return parkourEnabled; }
    public int getMaxParkourReach() { return maxParkourReach; }
    public int getMaxFallHeight() { return maxFallHeight; }
    public boolean isAllowUnsafeFalls() { return allowUnsafeFalls; }

    public double getWalkFlat() { return walkFlat; }
    public double getWalkDiagonal() { return walkDiagonal; }
    public double getSprintMultiplier() { return sprintMultiplier; }
    public double getStepUp() { return stepUp; }
    public double getStepDown() { return stepDown; }
    public double getFallSafeBase() { return fallSafeBase; }
    public double getFallPerBlock() { return fallPerBlock; }
    public double getFallDamagePenalty() { return fallDamagePenalty; }
    public double getSprintJumpBase() { return sprintJumpBase; }
    public double getSprintJumpPerGap() { return sprintJumpPerGap; }
    public double getJumpPenalty() { return jumpPenalty; }
    public double getLadderUp() { return ladderUp; }
    public double getLadderDown() { return ladderDown; }
    public double getSwim() { return swim; }
    public double getLavaMultiplier() { return lavaMultiplier; }
    public double getSoulSandMultiplier() { return soulSandMultiplier; }
    public double getIceMultiplier() { return iceMultiplier; }
    public double getSneakCost() { return sneakCost; }

    public double getWalkAcceleration() { return walkAcceleration; }
    public double getSprintFactor() { return sprintFactor; }
    public double getSneakFactor() { return sneakFactor; }
    public double getAirAcceleration() { return airAcceleration; }
    public double getDefaultSlipperiness() { return defaultSlipperiness; }
    public double getAirDrag() { return airDrag; }
    public double getVerticalDrag() { return verticalDrag; }
    public double getGravity() { return gravity; }
    public double getJumpVelocity() { return jumpVelocity; }
    public double getSprintJumpBoost() { return sprintJumpBoost; }

    public double getWaypointRadius() { return waypointRadius; }
    public int getPathUpdateInterval() { return pathUpdateInterval; }
    public int getStuckTimeout() { return stuckTimeout; }
    public double getNearTargetDistance() { return nearTargetDistance; }
}
