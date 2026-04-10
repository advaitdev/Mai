package me.advait.mai.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import me.advait.mai.Catalog;
import me.advait.mai.body.Humanoid;
import me.advait.mai.brain.action.mechanic.building.HumanoidBuildAction;
import me.advait.mai.brain.action.mechanic.building.HumanoidMineAction;
import me.advait.mai.brain.action.mechanic.movement.HumanoidWalkToAction;
import me.advait.mai.brain.action.mechanic.movement.runnable.HumanoidWalkToRunnable;
import me.advait.mai.file.serialization.LocationSerializer;
import me.advait.mai.gui.HumanoidInfoGUI;
import me.advait.mai.gui.HumanoidListGUI;
import me.advait.mai.pathetic.PatheticAgent;
import me.advait.mai.util.Messages;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import me.advait.mai.Mai;
import me.advait.mai.brain.action.HumanoidActionAgent;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@CommandAlias("humanoid|h|npc")
@Description("Manage Humanoid NPCs")
public class HumanoidCommand extends BaseCommand {

    // Live action bar task per player
    private static final Map<UUID, BukkitTask> queueWatchers = new HashMap<>();

    // ===== MANAGEMENT COMMANDS =====

    @Subcommand("spawn")
    @CommandPermission("mai.humanoid.spawn")
    @CommandCompletion("@nothing")
    @Syntax("<name>")
    @Description("Spawn a new Humanoid at your location")
    public void onSpawn(Player player, String name) {
        if (Catalog.getInstance().nameExists(name)) {
            Messages.sendMessage(player, "&cA Humanoid with that name already exists!");
            return;
        }

        Humanoid humanoid = Catalog.getInstance().register(name, player.getLocation());
        Messages.sendMessage(player, "&aSpawned Humanoid '&e" + name + "&a' at your location.");
        Messages.sendMessage(player, "&7UUID: " + humanoid.getUuid().toString());
    }

    @Subcommand("spawnat")
    @CommandPermission("mai.humanoid.spawn")
    @CommandCompletion("@nothing @nothing @nothing @nothing")
    @Syntax("<name> <x> <y> <z>")
    @Description("Spawn a new Humanoid at specific coordinates")
    public void onSpawnAt(Player player, String name, double x, double y, double z) {
        if (Catalog.getInstance().nameExists(name)) {
            Messages.sendMessage(player, "&cA Humanoid with that name already exists!");
            return;
        }

        Location location = new Location(player.getWorld(), x, y, z);
        Humanoid humanoid = Catalog.getInstance().register(name, location);
        Messages.sendMessage(player, "&aSpawned Humanoid '&e" + name + "&a' at " + LocationSerializer.toReadableString(location));
    }

    @Subcommand("list")
    @CommandPermission("mai.humanoid.list")
    @Description("Open the Humanoid management GUI")
    public void onList(Player player) {
        new HumanoidListGUI(player).open();
    }

    @Subcommand("tp|teleport")
    @CommandPermission("mai.humanoid.teleport")
    @CommandCompletion("@humanoids")
    @Syntax("<name>")
    @Description("Teleport to a Humanoid")
    public void onTeleport(Player player, String name) {
        Humanoid humanoid = Catalog.getInstance().getByName(name);
        if (humanoid == null) {
            Messages.sendMessage(player, "&cNo Humanoid found with name '&e" + name + "&c'");
            return;
        }
        if (humanoid.getMannequin() == null) {
            Messages.sendMessage(player, "&cThat Humanoid is not currently spawned!");
            return;
        }

        player.teleport(humanoid.getMannequin().getLocation());
        Messages.sendMessage(player, "&aTeleported to Humanoid '&e" + name + "&a'");
    }

    @Subcommand("delete|remove")
    @CommandPermission("mai.humanoid.delete")
    @CommandCompletion("@humanoids")
    @Syntax("<name>")
    @Description("Delete a Humanoid")
    public void onDelete(Player player, String name) {
        Humanoid humanoid = Catalog.getInstance().getByName(name);
        if (humanoid == null) {
            Messages.sendMessage(player, "&cNo Humanoid found with name '&e" + name + "&c'");
            return;
        }

        Catalog.getInstance().unregister(humanoid);
        Messages.sendMessage(player, "&aDeleted Humanoid '&e" + name + "&a'");
    }

    @Subcommand("info")
    @CommandPermission("mai.humanoid.info")
    @CommandCompletion("@humanoids")
    @Syntax("<name>")
    @Description("View information about a Humanoid")
    public void onInfo(Player player, String name) {
        Humanoid humanoid = Catalog.getInstance().getByName(name);
        if (humanoid == null) {
            Messages.sendMessage(player, "&cNo Humanoid found with name '&e" + name + "&c'");
            return;
        }

        new HumanoidInfoGUI(player, humanoid).open();
    }

    @Subcommand("save")
    @CommandPermission("mai.humanoid.admin")
    @Description("Force save all Humanoids to file")
    public void onSave(Player player) {
        Catalog.getInstance().saveAll();
        Messages.sendMessage(player, "&aSaved " + Catalog.getInstance().getCount() + " Humanoid(s) to file.");
    }

    @Subcommand("rename")
    @CommandPermission("mai.humanoid.rename")
    @CommandCompletion("@humanoids @nothing")
    @Syntax("<oldName> <newName>")
    @Description("Rename a Humanoid")
    public void onRename(Player player, String oldName, String newName) {
        Humanoid humanoid = Catalog.getInstance().getByName(oldName);
        if (humanoid == null) {
            Messages.sendMessage(player, "&cNo Humanoid found with name '&e" + oldName + "&c'");
            return;
        }
        if (Catalog.getInstance().nameExists(newName)) {
            Messages.sendMessage(player, "&cA Humanoid with name '&e" + newName + "&c' already exists!");
            return;
        }

        humanoid.setName(newName);
        Catalog.getInstance().save(humanoid);
        Messages.sendMessage(player, "&aRenamed Humanoid from '&e" + oldName + "&a' to '&e" + newName + "&a'");
    }

    // ===== DEBUG COMMANDS =====

    private Humanoid resolveHumanoid(Player player, String name) {
        if (name != null) return Catalog.getInstance().getByName(name);
        var list = Catalog.getInstance().getAllHumanoids();
        if (list.isEmpty()) {
            Messages.sendMessage(player, "&cNo Humanoids exist!");
            return null;
        }
        return list.getFirst();
    }

    @Subcommand("debug particles")
    @CommandPermission("mai.humanoid.debug")
    @Description("Toggle path debug particles")
    public void onDebugParticles(Player player) {
        boolean current = HumanoidWalkToRunnable.isDebugMode();
        HumanoidWalkToRunnable.setDebugMode(!current);
        Messages.sendMessage(player, "&7Path debug particles: " + (!current ? "&aEnabled" : "&cDisabled"));
    }

    @Subcommand("debug gotome")
    @CommandPermission("mai.humanoid.debug")
    @CommandCompletion("@humanoids")
    @Syntax("[name]")
    @Description("Make a Humanoid walk to your location")
    public void onDebugGotoMe(Player player, @Optional String name) {
        Humanoid humanoid = resolveHumanoid(player, name);
        if (humanoid == null || humanoid.getEntity() == null) {
            Messages.sendMessage(player, "&cNo valid Humanoid found!");
            return;
        }

        Messages.sendMessage(player, "&7Sending '&e" + humanoid.getName() + "&7' to your location...");
        humanoid.getActionAgent().addActions(new HumanoidWalkToAction(humanoid, player.getLocation()))
                .thenAccept(result -> Messages.sendMessage(player,
                        result.success() ? "&a" + humanoid.getName() + " arrived!" : "&c" + result.message()))
                .exceptionally(ex -> {
                    Messages.sendMessage(player, "&cError: " + ex.getMessage());
                    return null;
                });
    }

    @Subcommand("debug jump")
    @CommandPermission("mai.humanoid.debug")
    @CommandCompletion("@humanoids")
    @Syntax("[name]")
    @Description("Make a Humanoid jump")
    public void onDebugJump(Player player, @Optional String name) {
        Humanoid humanoid = resolveHumanoid(player, name);
        if (humanoid == null || humanoid.getEntity() == null) {
            Messages.sendMessage(player, "&cNo valid Humanoid found!");
            return;
        }

        Vector v = humanoid.getEntity().getVelocity();
        humanoid.getEntity().setVelocity(new Vector(v.getX(), 0.42, v.getZ()));
        Messages.sendMessage(player, "&a" + humanoid.getName() + " jumped!");
    }

    @Subcommand("debug mine")
    @CommandPermission("mai.humanoid.debug")
    @CommandCompletion("@humanoids")
    @Syntax("[name]")
    @Description("Make a Humanoid mine the block you're looking at")
    public void onDebugMine(Player player, @Optional String name) {
        Humanoid humanoid = resolveHumanoid(player, name);
        if (humanoid == null) {
            Messages.sendMessage(player, "&cNo valid Humanoid found!");
            return;
        }

        Block target = player.getTargetBlockExact(5);
        if (target == null || target.getType().isAir()) {
            Messages.sendMessage(player, "&cLook at a block to mine!");
            return;
        }

        Messages.sendMessage(player, "&7Mining " + target.getType() + "...");
        humanoid.getActionAgent().addActions(new HumanoidMineAction(humanoid, target, true))
                .thenAccept(result -> Messages.sendMessage(player,
                        result.success() ? "&aBlock mined!" : "&c" + result.message()))
                .exceptionally(ex -> {
                    Messages.sendMessage(player, "&cError: " + ex.getMessage());
                    return null;
                });
    }

    @Subcommand("debug build")
    @CommandPermission("mai.humanoid.debug")
    @CommandCompletion("@humanoids")
    @Syntax("[name]")
    @Description("Make a Humanoid place a block where you're looking")
    public void onDebugBuild(Player player, @Optional String name) {
        Humanoid humanoid = resolveHumanoid(player, name);
        if (humanoid == null) {
            Messages.sendMessage(player, "&cNo valid Humanoid found!");
            return;
        }

        Block target = player.getTargetBlockExact(5);
        if (target == null) {
            Messages.sendMessage(player, "&cLook at a location to build!");
            return;
        }

        ItemStack inHand = humanoid.getEquipment() != null ? humanoid.getEquipment().getItemInMainHand() : null;
        if (inHand == null || !inHand.getType().isBlock()) {
            Messages.sendMessage(player, "&cHumanoid needs a block in main hand!");
            return;
        }

        Messages.sendMessage(player, "&7Placing " + inHand.getType() + "...");
        humanoid.getActionAgent().addActions(new HumanoidBuildAction(humanoid, target.getLocation(), inHand))
                .thenAccept(result -> Messages.sendMessage(player,
                        result.success() ? "&aBlock placed!" : "&c" + result.message()))
                .exceptionally(ex -> {
                    Messages.sendMessage(player, "&cError: " + ex.getMessage());
                    return null;
                });
    }

    @Subcommand("debug pathcheck")
    @CommandPermission("mai.humanoid.debug")
    @CommandCompletion("@humanoids")
    @Syntax("[name]")
    @Description("Check if pathfinding to your location is possible")
    public void onDebugPathCheck(Player player, @Optional String name) {
        Humanoid humanoid = resolveHumanoid(player, name);
        if (humanoid == null || humanoid.getEntity() == null) {
            Messages.sendMessage(player, "&cNo valid Humanoid found!");
            return;
        }

        long startTime = System.currentTimeMillis();
        PatheticAgent.getInstance().getAnnotatedPath(humanoid, humanoid.getEntity().getLocation(), player.getLocation())
                .thenAccept(pathOpt -> {
                    long elapsed = System.currentTimeMillis() - startTime;
                    if (pathOpt.isPresent()) {
                        Messages.sendMessage(player, "&aPath found! &7(" + pathOpt.get().size() + " waypoints, " + elapsed + "ms)");
                    } else {
                        Messages.sendMessage(player, "&cNo path found! &7(" + elapsed + "ms)");
                    }
                })
                .exceptionally(ex -> {
                    Messages.sendMessage(player, "&cPathfinding error: " + ex.getMessage());
                    return null;
                });
    }

    @Subcommand("debug gotoandmine")
    @CommandPermission("mai.humanoid.debug")
    @CommandCompletion("@humanoids")
    @Syntax("[name]")
    @Description("Make a Humanoid walk to you and mine the block you're looking at")
    public void onDebugGotoAndMine(Player player, @Optional String name) {
        Humanoid humanoid = resolveHumanoid(player, name);
        if (humanoid == null) {
            Messages.sendMessage(player, "&cNo valid Humanoid found!");
            return;
        }

        Block target = player.getTargetBlockExact(5);
        if (target == null || target.getType().isAir()) {
            Messages.sendMessage(player, "&cLook at a block to mine!");
            return;
        }

        var walkAction = new HumanoidWalkToAction(humanoid, player.getLocation());
        var mineAction = new HumanoidMineAction(humanoid, target, true);

        Messages.sendMessage(player, "&7Walking to you, then mining " + target.getType() + "...");
        humanoid.getActionAgent().addActions(walkAction, mineAction)
                .thenAccept(result -> Messages.sendMessage(player,
                        result.success() ? "&aAction chain completed!" : "&c" + result.message()))
                .exceptionally(ex -> {
                    Messages.sendMessage(player, "&cError: " + ex.getMessage());
                    return null;
                });
    }

    @Subcommand("debug queue")
    @CommandPermission("mai.humanoid.debug")
    @CommandCompletion("@humanoids")
    @Syntax("[name]")
    @Description("Show current action queue for a Humanoid")
    public void onDebugQueue(Player player, @Optional String name) {
        Humanoid humanoid = resolveHumanoid(player, name);
        if (humanoid == null) return;

        HumanoidActionAgent agent = humanoid.getActionAgent();
        String currentAction = agent.getCurrentActionName();
        List<String> queued = agent.getQueuedActionNames();

        Messages.sendMessage(player, "&6=== " + humanoid.getName() + " Action Queue ===");
        if (currentAction != null) {
            Messages.sendMessage(player, "&a> Running: &f" + currentAction);
        } else {
            Messages.sendMessage(player, "&7> Idle");
        }
        if (queued.isEmpty()) {
            Messages.sendMessage(player, "&7  (no queued actions)");
        } else {
            for (int i = 0; i < queued.size(); i++) {
                Messages.sendMessage(player, "&e  " + (i + 1) + ". " + queued.get(i));
            }
        }
    }

    @Subcommand("debug cancel")
    @CommandPermission("mai.humanoid.debug")
    @CommandCompletion("@humanoids")
    @Syntax("[name]")
    @Description("Cancel all actions for a Humanoid")
    public void onDebugCancel(Player player, @Optional String name) {
        Humanoid humanoid = resolveHumanoid(player, name);
        if (humanoid == null) return;

        humanoid.getActionAgent().cancelAll();
        Messages.sendMessage(player, "&aCancelled all actions for '&e" + humanoid.getName() + "&a'");
    }

    @Subcommand("debug watch")
    @CommandPermission("mai.humanoid.debug")
    @CommandCompletion("@humanoids")
    @Syntax("[name]")
    @Description("Toggle live action bar showing queue state")
    public void onDebugWatch(Player player, @Optional String name) {
        UUID playerId = player.getUniqueId();

        // Toggle off if already watching
        if (queueWatchers.containsKey(playerId)) {
            queueWatchers.remove(playerId).cancel();
            Messages.sendMessage(player, "&7Queue watcher &cdisabled");
            player.sendActionBar(Component.empty());
            return;
        }

        Humanoid humanoid = resolveHumanoid(player, name);
        if (humanoid == null) return;

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(Mai.getInstance(), () -> {
            Player p = Bukkit.getPlayer(playerId);
            if (p == null || !p.isOnline()) {
                BukkitTask t = queueWatchers.remove(playerId);
                if (t != null) t.cancel();
                return;
            }

            HumanoidActionAgent agent = humanoid.getActionAgent();
            String current = agent.getCurrentActionName();
            int queueSize = agent.getQueueSize();

            Component bar;
            if (current != null) {
                bar = Component.text(humanoid.getName() + " ", NamedTextColor.GOLD)
                        .append(Component.text(current, NamedTextColor.GREEN, TextDecoration.BOLD))
                        .append(Component.text(" [" + queueSize + " in queue]", NamedTextColor.GRAY));
            } else {
                bar = Component.text(humanoid.getName() + " ", NamedTextColor.GOLD)
                        .append(Component.text("Idle", NamedTextColor.GRAY));
            }
            p.sendActionBar(bar);
        }, 0L, 5L);

        queueWatchers.put(playerId, task);
        Messages.sendMessage(player, "&7Queue watcher &aenabled &7for '&e" + humanoid.getName() + "&7'");
    }

    // ===== HELP =====

    @Subcommand("debug")
    @CommandPermission("mai.humanoid.debug")
    public void onDebugHelp(Player player) {
        Messages.sendMessage(player, "&6=== Debug Commands ===");
        Messages.sendMessage(player, "&e/h debug particles &7- Toggle path particles");
        Messages.sendMessage(player, "&e/h debug gotome [name] &7- Walk to you");
        Messages.sendMessage(player, "&e/h debug jump [name] &7- Make jump");
        Messages.sendMessage(player, "&e/h debug mine [name] &7- Mine target block");
        Messages.sendMessage(player, "&e/h debug build [name] &7- Place block");
        Messages.sendMessage(player, "&e/h debug pathcheck [name] &7- Check path");
        Messages.sendMessage(player, "&e/h debug gotoandmine [name] &7- Walk + mine");
        Messages.sendMessage(player, "&e/h debug queue [name] &7- Show action queue");
        Messages.sendMessage(player, "&e/h debug cancel [name] &7- Cancel all actions");
        Messages.sendMessage(player, "&e/h debug watch [name] &7- Toggle live queue bar");
    }

    @HelpCommand
    @Default
    @CatchUnknown
    public void onHelp(Player player) {
        Messages.sendMessage(player, "&6=== Humanoid Commands ===");
        Messages.sendMessage(player, "&e/h spawn <name> &7- Spawn at your location");
        Messages.sendMessage(player, "&e/h spawnat <name> <x> <y> <z> &7- Spawn at coords");
        Messages.sendMessage(player, "&e/h list &7- Open management GUI");
        Messages.sendMessage(player, "&e/h tp <name> &7- Teleport to Humanoid");
        Messages.sendMessage(player, "&e/h info <name> &7- View info");
        Messages.sendMessage(player, "&e/h delete <name> &7- Delete");
        Messages.sendMessage(player, "&e/h rename <old> <new> &7- Rename");
        Messages.sendMessage(player, "&e/h save &7- Force save");
        Messages.sendMessage(player, "&e/h debug &7- Debug commands");
    }
}
