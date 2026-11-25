package me.advait.mai.course;

import de.bsommerfeld.pathetic.bukkit.provider.FailingNavigationPointProvider;
import me.advait.mai.Mai;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pastes a {@link Course} into the (void) course world and clears it again.
 * A build writes only the cells that differ, so rebuilding between attempts
 * (undoing an opened door or a bot's mined block) is cheap. Main thread only.
 */
public final class CourseArena {

    /** Air cleared around the box on every side, so bot edits outside it are undone too. */
    private static final int MARGIN = 8;

    private final Course course;
    private final World world;
    private final int originX, originZ;
    private final BlockData[] palette;
    private boolean pinned;

    public CourseArena(Course course, World world, int originX, int originZ) {
        this.course = course;
        this.world = world;
        this.originX = originX;
        this.originZ = originZ;
        this.palette = new BlockData[course.palette().size()];
        for (int i = 0; i < palette.length; i++) {
            BlockData d;
            try {
                d = Bukkit.createBlockData(course.palette().get(i));
            } catch (IllegalArgumentException e) {
                d = Material.AIR.createBlockData();
            }
            // Random ticks are off in the course world, but a leaf that
            // could decay must never change the course between runs.
            if (d instanceof Leaves leaves) leaves.setPersistent(true);
            palette[i] = d;
        }
    }

    public Course course() { return course; }
    public World world() { return world; }

    /** Writes the course (and air over the margin), touching only cells that differ. */
    public void build() {
        pin();
        BlockData air = Material.AIR.createBlockData();
        int changed = 0;
        for (int x = -MARGIN; x < course.sizeX() + MARGIN; x++) {
            for (int z = -MARGIN; z < course.sizeZ() + MARGIN; z++) {
                boolean inX = x >= 0 && x < course.sizeX(), inZ = z >= 0 && z < course.sizeZ();
                for (int y = -2; y < course.sizeY() + MARGIN; y++) {
                    boolean in = inX && inZ && y >= 0 && y < course.sizeY();
                    BlockData want = in ? palette[course.blocks()[course.index(x, y, z)]] : air;
                    Block b = world.getBlockAt(originX + x, course.baseY() + y, originZ + z);
                    if (!b.getBlockData().equals(want)) {
                        b.setBlockData(want, false);
                        changed++;
                    }
                }
            }
        }
        invalidate();
        if (changed > 0 && me.advait.mai.brain.action.mechanic.movement.runnable.HumanoidWalkToRunnable.isDebugMode()) {
            Mai.getInstance().getLogger().info("[Course] built " + course.name() + " (" + changed + " blocks)");
        }
    }

    /** Clears everything the course and its margin occupy back to air. */
    public void clear() {
        BlockData air = Material.AIR.createBlockData();
        for (int x = -MARGIN; x < course.sizeX() + MARGIN; x++) {
            for (int z = -MARGIN; z < course.sizeZ() + MARGIN; z++) {
                for (int y = course.sizeY() + MARGIN - 1; y >= -2; y--) {
                    Block b = world.getBlockAt(originX + x, course.baseY() + y, originZ + z);
                    if (!b.getType().isAir()) b.setBlockData(air, false);
                }
            }
        }
        invalidate();
    }

    public Location start() {
        return new Location(world, originX + course.startX(), course.baseY() + course.startY(),
                originZ + course.startZ(), course.startYaw(), 0f);
    }

    /** Where to stand to finish: the center of the finish block's top. */
    public Location end() {
        return new Location(world, originX + Math.floor(course.endX()) + 0.5, course.baseY() + course.endY(),
                originZ + Math.floor(course.endZ()) + 0.5);
    }

    /** Standing on the finish: on the ground, the 0.6-wide feet over its column, at its height. */
    public boolean finished(Location feet, boolean onGround) {
        if (!onGround) return false;
        Location end = end();
        int bx = end.getBlockX(), bz = end.getBlockZ();
        boolean overX = feet.getX() + 0.3 > bx && feet.getX() - 0.3 < bx + 1;
        boolean overZ = feet.getZ() + 0.3 > bz && feet.getZ() - 0.3 < bz + 1;
        return overX && overZ && Math.abs(feet.getY() - end.getY()) < 0.75;
    }

    /** Fell below the course or wandered off its edge. */
    public String outOfBounds(Location feet) {
        if (feet.getY() < course.baseY() - 1) return "fell";
        double x = feet.getX() - originX, z = feet.getZ() - originZ;
        if (x < -4 || z < -4 || x > course.sizeX() + 4 || z > course.sizeZ() + 4) return "left_course";
        return null;
    }

    private void pin() {
        if (pinned) return;
        forEachChunk((cx, cz) -> {
            world.getChunkAt(cx, cz);
            world.addPluginChunkTicket(cx, cz, Mai.getInstance());
        });
        pinned = true;
    }

    private void invalidate() {
        forEachChunk((cx, cz) -> FailingNavigationPointProvider.invalidateChunk(world.getUID(), cx, cz));
    }

    private interface ChunkOp { void apply(int cx, int cz); }

    private void forEachChunk(ChunkOp op) {
        for (int cx = (originX - MARGIN) >> 4; cx <= (originX + course.sizeX() + MARGIN) >> 4; cx++) {
            for (int cz = (originZ - MARGIN) >> 4; cz <= (originZ + course.sizeZ() + MARGIN) >> 4; cz++) {
                op.apply(cx, cz);
            }
        }
    }

    /**
     * Captures the box [min, max] (inclusive) of {@code world} as a course
     * with the given start and finish (absolute positions inside the box).
     */
    public static Course capture(String name, String description, World world,
                                 int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                                 Location start, Location end, int timeoutTicks) {
        int sx = maxX - minX + 1, sy = maxY - minY + 1, sz = maxZ - minZ + 1;
        Map<BlockData, Integer> ids = new HashMap<>();
        List<String> palette = new ArrayList<>();
        int[] blocks = new int[sx * sy * sz];
        for (int x = 0; x < sx; x++) {
            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    BlockData d = world.getBlockAt(minX + x, minY + y, minZ + z).getBlockData();
                    Integer id = ids.get(d);
                    if (id == null) {
                        id = palette.size();
                        ids.put(d, id);
                        palette.add(d.getAsString());
                    }
                    blocks[(x * sy + y) * sz + z] = id;
                }
            }
        }
        String source = String.format("%s %d %d %d", world.getName(), minX, minY, minZ);
        return new Course(name, description, source, minY, sx, sy, sz, palette, blocks,
                start.getX() - minX, start.getY() - minY, start.getZ() - minZ, start.getYaw(),
                end.getX() - minX, end.getY() - minY, end.getZ() - minZ, timeoutTicks);
    }
}
