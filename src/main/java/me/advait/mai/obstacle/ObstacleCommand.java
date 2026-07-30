package me.advait.mai.obstacle;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.HelpCommand;
import co.aikar.commands.annotation.Subcommand;
import co.aikar.commands.annotation.Syntax;
import me.advait.mai.util.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /mai test ...} — the obstacle-course test harness command. Registered
 * as a top-level command (separate from {@code /h}) because every handler here
 * accepts {@link CommandSender}, so the server console can drive runs headlessly
 * without a Player handle. Only {@code tp} requires an in-game player.
 */
@CommandAlias("mai")
@Description("Mai obstacle-course test harness")
public class ObstacleCommand extends BaseCommand {

    @Subcommand("test run")
    @CommandPermission("mai.obstacle.run")
    @CommandCompletion("@obstacles")
    @Syntax("<obstacle>")
    @Description("Run a single obstacle test")
    public void onRun(CommandSender sender, String obstacle) {
        ObstacleTester.getInstance().runSingle(sender, obstacle);
    }

    @Subcommand("test all")
    @CommandPermission("mai.obstacle.run")
    @Description("Run every obstacle sequentially")
    public void onAll(CommandSender sender) {
        ObstacleTester.getInstance().runAll(sender);
    }

    @Subcommand("test list")
    @CommandPermission("mai.obstacle.run")
    @Description("List available obstacles")
    public void onList(CommandSender sender) {
        ObstacleTester.getInstance().list(sender);
    }

    @Subcommand("test cancel")
    @CommandPermission("mai.obstacle.run")
    @Description("Cancel the in-flight test")
    public void onCancel(CommandSender sender) {
        ObstacleTester.getInstance().cancel(sender);
    }

    @Subcommand("test reload")
    @CommandPermission("mai.obstacle.run")
    @Description("Reload obstacle files from disk")
    public void onReload(CommandSender sender) {
        ObstacleTester.getInstance().reload(sender);
    }

    @Subcommand("test tp")
    @CommandPermission("mai.obstacle.run")
    @CommandCompletion("@obstacles")
    @Syntax("<obstacle>")
    @Description("Build an arena and teleport to it (in-game only)")
    public void onTp(CommandSender sender, String obstacle) {
        if (!(sender instanceof Player player)) {
            Messages.sendMessage(sender, "&cThis subcommand can only be run in-game.");
            return;
        }
        ObstacleTester.getInstance().teleport(sender, obstacle, player);
    }

    @HelpCommand
    @Default
    @Subcommand("test")
    public void onHelp(CommandSender sender) {
        Messages.sendMessage(sender, "&6=== Mai Obstacle Tests ===");
        Messages.sendMessage(sender, "&e/mai test run <obstacle> &7- Run one test");
        Messages.sendMessage(sender, "&e/mai test all &7- Run all tests");
        Messages.sendMessage(sender, "&e/mai test list &7- List obstacles");
        Messages.sendMessage(sender, "&e/mai test cancel &7- Cancel running test");
        Messages.sendMessage(sender, "&e/mai test reload &7- Reload files from disk");
        Messages.sendMessage(sender, "&e/mai test tp <obstacle> &7- Build + teleport (in-game)");
    }
}
