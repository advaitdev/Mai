package me.advait.mai.command.debug;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import me.advait.mai.Mai;
import me.advait.mai.util.Messages;
import org.bukkit.entity.Player;

public class GetGitVersionCommand extends BaseCommand {

    @CommandAlias("getgitversion")
    public void run(Player player) {
        Messages.sendMessage(player, "&a" + Mai.getInstance().getDescription().getVersion());
    }

}
