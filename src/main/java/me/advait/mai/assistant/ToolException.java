package me.advait.mai.assistant;

/** A tool or command could not run; the message is shown to the caller as-is. */
public class ToolException extends RuntimeException {
    public ToolException(String message) {
        super(message);
    }
}
