package me.advait.mai.brain.action.result;

import me.advait.mai.Mai;
import me.advait.mai.brain.action.HumanoidAction;
import me.advait.mai.brain.action.event.HumanoidActionResultEvent;
import org.bukkit.Bukkit;

public class HumanoidActionResult {

    private final boolean success;
    private String message;

    public HumanoidActionResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public HumanoidActionResult(boolean success, String message, Class<?> resultClassOrigin) {
        this(success, message);
        if (!HumanoidAction.class.isAssignableFrom(resultClassOrigin)) {
            Mai.log().severe("A non-HumanoidAction class was passed into HumanoidActionResult: " + resultClassOrigin);
            throw new IllegalArgumentException("A non-HumanoidAction class was passed into HumanoidActionResult: " + resultClassOrigin);
        }
        // TODO: Call HumanoidActionResultEvent
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return (success ? "SUCCEEDED" : "FAILED") + ": " + message;
    }

}