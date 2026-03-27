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
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

@CommandAlias("humanoid|h|npc")
@Description("Manage Humanoid NPCs")
public class HumanoidCommand extends BaseCommand {

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
        new HumanoidWalkToAction(humanoid, player.getLocation()).run()
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
        new HumanoidMineAction(humanoid, target, true).run()
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
        new HumanoidBuildAction(humanoid, target.getLocation(), inHand).run()
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
        PatheticAgent.getInstance().getGroundPath(humanoid.getEntity().getLocation(), player.getLocation())
                .thenAccept(result -> {
                    long elapsed = System.currentTimeMillis() - startTime;
                    if (result.successful()) {
                        Messages.sendMessage(player, "&aPath found! &7(" + result.getPath().length() + " waypoints, " + elapsed + "ms)");
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
