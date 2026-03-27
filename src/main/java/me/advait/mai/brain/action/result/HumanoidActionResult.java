package me.advait.mai.brain.action.result;

public record HumanoidActionResult(boolean success, String message) {

    @Override
    public String toString() {
        return (success ? "SUCCEEDED" : "FAILED") + ": " + message;
    }
}
