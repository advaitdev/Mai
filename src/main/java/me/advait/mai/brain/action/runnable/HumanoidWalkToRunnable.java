package me.advait.mai.brain.action.runnable;

import de.metaphoriker.pathetic.api.pathing.result.Path;
import de.metaphoriker.pathetic.bukkit.mapper.BukkitMapper;
import me.advait.mai.Mai;
import me.advait.mai.brain.action.result.HumanoidActionMessage;
import me.advait.mai.brain.action.result.HumanoidActionResult;
import me.advait.mai.npc.pathetic.PatheticAgent;
import me.advait.mai.util.NPCUtil;
import me.advait.mai.util.PatheticUtil;
import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class HumanoidWalkToRunnable extends BukkitRunnable {

    private final NPC npc;
    private final Location target;
    private final CompletableFuture<HumanoidActionResult> resultFuture;

    public HumanoidWalkToRunnable(NPC npc, Location target, CompletableFuture<HumanoidActionResult> resultFuture) {
        this.npc = npc;
        this.target = target;
        this.resultFuture = resultFuture;
    }

    private Path previousPath = null;

    @Override
    public void run() {
        if (npc == null || npc.getEntity() == null) {
            resultFuture.complete(new HumanoidActionResult(false, HumanoidActionMessage.NPC_IS_NULL));
            cancel();
            return;
        }

        if (NPCUtil.isNPCNearDestination(npc, target)) {
            resultFuture.complete(new HumanoidActionResult(true, HumanoidActionMessage.WALK_TO_MESSAGE_SUCCESS));
            cancel();
            return;
        }

        Navigator navigator = npc.getNavigator();

        var pathfindingResult = PatheticAgent.getInstance().getGroundPath(npc.getEntity().getLocation(), target);
        pathfindingResult.thenAccept(result -> {
            if (result.successful()) {
                Path path = result.getPath();
                List<Vector> pathVectors = new ArrayList<>();
                if (previousPath != null) {
                    if (PatheticUtil.isSubpathEquivalent(previousPath, path)) return;
                }

                path.forEach(pathPosition -> pathVectors.add(BukkitMapper.toVector(pathPosition.toVector())));
                navigator.setTarget(pathVectors);
                previousPath = path;
            }

            else {
                resultFuture.complete(new HumanoidActionResult(false, HumanoidActionMessage.WALK_TO_MESSAGE_FAILURE));
                cancel();
            }
        });
    }

}
