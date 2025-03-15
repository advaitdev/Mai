package me.advait.patheticcitizens.npc.trait;

import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.api.trait.TraitName;
import net.citizensnpcs.npc.ai.CitizensNavigator;
import net.citizensnpcs.util.Util;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

@TraitName("SprintJump")
public class SprintJumpTrait extends Trait {

    private long lastJumpTime = 0;
    private static final long JUMP_COOLDOWN = 500; // 0.5 second cooldown

    public SprintJumpTrait() {
        super("sprintjump");
    }

    @Override
    public void run() {

        if (!npc.isSpawned()) return;
        if (!npc.getNavigator().isNavigating()) return;
        if (npc.getNavigator().isPaused()) return;

        LivingEntity entity = (LivingEntity) npc.getEntity();
        Location targetLocation = npc.getNavigator().getTargetAsLocation();

        if (npc.getStoredLocation().distance(targetLocation) < 3) return;

        Util.faceLocation(entity, targetLocation);

        long currentTime = System.currentTimeMillis();

        Location location = entity.getLocation();
        Vector velocity = location.getDirection().normalize().multiply(0.7);

        if (velocity.isZero()) return;

        if (entity.isOnGround() && currentTime - lastJumpTime > JUMP_COOLDOWN) {
            lastJumpTime = currentTime;

            npc.getNavigator().getLocalParameters().speedModifier(0.7f);

            velocity.setY(0.5);
            entity.setVelocity(velocity);
        }

    }

}
