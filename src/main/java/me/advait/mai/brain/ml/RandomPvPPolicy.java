package me.advait.mai.brain.ml;

import java.util.Random;

public class RandomPvPPolicy implements PvPPolicy {

    private static final Random random = new Random();

    public PvPAction choose() {
        double forward = random.nextDouble() * 2 - 1;
        double strafe = random.nextDouble() * 2 - 1;

        boolean sprint = random.nextBoolean();
        double movementSpeed = sprint ? 0.287 : 0.215;
        double movementDistance = 1.5 + random.nextDouble() * 2.5;

        boolean jump = random.nextDouble() < 0.3;
        long blockDuration = random.nextDouble() < 0.2 ? (10 + random.nextInt(30)) : 0;

        boolean doAttack = random.nextDouble() < 0.6;
        double attackAngle = -30 + random.nextDouble() * 60;
        double minCooldown = 0.6 + random.nextDouble() * 0.4;

        return new PvPAction(
                doAttack ? PvPAction.Type.ATTACK : PvPAction.Type.IDLE,
                forward, strafe, movementSpeed, movementDistance,
                sprint, jump, attackAngle, minCooldown, blockDuration
        );
    }
}
