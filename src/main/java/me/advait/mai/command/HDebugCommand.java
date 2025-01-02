package me.advait.mai.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import de.metaphoriker.pathetic.api.pathing.Pathfinder;
import de.metaphoriker.pathetic.api.pathing.result.PathfinderResult;
import de.metaphoriker.pathetic.api.wrapper.PathPosition;
import de.metaphoriker.pathetic.bukkit.mapper.BukkitMapper;
import me.advait.mai.Mai;
import me.advait.mai.npc.HumanoidUtil;
import me.advait.mai.npc.NPCUtil;
import me.advait.mai.npc.pathetic.SolidGroundFilter;
import me.advait.mai.npc.trait.pathfinder.PatheticAgent;
import me.advait.mai.util.Messages;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.npc.NPC;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;

@CommandAlias("hdebug")
public class HDebugCommand extends BaseCommand {

    @CommandAlias("jump")
    public void runJump(Player player) {
        NPC npc = CitizensAPI.getDefaultNPCSelector().getSelected(player);
        if (npc == null) {
            Messages.sendMessage(player, "&cYou have no NPC selected!");
            return;
        }

        HumanoidUtil.jump(npc, 0.5);
    }

    @CommandAlias("pile")
    public void runPile(Player player) {
        NPC npc = CitizensAPI.getDefaultNPCSelector().getSelected(player);
        if (npc == null) {
            Messages.sendMessage(player, "&cYou have no NPC selected!");
            return;
        }

        boolean didPile = HumanoidUtil.pileUp(npc);
        if (!didPile) Messages.sendMessage(player, "&cNPC could not place the block below!");
    }

    @CommandAlias("dig")
    public void runDig(Player player) {
        NPC npc = CitizensAPI.getDefaultNPCSelector().getSelected(player);
        if (npc == null) {
            Messages.sendMessage(player, "&cYou have no NPC selected!");
            return;
        }

        HumanoidUtil.mineBlock(npc, npc.getEntity().getLocation().clone().subtract(0, 1, 0), new ItemStack(Material.DIAMOND_PICKAXE));
    }

    @CommandAlias("mine")
    public void runMine(Player player) {
        NPC npc = CitizensAPI.getDefaultNPCSelector().getSelected(player);
        if (npc == null) {
            Messages.sendMessage(player, "&cYou have no NPC selected!");
            return;
        }

        boolean didMine = HumanoidUtil.mineTowardsTarget(npc, player.getLocation(), new ItemStack(Material.DIAMOND_PICKAXE));
        if (!didMine) {
            Messages.sendMessage(player, "&cNPC could not mine to your location!");
        }
    }

    @CommandAlias("bridge")
    public void runBridge(Player player) {
        NPC npc = CitizensAPI.getDefaultNPCSelector().getSelected(player);
        if (npc == null) {
            Messages.sendMessage(player, "&cYou have no NPC selected!");
            return;
        }

        boolean didBridge = HumanoidUtil.bridgeTowardsTarget(npc, player.getLocation(), Material.DIAMOND_BLOCK);
        if (!didBridge) {
            Messages.sendMessage(player, "&cNPC could not bridge to your location!");
        }
    }

    @CommandAlias("getblockinfrontatfootlevel")
    public void runGetBlockInFrontAtFootLevel(Player player) {
        Messages.sendMessage(player, "&aBlock in front at foot level: " + HumanoidUtil.getBlockInFrontAtFootLevel(player).toString());
    }

    @CommandAlias("ispathpossible")
    public void runIsPathPossible(Player player) {
        NPC npc = CitizensAPI.getDefaultNPCSelector().getSelected(player);
        if (npc == null) {
            Messages.sendMessage(player, "&cYou have no NPC selected!");
            return;
        }

        long startTime = System.currentTimeMillis();
        boolean canNavigateTo = npc.getNavigator().canNavigateTo(player.getLocation());
        long elapsedTime = (System.currentTimeMillis() - startTime);
        Messages.sendMessage(player, "&aResult for path: " + canNavigateTo + " (" + elapsedTime + " ms)");
    }

    @CommandAlias("gotome")
    public void runGoToMe(Player player) {
        NPC npc = CitizensAPI.getDefaultNPCSelector().getSelected(player);
        if (npc == null) {
            Messages.sendMessage(player, "&cYou have no NPC selected!");
            return;
        }

        Navigator navigator = npc.getNavigator();
        Location targetLocation = player.getLocation();
        Pathfinder pathfinder = PatheticAgent.getInstance().getPathfinder();

        PathPosition start = BukkitMapper.toPathPosition(npc.getEntity().getLocation());
        PathPosition end = BukkitMapper.toPathPosition(targetLocation);

        CompletionStage<PathfinderResult> pathfindingResult = pathfinder.findPath(
                start,
                end,
                List.of(new SolidGroundFilter()));

        pathfindingResult.thenAccept(result -> {
            if (result.successful()) {
                List<Vector> pathVectors = new ArrayList<>();
                result.getPath().forEach(position -> {
                    pathVectors.add(BukkitMapper.toVector(position.toVector()));
                });

                navigator.setTarget(pathVectors);
                Messages.sendMessage(player, "&aFound path of length " + result.getPath().length() + ", going to your location!");
            }

            else {
                Messages.sendMessage(player, "&cFailed to find path!");
            }
        });
    }

}
