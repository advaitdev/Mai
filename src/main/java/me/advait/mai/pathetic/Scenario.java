package me.advait.mai.pathetic;

import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import de.bsommerfeld.pathetic.bukkit.mapper.BukkitMapper;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Movement scenario costs for humanoid pathfinding.
 */
public enum Scenario {

    WALK_FLAT {
        @Override
        public boolean matches(PathPosition current, PathPosition previous, World world) {
            return deltaY(current, previous) == 0
                    && isSolidBelow(current, world)
                    && isAir(current, world)
                    && isAir(current.add(0, 1, 0), world);
        }

        @Override
        public double computeCost(PathPosition current, PathPosition previous, World world) {
            return 1.0;
        }
    },

    STEP_UP {
        @Override
        public boolean matches(PathPosition current, PathPosition previous, World world) {
            return deltaY(current, previous) == 1
                    && isSolidBelow(current, world)
                    && isAir(current, world)
                    && isAir(current.add(0, 1, 0), world);
        }

        @Override
        public double computeCost(PathPosition current, PathPosition previous, World world) {
            return 1.2;
        }
    },

    LADDER_CLIMB {
        @Override
        public boolean matches(PathPosition current, PathPosition previous, World world) {
            return blockAt(current, world).getType() == Material.LADDER
                    && isAir(current.add(0, 1, 0), world);
        }

        @Override
        public double computeCost(PathPosition current, PathPosition previous, World world) {
            return 1.8;
        }
    },

    SWIM {
        @Override
        public boolean matches(PathPosition current, PathPosition previous, World world) {
            return blockAt(current, world).getType() == Material.WATER
                    && blockAt(current.add(0, 1, 0), world).getType() == Material.WATER;
        }

        @Override
        public double computeCost(PathPosition current, PathPosition previous, World world) {
            return 3.0;
        }
    },

    JUMP_UP_ONE_BLOCK {
        @Override
        public boolean matches(PathPosition current, PathPosition previous, World world) {
            return deltaY(current, previous) == 1
                    && isAir(current, world)
                    && isAir(current.add(0, 1, 0), world)
                    && isSolidBelow(current, world);
        }

        @Override
        public double computeCost(PathPosition current, PathPosition previous, World world) {
            return 2.0;
        }
    },

    FALL_SAFE {
        @Override
        public boolean matches(PathPosition current, PathPosition previous, World world) {
            int dy = deltaY(current, previous);
            return dy < 0 && Math.abs(dy) <= 3
                    && isSolidBelow(current, world)
                    && isAir(current, world)
                    && isAir(current.add(0, 1, 0), world);
        }

        @Override
        public double computeCost(PathPosition current, PathPosition previous, World world) {
            return 1.5 + Math.abs(deltaY(current, previous)) * 0.5;
        }
    },

    PLACE_BLOCK_TO_MOVE {
        @Override
        public boolean matches(PathPosition current, PathPosition previous, World world) {
            return blockAt(current.add(0, -1, 0), world).getType() == Material.AIR
                    && isAir(current, world)
                    && isAir(current.add(0, 1, 0), world);
        }

        @Override
        public double computeCost(PathPosition current, PathPosition previous, World world) {
            return 3.5;
        }
    },

    BREAK_BLOCK_IN_PATH {
        @Override
        public boolean matches(PathPosition current, PathPosition previous, World world) {
            return (!isPassable(current, world) && isBreakable(current, world))
                    || (!isPassable(current.add(0, 1, 0), world) && isBreakable(current.add(0, 1, 0), world));
        }

        @Override
        public double computeCost(PathPosition current, PathPosition previous, World world) {
            double total = 0.0;
            for (int y = 0; y <= 1; y++) {
                PathPosition pos = current.add(0, y, 0);
                if (!isPassable(pos, world) && isBreakable(pos, world)) {
                    Material mat = blockAt(pos, world).getType();
                    total += 1.0 + (getHardness(mat) / getToolSpeed(mat));
                }
            }
            return total;
        }
    };

    public abstract boolean matches(PathPosition current, PathPosition previous, World world);
    public abstract double computeCost(PathPosition current, PathPosition previous, World world);

    protected static int deltaY(PathPosition c, PathPosition p) {
        return (int) (c.getY() - p.getY());
    }

    protected static Block blockAt(PathPosition pos, World world) {
        return BukkitMapper.toLocation(pos, world).getBlock();
    }

    protected static boolean isAir(PathPosition pos, World world) {
        return blockAt(pos, world).getType() == Material.AIR;
    }

    protected static boolean isSolidBelow(PathPosition pos, World world) {
        return blockAt(pos.add(0, -1, 0), world).getType().isSolid();
    }

    protected static boolean isBreakable(PathPosition pos, World world) {
        return blockAt(pos, world).getType().getHardness() != -1;
    }

    protected static boolean isPassable(PathPosition pos, World world) {
        return blockAt(pos, world).isPassable();
    }

    protected static double getHardness(Material material) {
        return switch (material) {
            case STONE -> 1.5;
            case DIRT -> 0.5;
            case OAK_LOG, BIRCH_LOG, SPRUCE_LOG, DARK_OAK_LOG, ACACIA_LOG, JUNGLE_LOG, MANGROVE_LOG, CHERRY_LOG -> 2.0;
            case OBSIDIAN -> 50.0;
            default -> 1.0;
        };
    }

    protected static double getToolSpeed(Material material) {
        return switch (material) {
            case STONE -> 4.0;
            case DIRT, GRASS_BLOCK, SAND, GRAVEL -> 2.0;
            case OAK_LOG, BIRCH_LOG, SPRUCE_LOG, DARK_OAK_LOG, ACACIA_LOG, JUNGLE_LOG, MANGROVE_LOG, CHERRY_LOG -> 2.0;
            default -> 0.1;
        };
    }
}
