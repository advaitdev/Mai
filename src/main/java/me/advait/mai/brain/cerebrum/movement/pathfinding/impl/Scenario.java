package me.advait.mai.brain.cerebrum.movement.pathfinding.impl;

import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import de.bsommerfeld.pathetic.bukkit.mapper.BukkitMapper;
import de.bsommerfeld.pathetic.bukkit.provider.BukkitNavigationPoint;
import org.bukkit.Material;
import org.bukkit.block.BlockType;

public enum Scenario {

    /**
     * Walking to an adjacent node on flat terrain.
     */
    WALK_FLAT {
        @Override
        public boolean matches(PathPosition current, PathPosition previous) {
            return deltaY(current, previous) == 0
                    && isSolidBelow(current)
                    && isAir(current)
                    && isAir(current.add(0, 1, 0)); // player height clearance
        }

        @Override
        public double computeCost(PathPosition current, PathPosition previous) {
            return 1.0;
        }
    },

    /**
     * Stepping up 1 block — common vertical adjustment.
     */
    STEP_UP {
        @Override
        public boolean matches(PathPosition current, PathPosition previous) {
            return deltaY(current, previous) == 1
                    && isSolidBelow(current)
                    && isAir(current)
                    && isAir(current.add(0, 1, 0));
        }

        @Override
        public double computeCost(PathPosition current, PathPosition previous) {
            return 1.2;
        }
    },

    /**
     * Climbing a ladder — slower than walking.
     */
    LADDER_CLIMB {
        @Override
        public boolean matches(PathPosition current, PathPosition previous) {
            return current.getBlockType() == Material.LADDER
                    && isAir(current.add(0, 1, 0));
        }

        @Override
        public double computeCost(PathPosition current, PathPosition previous) {
            return 1.8;
        }
    },

    /**
     * Swimming through water blocks.
     */
    SWIM {
        @Override
        public boolean matches(PathPosition current, PathPosition previous) {
            return current.getBlockType() == BlockType.WATER
                    && current.add(0, 1, 0).getBlockType() == BlockType.WATER;
        }

        @Override
        public double computeCost(PathPosition current, PathPosition previous) {
            return 3.0;
        }
    },

    /**
     * Jumping up 1 block vertically.
     */
    JUMP_UP_ONE_BLOCK {
        @Override
        public boolean matches(PathPosition current, PathPosition previous) {
            return deltaY(current, previous) == 1
                    && isAir(current)
                    && isAir(current.add(0, 1, 0))
                    && isSolidBelow(current);
        }

        @Override
        public double computeCost(PathPosition current, PathPosition previous) {
            return 2.0;
        }
    },

    /**
     * Falling safely ≤ 3 blocks.
     */
    FALL_SAFE {
        @Override
        public boolean matches(PathPosition current, PathPosition previous) {
            int dy = deltaY(current, previous);
            return dy < 0 && Math.abs(dy) <= 3
                    && isSolidBelow(current)
                    && isAir(current)
                    && isAir(current.add(0, 1, 0));
        }

        @Override
        public double computeCost(PathPosition current, PathPosition previous) {
            return 1.5 + Math.abs(deltaY(current, previous)) * 0.5;
        }
    },

    /**
     * Placing a block to bridge or climb.
     */
    PLACE_BLOCK_TO_MOVE {
        @Override
        public boolean matches(PathPosition current, PathPosition previous) {
            return BukkitMapper.toLocation(current.add(0, -1, 0)).getBlock().getType() == Material.AIR
                    && isAir(current)
                    && isAir(current.add(0, 1, 0))
                    && hasBlocks();
        }

        @Override
        public double computeCost(PathPosition current, PathPosition previous) {
            return 3.5;
        }
    },

    /**
     * Breaking a block that's in the way.
     */
    BREAK_BLOCK_IN_PATH {
        @Override
        public boolean matches(PathPosition current, PathPosition previous) {
            return (!current.isPassable() && isBreakable(current))
                    || (!current.add(0, 1, 0).isPassable() && isBreakable(current.add(0, 1, 0)));
        }

        @Override
        public double computeCost(PathPosition current, PathPosition previous) {
            double total = 0.0;
            for (int y = 0; y <= 1; y++) {
                PathPosition pos = current.add(0, y, 0);
                if (!pos.isPassable() && isBreakable(pos)) {
                    double hardness = getHardness(pos.getBlockType());
                    double toolSpeed = getToolSpeed(pos.getBlockType());
                    total += 1.0 + (hardness / toolSpeed);
                }
            }
            return total;
        }
    };

    public abstract boolean matches(PathPosition current, PathPosition previous);
    public abstract double computeCost(PathPosition current, PathPosition previous);

    protected int deltaX(PathPosition c, PathPosition p) { return c.getX() - p.getX(); }
    protected int deltaY(PathPosition c, PathPosition p) { return c.getY() - p.getY(); }
    protected int deltaZ(PathPosition c, PathPosition p) { return c.getZ() - p.getZ(); }
    protected boolean isAir(PathPosition pos) { return pos.getBlockType() == BlockType.AIR; }
    protected boolean isSolidBelow(PathPosition pos) { return pos.offset(0, -1, 0).isSolid(); }
    protected boolean hasBlocks() { return true; }
    protected boolean isBreakable(PathPosition pos) { return pos.getBlockType() != BlockType.BEDROCK; }
    protected double getHardness(BlockType block) {
        return switch (block) {
            case STONE -> 1.5;
            case DIRT -> 0.5;
            case WOOD -> 2.0;
            case OBSIDIAN -> 50.0;
            default -> 1.0;
        };
    }
    protected double getToolSpeed(BlockType block) {
        return switch (block) {
            case STONE -> 4.0;
            case DIRT -> 2.0;
            case WOOD -> 2.0;
            default -> 0.1;
        };
    }
    protected boolean npcCanSprint() { return true; }
}
