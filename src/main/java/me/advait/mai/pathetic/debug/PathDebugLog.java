package me.advait.mai.pathetic.debug;

import me.advait.mai.Mai;
import me.advait.mai.brain.action.mechanic.movement.runnable.HumanoidWalkToRunnable;

import java.util.logging.Logger;

/**
 * One-line event logger for the pathfinding/locomotion pipeline. Writes to
 * Mai's plugin logger (console + {@code latest.log}) when
 * {@link HumanoidWalkToRunnable#isDebugMode()} is on. Silent otherwise.
 *
 * <p>The goal is "what did the bot decide, and why?" — so call sites log
 * one-shot events (waypoint advances, emergency jumps, replans) rather
 * than per-tick state. Events are prefixed with {@code [PathDebug]} for
 * easy grepping: {@code tail -f latest.log | grep PathDebug}.
 */
public final class PathDebugLog {

    private static final Logger LOGGER = Mai.getInstance().getLogger();
    private static final String PREFIX = "[PathDebug] ";

    private PathDebugLog() {}

    /** Log an event. No-op when debug mode is off. */
    public static void event(String msg) {
        if (!HumanoidWalkToRunnable.isDebugMode()) return;
        LOGGER.info(PREFIX + msg);
    }

    /** Log a formatted event (String.format). No-op when debug mode is off. */
    public static void event(String fmt, Object... args) {
        if (!HumanoidWalkToRunnable.isDebugMode()) return;
        LOGGER.info(PREFIX + String.format(fmt, args));
    }
}
