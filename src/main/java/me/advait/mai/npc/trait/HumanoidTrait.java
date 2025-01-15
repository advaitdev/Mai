package me.advait.mai.npc.trait;

import de.metaphoriker.pathetic.api.pathing.result.Path;
import de.metaphoriker.pathetic.bukkit.mapper.BukkitMapper;
import me.advait.mai.Mai;
import me.advait.mai.npc.pathetic.PatheticAgent;
import me.advait.mai.util.PatheticUtil;
import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.api.trait.TraitName;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

@TraitName("humanoid")
public class HumanoidTrait extends Trait {

    public HumanoidTrait() {  // Constructor cannot have any parameters (according to Citizens).
        super("humanoid");
    }

    private int pathfindingRunnableID;
    private Path previousPath = null;

    @Override
    public void onSpawn() {
        this.pathfindingRunnableID = Bukkit.getScheduler().scheduleSyncRepeatingTask(Mai.getInstance(), () -> {

            Navigator navigator = npc.getNavigator();
            Location target = navigator.getTargetAsLocation();
            if (target == null) return;

            var pathfindingResult = PatheticAgent.getInstance().getGroundPath(npc.getEntity().getLocation(), target);
            pathfindingResult.thenAccept(result -> {

               Path path = result.getPath();
               List<Vector> pathVectors = new ArrayList<>();
               if (previousPath != null) {
                   if (PatheticUtil.isSubpathEquivalent(previousPath, path)) return;
               }
               path.forEach(pathPosition -> pathVectors.add(BukkitMapper.toVector(pathPosition.toVector())));
               navigator.setTarget(pathVectors);
               previousPath = path;

            });

        }, 0, 10);
    }

    @Override
    public void onDespawn() {
        Bukkit.getScheduler().cancelTask(this.pathfindingRunnableID);
        previousPath = null;
    }

    @Override
    public void onRemove() {
        Bukkit.getScheduler().cancelTask(this.pathfindingRunnableID);
        previousPath = null;
    }

        // target's y-level > npc's y-level = build up
        // target's y-level < npc's y-level = mine down
        // target in front & npc path not on ground = build towards
        // target in front & npc path on ground = mine towards

}
