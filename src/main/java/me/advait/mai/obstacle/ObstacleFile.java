package me.advait.mai.obstacle;

import org.bukkit.Material;

/**
 * A parsed {@code .obstacle} file: header metadata plus a 2D grid of
 * {@link Cell}s. Grid rows run north-to-south (increasing Z), columns run
 * west-to-east (increasing X). Exactly one start and one end cell exist; their
 * coordinates are resolved at parse time and exposed via the accessors below.
 *
 * @param stem          the file name without extension; used for result lines
 * @param name          the {@code name:} header value
 * @param description   the {@code description:} header value (may be empty)
 * @param material      block placed for grid stacks
 * @param floorMaterial block placed for the safety floor under each cell
 * @param timeoutTicks  ticks before a run is declared TIMEOUT
 * @param spawnYaw      yaw the humanoid faces when spawned
 * @param originY       Y level of the arena floor
 * @param botTool       tool given to the bot before the run (null = none)
 * @param botBlock      block type stocked in the bot's inventory (null = none)
 * @param botBlockCount how many {@code botBlock} to give
 * @param ceiling       if &gt;0, a solid roof at {@code originY+ceiling} over every
 *                      floored cell, enclosing the course into a tunnel
 * @param grid          {@code grid[row][col]} cell data
 * @param startRow      row index of the start cell
 * @param startCol      column index of the start cell
 * @param endRow        row index of the end cell
 * @param endCol        column index of the end cell
 */
public record ObstacleFile(
        String stem,
        String name,
        String description,
        Material material,
        Material floorMaterial,
        int timeoutTicks,
        int spawnYaw,
        int originY,
        Material botTool,
        Material botBlock,
        int botBlockCount,
        int ceiling,
        Cell[][] grid,
        int startRow, int startCol,
        int endRow, int endCol
) {

    public int rows() { return grid.length; }

    public int cols() { return grid.length == 0 ? 0 : grid[0].length; }

    public Cell startCell() { return grid[startRow][startCol]; }

    public Cell endCell() { return grid[endRow][endCol]; }

    /**
     * A single grid cell.
     *
     * @param hasFloor whether the safety floor block is present below the cell
     * @param height   number of {@code material} blocks stacked above the floor
     */
    public record Cell(boolean hasFloor, int height) {
        static final Cell VOID = new Cell(false, 0);
        static final Cell FLOOR = new Cell(true, 0);

        static Cell stack(int height) { return new Cell(true, height); }
    }
}
