package me.advait.mai.course;

import me.advait.mai.pathetic.debug.PathDebugLog;
import org.bukkit.Location;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * One course attempt's tick-by-tick log, in the obstacle log's format so
 * human and bot runs read (and parse) the same: position, velocity (this
 * tick's displacement), ground flag, keys and view. A bot log also carries
 * every PathDebug event stamped with its tick.
 */
final class CourseLog {

    private final File file;
    private PrintWriter writer;
    private int currentTick;
    private boolean sink;

    CourseLog(File file) {
        this.file = file;
        try {
            file.getParentFile().mkdirs();
            writer = new PrintWriter(file);
        } catch (IOException e) {
            writer = null;
        }
    }

    File file() { return file; }

    void header(Course course, String runner, Location start, Location end, boolean captureDebug) {
        if (writer == null) return;
        writer.println("# Course: " + course.name());
        writer.println("description=" + course.description());
        writer.println("runner=" + runner);
        writer.printf("start=(%.3f,%.3f,%.3f) yaw=%.1f end=(%.2f,%.2f,%.2f)%n",
                start.getX(), start.getY(), start.getZ(), start.getYaw(), end.getX(), end.getY(), end.getZ());
        writer.println("ticks (t=1 is the first tick that moved):");
        writer.flush();
        if (captureDebug) {
            sink = true;
            PathDebugLog.setSink(msg -> {
                writer.println("  [" + currentTick + "] " + msg);
                writer.flush();
            });
        }
    }

    void tick(int tick, Location loc, double vx, double vy, double vz, boolean onGround, String input) {
        if (writer == null) return;
        currentTick = tick;
        writer.printf("t=%d x=%.3f y=%.3f z=%.3f vx=%.3f vy=%.3f vz=%.3f hspeed=%.3f onGround=%b %s%n",
                tick, loc.getX(), loc.getY(), loc.getZ(), vx, vy, vz, Math.hypot(vx, vz), onGround, input);
    }

    void finish(String status, String reason, int ticks) {
        if (sink) PathDebugLog.setSink(null);
        if (writer == null) return;
        writer.println("result: status=" + status + " reason=" + reason + " ticks=" + ticks);
        writer.close();
        writer = null;
    }
}
