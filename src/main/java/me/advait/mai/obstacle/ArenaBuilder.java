package me.advait.mai.obstacle;

import de.bsommerfeld.pathetic.bukkit.provider.FailingNavigationPointProvider;
import me.advait.mai.Mai;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

/**
 * Builds an arena in-world from an {@link ObstacleFile} and tears it back down
 * leaving no trace.
 *
 * <p>Teardown restores a snapshot of the whole arena <em>bounding box</em> taken
 * before building — not just the blocks this class wrote. That matters because
 * the bot itself edits the world during a run (mining away walls, placing
 * cobblestone to bridge/pillar); tracking only our own writes would leave the
 * bot's blocks behind. Restoring the full box guarantees the region returns to
 * its exact original state regardless of what the run did.
 *
 * <p>All block access is synchronous and must run on the main server thread.
 */
public final class ArenaBuilder {

    /** Air blocks cleared above the tallest stack so the bot always has headroom. */
    private static final int HEADROOM = 3;
    /** Extra vertical room captured above the arena to cover bot-placed pillars. */
    private static final int EDIT_MARGIN = 6;

    private final ObstacleFile obs;
    private final World world;
    private final int originX;
    private final int originY;
    private final int originZ;

    private int boxMinX, boxMinY, boxMinZ;
    private BlockData[][][] snapshot;   // [x][y][z] original block data over the box
    private boolean built = false;

    public ArenaBuilder(ObstacleFile obs, World world, int originX, int originZ) {
        this.obs = obs;
        this.world = world;
        this.originX = originX;
        this.originY = obs.originY();
        this.originZ = originZ;
    }

    public World world() { return world; }

    /** World Y of the arena floor layer (used for fall detection). */
    public int floorY() { return originY; }

    public void build() {
        // The arena sits far from spawn, so its chunks would neither load nor
        // tick — leaving a spawned humanoid invalid (read as "died"). Pin the
        // spanned chunks with plugin tickets so entities tick during the run.
        // Tickets are kept for the plugin's lifetime (not removed on teardown):
        // churning them between back-to-back runs left the chunk unloaded mid
        // setBlockData and broke pathfinding. The arena always reuses the same
        // origin, so a handful of permanently-loaded chunks is harmless.
        pinChunks();

        int maxHeight = 0;
        for (int r = 0; r < obs.rows(); r++) {
            for (int c = 0; c < obs.cols(); c++) {
                maxHeight = Math.max(maxHeight, obs.grid()[r][c].height());
            }
        }
        int clearTop = originY + maxHeight + HEADROOM;
        int topY = Math.max(clearTop, obs.ceiling() > 0 ? originY + obs.ceiling() : clearTop) + EDIT_MARGIN;

        snapshotBox(topY);

        for (int r = 0; r < obs.rows(); r++) {
            for (int c = 0; c < obs.cols(); c++) {
                ObstacleFile.Cell cell = obs.grid()[r][c];
                int bx = originX + c;
                int bz = originZ + r;

                // Floor layer (or air for void cells, so falls register).
                set(bx, originY, bz, cell.hasFloor() ? obs.floorMaterial() : Material.AIR);

                // Stacked blocks above the floor.
                for (int h = 1; h <= cell.height(); h++) {
                    set(bx, originY + h, bz, obs.material());
                }

                // Clear headroom above the stack so the bot can stand/jump.
                for (int y = originY + cell.height() + 1; y <= clearTop; y++) {
                    set(bx, y, bz, Material.AIR);
                }

                // Optional roof: encloses floored cells into a tunnel so the
                // bot can't climb out (forces mine-through over climb-over).
                if (obs.ceiling() > 0 && cell.hasFloor()) {
                    set(bx, originY + obs.ceiling(), bz, obs.floorMaterial());
                }
            }
        }
        // Pathetic caches chunk snapshots in a static, never-auto-refreshed map.
        // We just rewrote these chunks, so drop the stale snapshots or the next
        // pathfind sees the previous arena's blocks and returns no_path.
        invalidateSnapshots();
        built = true;
    }

    public void teardown() {
        if (!built) return;
        // Restore the entire captured box (covers our blocks AND the bot's edits).
        // Top-down so a removed support can't trigger physics on a block below it.
        for (int yi = snapshot[0].length - 1; yi >= 0; yi--) {
            for (int xi = 0; xi < snapshot.length; xi++) {
                for (int zi = 0; zi < snapshot[0][0].length; zi++) {
                    world.getBlockAt(boxMinX + xi, boxMinY + yi, boxMinZ + zi)
                            .setBlockData(snapshot[xi][yi][zi], false);
                }
            }
        }
        snapshot = null;
        invalidateSnapshots();
        built = false;
    }

    private void snapshotBox(int topY) {
        boxMinX = originX - 1;
        boxMinY = originY - 1;
        boxMinZ = originZ - 1;
        int sizeX = obs.cols() + 2;
        int sizeY = topY - boxMinY + 1;
        int sizeZ = obs.rows() + 2;
        snapshot = new BlockData[sizeX][sizeY][sizeZ];
        for (int xi = 0; xi < sizeX; xi++) {
            for (int yi = 0; yi < sizeY; yi++) {
                for (int zi = 0; zi < sizeZ; zi++) {
                    snapshot[xi][yi][zi] = world.getBlockAt(
                            boxMinX + xi, boxMinY + yi, boxMinZ + zi).getBlockData();
                }
            }
        }
    }

    private void invalidateSnapshots() {
        forEachChunk((cx, cz) ->
                FailingNavigationPointProvider.invalidateChunk(world.getUID(), cx, cz));
    }

    private void pinChunks() {
        forEachChunk((cx, cz) -> {
            world.getChunkAt(cx, cz);   // force a synchronous load
            world.addPluginChunkTicket(cx, cz, Mai.getInstance());
        });
    }

    private interface ChunkOp { void apply(int cx, int cz); }

    private void forEachChunk(ChunkOp op) {
        int minCX = (originX - 1) >> 4;
        int maxCX = (originX + obs.cols()) >> 4;
        int minCZ = (originZ - 1) >> 4;
        int maxCZ = (originZ + obs.rows()) >> 4;
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                op.apply(cx, cz);
            }
        }
    }

    public Location startLocation() {
        return topCenter(obs.startRow(), obs.startCol(), obs.startCell().height(), obs.spawnYaw());
    }

    /** Walk target: center of the END column's top face. */
    public Location endLocation() {
        return topCenter(obs.endRow(), obs.endCol(), obs.endCell().height(), 0f);
    }

    public int endHeight() { return obs.endCell().height(); }
    public int endBlockX() { return originX + obs.endCol(); }
    public int endBlockZ() { return originZ + obs.endRow(); }

    private Location topCenter(int row, int col, int height, float yaw) {
        return new Location(world,
                originX + col + 0.5,
                originY + height + 1,
                originZ + row + 0.5,
                yaw, 0f);
    }

    private void set(int x, int y, int z, Material material) {
        world.getBlockAt(x, y, z).setType(material, false);
    }
}
