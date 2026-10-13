package me.advait.mai.obstacle;

import me.advait.mai.Mai;
import me.advait.mai.pathetic.debug.PathDebugLog;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Per-run debug log. Writes one file per test under
 * {@code plugins/Mai/obstacle-logs/<timestamp>-<stem>.log} containing the
 * parsed obstacle, arena origin, tick-by-tick humanoid state (position,
 * velocity, ground flag), every {@link PathDebugLog} event the locomotion
 * pipeline emitted (indented, stamped with the tick it happened on), and the
 * final result. This is the artifact to read to diagnose a FAIL or to
 * calibrate physics: the velocity columns are the bot's actual per-tick
 * motion on this server.
 */
public final class ObstacleLog {

    private final PrintWriter writer;
    private final boolean ok;
    /** Tick most recently written by {@link #tick}; stamps PathDebug events. */
    private int currentTick = 0;

    public ObstacleLog(ObstacleFile obs) {
        File dir = new File(Mai.getInstance().getDataFolder(), "obstacle-logs");
        dir.mkdirs();
        String ts = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        File file = new File(dir, ts + "-" + obs.stem() + ".log");

        PrintWriter w = null;
        boolean success = false;
        try {
            w = new PrintWriter(file);
            success = true;
        } catch (IOException e) {
            Mai.getInstance().getLogger().warning("Could not open obstacle log: " + e.getMessage());
        }
        this.writer = w;
        this.ok = success;
    }

    public void header(ObstacleFile obs, ArenaBuilder arena, Location start, Location end) {
        if (!ok) return;
        writer.println("# Obstacle: " + obs.stem());
        writer.println("name=" + obs.name());
        writer.println("description=" + obs.description());
        writer.println("material=" + obs.material() + " floor_material=" + obs.floorMaterial());
        writer.println("timeout_ticks=" + obs.timeoutTicks() + " spawn_yaw=" + obs.spawnYaw());
        writer.printf("arena_world=%s floor_y=%d%n", arena.world().getName(), arena.floorY());
        writer.printf("start=(%.2f,%.2f,%.2f) end=(%.2f,%.2f,%.2f) end_block=(%d,%d) end_height=%d%n",
                start.getX(), start.getY(), start.getZ(),
                end.getX(), end.getY(), end.getZ(),
                arena.endBlockX(), arena.endBlockZ(), arena.endHeight());
        writer.println("grid:");
        for (int r = 0; r < obs.rows(); r++) {
            StringBuilder sb = new StringBuilder("  ");
            for (int c = 0; c < obs.cols(); c++) {
                ObstacleFile.Cell cell = obs.grid()[r][c];
                if (r == obs.startRow() && c == obs.startCol()) sb.append("S").append(cell.height());
                else if (r == obs.endRow() && c == obs.endCol()) sb.append("E").append(cell.height());
                else if (!cell.hasFloor()) sb.append("_ ");
                else sb.append(cell.height()).append(" ");
                sb.append(' ');
            }
            writer.println(sb.toString().stripTrailing());
        }
        writer.println("ticks:");
        writer.flush();

        // Capture every locomotion decision into this file for the run's
        // duration. The watcher logs the tick line before the driver runs,
        // so events stamped [t] happened during tick t's decision pass.
        PathDebugLog.setSink(msg -> {
            writer.println("  [" + currentTick + "] " + msg);
            writer.flush();
        });
    }

    public void tick(int tick, Location loc, Vector vel, boolean onGround, String movementKey) {
        if (!ok) return;
        currentTick = tick;
        double hSpeed = Math.sqrt(vel.getX() * vel.getX() + vel.getZ() * vel.getZ());
        writer.printf("t=%d x=%.3f y=%.3f z=%.3f vx=%.3f vy=%.3f vz=%.3f hspeed=%.3f onGround=%b move=%s%n",
                tick, loc.getX(), loc.getY(), loc.getZ(),
                vel.getX(), vel.getY(), vel.getZ(), hSpeed, onGround, movementKey);
        writer.flush();
    }

    public void finish(Result result, int ticks) {
        PathDebugLog.setSink(null);
        if (!ok) return;
        writer.println("result: status=" + result.status() + " reason=" + result.reason() + " ticks=" + ticks);
        writer.flush();
        writer.close();
    }
}
