package me.advait.mai.brain.action.result;

public final class HumanoidActionMessage {

    public static final String WALK_TO_MESSAGE_SUCCESS = "Arrived at destination via only walking on the ground.";
    public static final String WALK_TO_MESSAGE_FAILURE = "Cannot reach destination via only walking on the ground.";

    public static final String MINE_MESSAGE_FAILURE_NO_TOOL = "No appropriate tool found to mine with. " +
            "Either forcibly mine the block if it isn't too difficult to mine and a large portion of it isn't needed, or get the appropriate tool.";

}
