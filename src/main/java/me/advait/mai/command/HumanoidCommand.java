package me.advait.mai.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import me.advait.mai.Catalog;
import me.advait.mai.body.Humanoid;
import me.advait.mai.file.serialization.LocationSerializer;
import me.advait.mai.gui.HumanoidInfoGUI;
import me.advait.mai.gui.HumanoidListGUI;
import me.advait.mai.util.Messages;
import org.bukkit.Location;
import org.bukkit.entity.Player;

@CommandAlias("humanoid|h|npc")
@Description("Manage humanoid NPCs")
public class HumanoidCommand extends BaseCommand {

    @Subcommand("spawn")
    @CommandPermission("mai.humanoid.spawn")
    @CommandCompletion("@nothing")
    @Description("Spawn a new humanoid at your location")
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
    @Description("Spawn a new humanoid at specific coordinates")
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
    @Description("Open the humanoid management GUI")
    public void onList(Player player) {
        new HumanoidListGUI(player).open();
    }

    @Subcommand("tp|teleport")
    @CommandPermission("mai.humanoid.teleport")
    @CommandCompletion("@humanoids")
    @Description("Teleport to a humanoid")
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
    @Description("Delete a humanoid")
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
    @Description("View information about a humanoid")
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
    @Description("Force save all humanoids to file")
    public void onSave(Player player) {
        Catalog.getInstance().saveAll();
        Messages.sendMessage(player, "&aSaved " + Catalog.getInstance().getCount() + " Humanoid(s) to file.");
    }

    @Subcommand("rename")
    @CommandPermission("mai.humanoid.rename")
    @CommandCompletion("@humanoids @nothing")
    @Description("Rename a humanoid")
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

    @Default
    @CatchUnknown
    public void onDefault(Player player) {
        Messages.sendMessage(player, "&6=== Humanoid Commands ===");
        Messages.sendMessage(player, "&e/humanoid spawn <name> &7- Spawn a Humanoid at your location");
        Messages.sendMessage(player, "&e/humanoid spawnat <name> <x> <y> <z> &7- Spawn at coordinates");
        Messages.sendMessage(player, "&e/humanoid list &7- Open management GUI");
        Messages.sendMessage(player, "&e/humanoid tp <name> &7- Teleport to a Humanoid");
        Messages.sendMessage(player, "&e/humanoid info <name> &7- View Humanoid info");
        Messages.sendMessage(player, "&e/humanoid delete <name> &7- Delete a Humanoid");
        Messages.sendMessage(player, "&e/humanoid rename <old> <new> &7- Rename a Humanoid");
        Messages.sendMessage(player, "&e/humanoid save &7- Force save all to file");
    }
}
