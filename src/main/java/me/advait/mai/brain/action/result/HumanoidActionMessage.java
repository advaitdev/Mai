package me.advait.mai.brain.action.result;

public final class HumanoidActionMessage {

    public static final String NPC_IS_NULL = "The player entity currently does not exist (most likely disconnected or is dead and about to respawn).";

    public static final String WALK_TO_MESSAGE_SUCCESS = "Arrived at destination via only walking on the ground.";
    public static final String WALK_TO_MESSAGE_STUCK = "Stuck at the same location for an extended period of time.";
    public static final String WALK_TO_MESSAGE_FAILURE = "Cannot reach destination via only walking on the ground.";

    public static final String MINE_MESSAGE_FAILURE_NO_TOOL = "No appropriate tool found to mine with. " +
            "Either forcibly mine the block if it isn't too difficult to mine and a large portion of it isn't needed, or get the appropriate tool.";
    public static final String MINE_MESSAGE_FAILURE_TOO_FAR = "Attempted to mine the block, but it is too far away to reach (more than 4.5 blocks).";
    public static final String MINE_MESSAGE_FAILURE_UNBREAKABLE = "This block is unbreakable (it may either be a liquid or an unbreakable block such as bedrock).";
    public static final String MINE_MESSAGE_FAILURE_NULL = "This block turned null (most likely disappeared or changed types while running the action).";
    public static final String MINE_MESSAGE_SUCCESS = "Mined block successfully.";

    public static final String BUILD_MESSAGE_FAILURE_INVALID_LOCATION = "A block cannot be placed in this location because a block is placed there.";
    public static final String BUILD_MESSAGE_FAILURE_TOO_FAR = "Attempted to build at this location, but it is too far away to reach (more than 4.5 blocks).";
    public static final String BUILD_MESSAGE_FAILURE_NOT_HOLDING_BLOCK = "Could not place the block at this location because it is not held (it may have moved inventory slots since the action was ran).";
    public static final String BUILD_MESSAGE_FAILURE_NOT_PLACEABLE = "The item held is not placeable (it may not be a utility material instead of a block).";
    public static final String BUILD_MESSAGE_SUCCESS = "Placed block successfully.";

}
