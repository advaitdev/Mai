package me.advait.mai.command.debug;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import me.advait.mai.Catalog;
import me.advait.mai.body.Humanoid;
import me.advait.mai.brain.action.HumanoidActionAgent;
import me.advait.mai.brain.action.mechanic.building.HumanoidBuildAction;
import me.advait.mai.brain.action.mechanic.building.HumanoidMineAction;
import me.advait.mai.brain.action.mechanic.movement.HumanoidWalkToAction;
import me.advait.mai.npc.HumanoidUtil;
import me.advait.mai.pathetic.PatheticAgent;
import me.advait.mai.util.Messages;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@CommandAlias("hdebug")
public class HDebugCommand extends BaseCommand {

    private static Humanoid getSelectedHumanoid(Player player) {
        var list = Catalog.getInstance().getAllHumanoids();
        return list.isEmpty() ? null : list.get(0);
    }

    @CommandAlias("jump")
    public void runJump(Player player) {
        Humanoid humanoid = getSelectedHumanoid(player);
        if (humanoid == null || humanoid.getEntity() == null) {
            Messages.sendMessage(player, "&cYou have no humanoid / mannequin!");
            return;
        }
        HumanoidUtil.jump(humanoid.getEntity(), 0.5);
    }

    @CommandAlias("pile")
    public void runPile(Player player) {
        Humanoid humanoid = getSelectedHumanoid(player);
        if (humanoid == null || humanoid.getEntity() == null) {
            Messages.sendMessage(player, "&cYou have no humanoid / mannequin!");
            return;
        }
        boolean didPile = HumanoidUtil.pileUp(humanoid.getEntity());
        if (!didPile) Messages.sendMessage(player, "&cHumanoid could not place the block below!");
    }

    @CommandAlias("dig")
    public void runDig(Player player) {
        Humanoid humanoid = getSelectedHumanoid(player);
        if (humanoid == null || humanoid.getEntity() == null) {
            Messages.sendMessage(player, "&cYou have no humanoid / mannequin!");
            return;
        }
        HumanoidUtil.mineBlock(humanoid.getEntity(), humanoid.getEntity().getLocation().clone().subtract(0, 1, 0), new ItemStack(Material.DIAMOND_PICKAXE));
    }

    @CommandAlias("mine")
    public void runMine(Player player) {
        Humanoid humanoid = getSelectedHumanoid(player);
        if (humanoid == null) {
            Messages.sendMessage(player, "&cNo humanoid exists in this world!");
            return;
        }

        Block inFront = player.getTargetBlockExact(3);

        var mineAction = new HumanoidMineAction(humanoid, inFront, true);
        var mineActionResult = mineAction.run();

        mineActionResult.thenAccept(result -> {
            if (result.isSuccess()) {
                Messages.sendMessage(player, "&a" + result);
            } else {
                Messages.sendMessage(player, "&c" + result);
            }
        }).exceptionally(ex -> {
            Messages.sendMessage(player, "&cAn error occurred: " + ex.getMessage());
            ex.printStackTrace();
            return null;
        });
    }

    @CommandAlias("gotoandmine")
    public void runGotoAndMine(Player player) {
        Humanoid humanoid = getSelectedHumanoid(player);
        if (humanoid == null) {
            Messages.sendMessage(player, "&cNo humanoid exists in this world!");
            return;
        }

        var walkToAction = new HumanoidWalkToAction(humanoid, player.getLocation());
        var mineAction = new HumanoidMineAction(humanoid, player.getTargetBlockExact(3), true);

        HumanoidActionAgent.getInstance().addActions(walkToAction, mineAction).thenAccept(result -> {
                    if (result.isSuccess()) {
                        Messages.sendMessage(player, "&agoToAndMine completed successfully!");
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

    @CommandAlias("gotoandbuild")
    public void runGotoAndBuild(Player player) {
        Humanoid humanoid = getSelectedHumanoid(player);
        if (humanoid == null) {
            Messages.sendMessage(player, "&cNo humanoid exists in this world!");
            return;
        }

        var walkToAction = new HumanoidWalkToAction(humanoid, player.getLocation());

        if (player.getTargetBlockExact(3) == null) {
            Messages.sendMessage(player, "&cYou are too far away from any targetable block; get closer to something.");
            return;
        }

        ItemStack inHand = humanoid.getEquipment() != null ? humanoid.getEquipment().getItemInMainHand() : null;
        var buildAction = new HumanoidBuildAction(humanoid, player.getTargetBlockExact(3).getLocation(), inHand);

        HumanoidActionAgent.getInstance().addActions(walkToAction, buildAction).thenAccept(result -> {
                    if (result.isSuccess()) {
                        Messages.sendMessage(player, "&agoToAndBuild completed successfully!");
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
        Humanoid humanoid = getSelectedHumanoid(player);
        if (humanoid == null) {
            Messages.sendMessage(player, "&cNo humanoid exists in this world!");
            return;
        }

        Block inFront = player.getTargetBlockExact(3);

        var mineAction = new HumanoidMineAction(humanoid, inFront, false);
        var mineActionResult = mineAction.run();

        mineActionResult.thenAccept(result -> {
            if (result.isSuccess()) {
                Messages.sendMessage(player, "&a" + result + " - Block: " + inFront);
            } else {
                Messages.sendMessage(player, "&c" + result + " - Block: " + inFront);
            }
        }).exceptionally(ex -> {
            Messages.sendMessage(player, "&cAn error occurred: " + ex.getMessage());
            ex.printStackTrace();
            return null;
        });
    }

    @CommandAlias("bridge")
    public void runBridge(Player player) {
        Humanoid humanoid = getSelectedHumanoid(player);
        if (humanoid == null || humanoid.getEntity() == null) {
            Messages.sendMessage(player, "&cYou have no humanoid / mannequin!");
            return;
        }
        boolean didBridge = HumanoidUtil.bridgeTowardsTarget(humanoid.getEntity(), player.getLocation(), Material.DIAMOND_BLOCK);
        if (!didBridge) {
            Messages.sendMessage(player, "&cHumanoid could not bridge to your location!");
        }
    }

    @CommandAlias("getblockinfrontatfootlevel")
    public void runGetBlockInFrontAtFootLevel(Player player) {
        Messages.sendMessage(player, "&aBlock in front at foot level: " + HumanoidUtil.getBlockInFrontAtFootLevel(player).toString());
    }

    @CommandAlias("ispathpossible")
    public void runIsPathPossible(Player player) {
        Humanoid humanoid = getSelectedHumanoid(player);
        if (humanoid == null || humanoid.getEntity() == null) {
            Messages.sendMessage(player, "&cYou have no humanoid / mannequin!");
            return;
        }
        long startTime = System.currentTimeMillis();
        PatheticAgent.getInstance().getGroundPath(humanoid.getEntity().getLocation(), player.getLocation())
                .thenAccept(result -> {
                    long elapsed = System.currentTimeMillis() - startTime;
                    Messages.sendMessage(player, "&aResult for path: " + result.successful() + " (" + elapsed + " ms)");
                })
                .exceptionally(ex -> {
                    Messages.sendMessage(player, "&cPath check failed: " + ex.getMessage());
                    return null;
                });
    }

    @CommandAlias("gotome")
    public void runGoToMe(Player player) {
        Humanoid humanoid = getSelectedHumanoid(player);
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
            Messages.sendMessage(player, "&cAn error occurred: " + ex.getMessage());
            ex.printStackTrace();
            return null;
        });
    }

}
