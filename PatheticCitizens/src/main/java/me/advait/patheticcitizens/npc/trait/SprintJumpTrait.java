package me.advait.patheticcitizens.npc.trait;

import net.citizensnpcs.api.trait.Trait;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

public class SprintJumpTrait extends Trait {

    private long lastJumpTime = 0;
    private static final long JUMP_COOLDOWN = 500; // 0.5 second cooldown

    public SprintJumpTrait() {
        super("sprintjump");
    }

    @Override
    public void run() {
        if (!npc.isSpawned()) return;

        LivingEntity entity = (LivingEntity) npc.getEntity();
        long currentTime = System.currentTimeMillis();

        if (entity.isOnGround() && currentTime - lastJumpTime > JUMP_COOLDOWN && npc.getNavigator().isNavigating()) {
            lastJumpTime = currentTime;

            npc.getNavigator().getLocalParameters().speedModifier(1.3f);

            Vector velocity = entity.getVelocity();
            velocity.setY(0.5);
            velocity.setX(velocity.getX() * 1.2);
            velocity.setZ(velocity.getZ() * 1.2);
            entity.setVelocity(velocity);
        }
    }
}
