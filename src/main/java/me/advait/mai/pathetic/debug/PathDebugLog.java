package me.advait.mai.pathetic.debug;

import me.advait.mai.Mai;
import me.advait.mai.brain.action.mechanic.movement.runnable.HumanoidWalkToRunnable;

import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * One-line event logger for the pathfinding/locomotion pipeline. Writes to
 * Mai's plugin logger (console + {@code latest.log}) when
 * {@link HumanoidWalkToRunnable#isDebugMode()} is on. Independently of that
 * flag, an optional {@linkplain #setSink sink} receives every event — the
 * obstacle harness attaches one per run so each per-run log is a complete,
 * self-contained record of what the bot decided and why.
 *
 * <p>The goal is "what did the bot decide, and why?" — so call sites log
 * one-shot events (waypoint advances, emergency jumps, replans) rather
 * than per-tick state. Events are prefixed with {@code [PathDebug]} for
 * easy grepping: {@code tail -f latest.log | grep PathDebug}.
 */
public final class PathDebugLog {

    private static final Logger LOGGER = Mai.getInstance().getLogger();
    private static final String PREFIX = "[PathDebug] ";

    private static volatile Consumer<String> sink;

    private PathDebugLog() {}

    /**
     * Installs (or with {@code null}, removes) a consumer that receives every
     * event regardless of the console debug flag. Main thread only; a single
     * sink is supported — the obstacle harness runs one test at a time.
     */
    public static void setSink(Consumer<String> consumer) {
        sink = consumer;
    }

    /** Log an event. No-op when debug mode is off and no sink is attached. */
    public static void event(String msg) {
        boolean console = HumanoidWalkToRunnable.isDebugMode();
        Consumer<String> s = sink;
        if (!console && s == null) return;
        if (console) LOGGER.info(PREFIX + msg);
        if (s != null) s.accept(msg);
    }

    /** Log a formatted event (String.format). No-op when nothing is listening. */
    public static void event(String fmt, Object... args) {
        boolean console = HumanoidWalkToRunnable.isDebugMode();
        Consumer<String> s = sink;
        if (!console && s == null) return;
        String msg = String.format(fmt, args);
        if (console) LOGGER.info(PREFIX + msg);
        if (s != null) s.accept(msg);
    }
}
