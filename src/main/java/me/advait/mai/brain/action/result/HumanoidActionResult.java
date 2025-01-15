package me.advait.mai.brain.action.result;

public class HumanoidActionResult {

    private final boolean success;
    private String message;

    public HumanoidActionResult(boolean success, String message) {
        this.success = success;
        this.message = message;
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