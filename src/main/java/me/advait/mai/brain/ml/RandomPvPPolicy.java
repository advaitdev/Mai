package me.advait.mai.brain.ml;

import java.util.Random;

public class RandomPvPPolicy implements PvPPolicy {

    private static final Random random = new Random();

    public PvPAction choose() {
        // Random movement axes
        double forward = random.nextDouble() * 2 - 1; // [-1, 1]
        double strafe = random.nextDouble() * 2 - 1;  // [-1, 1]

        // Random movement type
        boolean sprint = random.nextBoolean();
        double movementSpeed = sprint ? 0.287 : 0.215;
        double movementDistance = 1.5 + random.nextDouble() * 2.5; // 1.5 - 4 blocks

        // Random jump or block
        boolean jump = random.nextDouble() < 0.3; // 30% chance to jump
        long blockDuration = random.nextDouble() < 0.2 ? (10 + random.nextInt(30)) : 0; // 20% chance

        // Random attack
        boolean doAttack = random.nextDouble() < 0.6; // 60% chance to attack
        double attackAngle = -30 + random.nextDouble() * 60; // [-30, +30] degrees
        double minCooldown = 0.6 + random.nextDouble() * 0.4; // [0.6 - 1.0]

        return new PvPAction(
                doAttack ? PvPAction.Type.ATTACK : PvPAction.Type.IDLE,
                forward,
                strafe,
                movementSpeed,
                movementDistance,
                sprint,
                jump,
                attackAngle,
                minCooldown,
                blockDuration
        );
    }

}
