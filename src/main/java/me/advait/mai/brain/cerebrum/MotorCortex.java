package me.advait.mai.brain.cerebrum;

import org.bukkit.Location;

// responsible for movement
public interface MotorCortex {

    void goTo(Location location);
    void wander();
    void mine();
    void place();
    void pickUpItem();
    void dropItem();
    void craft();
    void smelt();
    void attack();
    void shoot();

}
