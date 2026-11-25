package me.advait.mai.course;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Every attempt on a course, human and bot, one CSV line each in
 * {@code course-runs/<course>/results.csv}:
 * {@code time,runner,kind,status,ticks,log}. A discarded attempt stays in the
 * file with status {@code DISCARDED} and no longer counts.
 */
final class CourseResults {

    record Attempt(String time, String runner, String kind, String status, int ticks, String log) {
        boolean finished() { return "FINISHED".equals(status); }
        String line() { return String.join(",", time, runner, kind, status, Integer.toString(ticks), log); }
    }

    private final File file;

    CourseResults(File dir) {
        this.file = new File(dir, "results.csv");
    }

    synchronized List<Attempt> all() {
        List<Attempt> out = new ArrayList<>();
        if (!file.exists()) return out;
        try {
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                String[] p = line.split(",", -1);
                if (p.length < 6 || p[0].equals("time")) continue;
                try {
                    out.add(new Attempt(p[0], p[1], p[2], p[3], Integer.parseInt(p[4]), p[5]));
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
        return out;
    }

    synchronized void add(String runner, String kind, String status, int ticks, File log) {
        List<Attempt> all = all();
        all.add(new Attempt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME).substring(0, 19),
                runner, kind, status, ticks, log == null ? "" : log.getName()));
        write(all);
    }

    /** Marks this runner's most recent attempt discarded; returns it, or null if there was none. */
    synchronized Attempt discardLast(String runner) {
        List<Attempt> all = all();
        for (int i = all.size() - 1; i >= 0; i--) {
            Attempt a = all.get(i);
            if (!a.runner().equals(runner) || a.status().equals("DISCARDED")) continue;
            all.set(i, new Attempt(a.time(), a.runner(), a.kind(), "DISCARDED", a.ticks(), a.log()));
            write(all);
            return a;
        }
        return null;
    }

    /** Fastest finished time of this kind ("human" or "bot"), or -1. */
    int best(String kind) {
        int best = -1;
        for (Attempt a : all()) {
            if (a.finished() && a.kind().equals(kind) && (best < 0 || a.ticks() < best)) best = a.ticks();
        }
        return best;
    }

    List<Integer> finishedTicks(String kind) {
        List<Integer> out = new ArrayList<>();
        for (Attempt a : all()) if (a.finished() && a.kind().equals(kind)) out.add(a.ticks());
        return out;
    }

    private void write(List<Attempt> all) {
        List<String> lines = new ArrayList<>();
        lines.add("time,runner,kind,status,ticks,log");
        for (Attempt a : all) lines.add(a.line());
        try {
            file.getParentFile().mkdirs();
            Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }
}
