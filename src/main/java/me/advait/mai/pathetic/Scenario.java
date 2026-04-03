package me.advait.mai.pathetic;

import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import de.bsommerfeld.pathetic.bukkit.mapper.BukkitMapper;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Movement scenarios for humanoid pathfinding.
 * Only scenarios the bot can actually perform are included.
 * Moves that don't match any scenario are heavily penalized by the cost processor.
 */
public enum Scenario {

    WALK_FLAT {
        @Override
        public boolean matches(PathPosition current, PathPosition previous, World world) {
            return deltaY(current, previous) == 0
                    && isSolidBelow(current, world)
                    && isPassable(current, world)
                    && isPassable(current.add(0, 1, 0), world);
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
                    && isPassable(current, world)
                    && isPassable(current.add(0, 1, 0), world);
        }

        @Override
        public double computeCost(PathPosition current, PathPosition previous, World world) {
            return 1.5;
        }
    },

    FALL_SAFE {
        @Override
        public boolean matches(PathPosition current, PathPosition previous, World world) {
            int dy = deltaY(current, previous);
            return dy < 0 && Math.abs(dy) <= 3
                    && isSolidBelow(current, world)
                    && isPassable(current, world)
                    && isPassable(current.add(0, 1, 0), world);
        }

        @Override
        public double computeCost(PathPosition current, PathPosition previous, World world) {
            return 1.5 + Math.abs(deltaY(current, previous)) * 0.5;
        }
    },

    LADDER_CLIMB {
        @Override
        public boolean matches(PathPosition current, PathPosition previous, World world) {
            Material mat = blockAt(current, world).getType();
            return (mat == Material.LADDER || mat == Material.VINE)
                    && isPassable(current.add(0, 1, 0), world);
        }

        @Override
        public double computeCost(PathPosition current, PathPosition previous, World world) {
            return 1.8;
        }
    },

    SWIM {
        @Override
        public boolean matches(PathPosition current, PathPosition previous, World world) {
            Material feet = blockAt(current, world).getType();
            return feet == Material.WATER;
        }

        @Override
        public double computeCost(PathPosition current, PathPosition previous, World world) {
            return 3.0;
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

    protected static boolean isPassable(PathPosition pos, World world) {
        return blockAt(pos, world).isPassable();
    }

    protected static boolean isSolidBelow(PathPosition pos, World world) {
        return blockAt(pos.add(0, -1, 0), world).getType().isSolid();
    }
}
