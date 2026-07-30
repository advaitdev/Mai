package me.advait.mai.obstacle;

import java.util.UUID;

/**
 * Outcome of a single obstacle test run. The {@link #line} rendering is the
 * machine-parseable contract downstream tooling greps for via the
 * {@code [Obstacle] RESULT} prefix — keep the prefix and field layout stable.
 */
public record Result(Status status, String reason) {

    public enum Status { PASS, FAIL, TIMEOUT, ERROR }

    public static Result pass() { return new Result(Status.PASS, "arrived"); }
    public static Result timeout() { return new Result(Status.TIMEOUT, "timeout"); }
    public static Result fail(String reason) { return new Result(Status.FAIL, reason); }
    public static Result error(String reason) { return new Result(Status.ERROR, reason); }

    public String line(String stem, int ticks, UUID humanoid) {
        return String.format("[Obstacle] RESULT name=%s status=%s ticks=%d reason=%s humanoid=%s",
                stem, status, ticks, reason, humanoid == null ? "none" : humanoid);
    }
}
