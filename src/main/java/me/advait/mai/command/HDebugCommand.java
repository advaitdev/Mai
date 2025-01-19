package me.advait.mai.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import me.advait.mai.Catalog;
import me.advait.mai.body.Humanoid;
import me.advait.mai.brain.action.HumanoidActionChain;
import me.advait.mai.brain.action.HumanoidMineAction;
import me.advait.mai.brain.action.HumanoidWalkToAction;
import me.advait.mai.npc.HumanoidUtil;
import me.advait.mai.util.LocationUtil;
import me.advait.mai.util.Messages;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
        Humanoid humanoid = Catalog.getInstance().getAllHumanoids().get(0);
        if (humanoid == null) {
            Messages.sendMessage(player, "&cNo humanoid exists in this world!");
            return;
        }

        NPC npc = humanoid.getNpc();
        Block inFront = LocationUtil.getBlockInFrontAtEyeLevel((Player) npc.getEntity(), 1);
        BlockState inFrontState = inFront.getState();

        var mineAction = new HumanoidMineAction(humanoid, inFront, true);
        var mineActionResult = mineAction.run();

        mineActionResult.thenAccept(result -> {
            if (result.isSuccess()) {
                Messages.sendMessage(player, "&a" + result);
            } else {
                Messages.sendMessage(player, "&c" + result);
            }
        }).exceptionally(ex -> {
            Messages.sendMessage(player, "&An error occurred: " + ex.getMessage());
            ex.printStackTrace();
            return null;
        });
    }

    @CommandAlias("gotoandmine")
    public void runGotoAndMine(Player player) {

        Humanoid humanoid = Catalog.getInstance().getAllHumanoids().get(0);
        if (humanoid == null) {
            Messages.sendMessage(player, "&cNo humanoid exists in this world!");
            return;
        }

        var walkToAction = new HumanoidWalkToAction(humanoid, player.getLocation());
        var mineAction = new HumanoidMineAction(humanoid, player.getTargetBlockExact(3), true);

        HumanoidActionChain.executeChainedActions(walkToAction, mineAction).thenAccept(result -> {
                    if (result.isSuccess()) {
                        Messages.sendMessage(player, "&aAll actions completed successfully!");
                    } else {
                        Messages.sendMessage(player, "&c" + result);
                    }
                })
                .exceptionally(ex -> {
                    Messages.sendMessage(player, "&cAn error occurred during the action chain: " + ex.getMessage());
                    ex.printStackTrace();
                    return null;
                });

    }

    @CommandAlias("minewithtool")
    public void runMineWithTool(Player player) {
        Humanoid humanoid = Catalog.getInstance().getAllHumanoids().get(0);
        if (humanoid == null) {
            Messages.sendMessage(player, "&cNo humanoid exists in this world!");
            return;
        }

        NPC npc = humanoid.getNpc();
        Block inFront = LocationUtil.getBlockInFrontAtEyeLevel((Player) npc.getEntity(), 1);

        var mineAction = new HumanoidMineAction(humanoid, inFront, false);
        var mineActionResult = mineAction.run();

        mineActionResult.thenAccept(result -> {
            if (result.isSuccess()) {
                Messages.sendMessage(player, "&aSuccess: " + result.getMessage() + " - Block: " + inFront);
            } else {
                Messages.sendMessage(player, "&cFailure: " + result.getMessage() + " - Block: " + inFront);
            }
        }).exceptionally(ex -> {
            Messages.sendMessage(player, "&An error occurred: " + ex.getMessage());
            ex.printStackTrace();
            return null;
        });
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
        Humanoid humanoid = Catalog.getInstance().getAllHumanoids().get(0);
        if (humanoid == null) {
            Messages.sendMessage(player, "&cNo humanoid exists in this world!");
            return;
        }

        var walkToAction = new HumanoidWalkToAction(humanoid, player.getLocation());
        var walkToActionResult = walkToAction.run();

        walkToActionResult.thenAccept(result -> {
            if (result.isSuccess()) {
                Messages.sendMessage(player, "&a" + result);
            } else {
                Messages.sendMessage(player, "&c" + result);
            }
        }).exceptionally(ex -> {
            Messages.sendMessage(player, "&An error occurred: " + ex.getMessage());
            ex.printStackTrace();
            return null;
        });

    }

}
