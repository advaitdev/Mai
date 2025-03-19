package me.advait.patheticcitizens.navigator;

import de.metaphoriker.pathetic.bukkit.mapper.BukkitMapper;
import me.advait.patheticcitizens.PatheticCitizens;
import me.advait.patheticcitizens.pathfinder.PatheticAgent;
import net.citizensnpcs.api.ai.AbstractPathStrategy;
import net.citizensnpcs.api.ai.NavigatorParameters;
import net.citizensnpcs.api.ai.TargetType;
import net.citizensnpcs.api.astar.pathfinder.Path;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.util.NMS;
import net.citizensnpcs.util.Util;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public class PatheticNavigationStrategy extends AbstractPathStrategy {

    private Location current;
    private final Location destination;
    private final NPC citizensNPC;
    private final NavigatorParameters citizensParams;
    private Path citizensPlan;

    private boolean isPathfinding = false;

    private final PatheticAgent AGENT = PatheticAgent.getInstance();

    public PatheticNavigationStrategy(NPC citizensNPC, Location destination, NavigatorParameters citizensParams) {
        super(TargetType.LOCATION);
        this.citizensNPC = citizensNPC;
        this.citizensParams = citizensParams;
        this.destination = destination;
    }

    @Override
    public Location getCurrentDestination() {
        return this.current != null ? this.current : this.destination.clone();
    }

    @Override
    public Iterable<Vector> getPath() {
        return this.citizensPlan == null ? null : this.citizensPlan.getPath();
    }

    @Override
    public Location getTargetAsLocation() {
        return this.destination;
    }

    @Override
    public void stop() {
        this.citizensPlan = null;
    }

    public boolean isComplete() {
        return citizensNPC.getStoredLocation().distance(destination) <= citizensParams.pathDistanceMargin();
    }

    private void calculatePath() {
        isPathfinding = true;
        AGENT.getGroundPath(citizensNPC.getStoredLocation(), destination).thenAccept(result -> {
            if (result.successful()) {
                Bukkit.getScheduler().runTask(PatheticCitizens.getInstance(), () -> {
                    List<Vector> pathVectors = new ArrayList<>();
                    result.getPath().forEach(pathPosition -> pathVectors.add(BukkitMapper.toVector(pathPosition.toVector())));
                    this.citizensPlan = new Path(pathVectors);
                    isPathfinding = false;
                });
            } else {
                isPathfinding = false;  // TODO: Should this be true?
            }
        });
    }

    @Override
    public boolean update() {
        if (this.isComplete()) {
            stop();
            return true;
        }

        if (isPathfinding) return false;
        else calculatePath();

        if (this.citizensPlan != null && !this.citizensPlan.isComplete()) {
            Location loc = this.citizensNPC.getEntity().getLocation();

            if (this.current == null) this.current = this.citizensPlan.getCurrentVector().toLocation(loc.getWorld());

            Location dest = this.citizensPlan.isFinalEntry() ? this.current : Util.getCenterLocation(this.current.getBlock());

            double dX = dest.getX() - loc.getX();
            double dZ = dest.getZ() - loc.getZ();
            double dY = dest.getY() - loc.getY();
            double xzDistance = Math.sqrt(dX * dX + dZ * dZ);

            if (Math.abs(dY) < (double)1.0F && xzDistance <= this.citizensParams.distanceMargin()) {
                this.citizensPlan.update(this.citizensNPC);
                if (this.citizensPlan.isComplete()) {
                    return true;
                } else {
                    // This theoretically should never happen (?)
                    this.current = null;
                    return false;
                }
            }

            else {
                NMS.setDestination(this.citizensNPC.getEntity(), dest.getX(), dest.getY(), dest.getZ(), this.citizensParams.speedModifier());
            }

            this.citizensPlan.run(this.citizensNPC);
            return false;
        }

        else return true;
    }
}
