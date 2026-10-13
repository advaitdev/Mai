package me.advait.mai.assistant;

/**
 * A long-running activity a humanoid keeps doing until told otherwise
 * (follow someone, guard them, fight a target). Ticked once per server tick
 * by {@link Assistant}. One-shot jobs (walk somewhere, mine a block) go
 * through the action queue instead; starting either one stops the other.
 */
public interface Behavior {

    /** Short machine-friendly name, e.g. {@code follow}. */
    String name();

    /** One line for status output, e.g. {@code following advaits (3.1 blocks)}. */
    String describe();

    /** Run one tick. Return false when the behavior is finished. */
    boolean tick();

    /** Release anything the behavior holds (walks, state). Called once. */
    default void stop() {}
}
