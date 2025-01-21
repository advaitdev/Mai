package me.advait.mai.brain.action.event;

import me.advait.mai.body.Humanoid;
import org.bukkit.Location;

public class HumanoidWalkToActionEvent extends HumanoidActionEvent {

    private final Location destination;

    public HumanoidWalkToActionEvent(Humanoid humanoid, Location destination) {
        super(humanoid);
        this.destination = destination;
    }

    public Location getDestination() {
        return destination;
    }

}
