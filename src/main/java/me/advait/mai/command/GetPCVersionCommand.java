package me.advait.mai.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import me.advait.mai.Mai;
import me.advait.mai.util.Messages;
import org.bukkit.entity.Player;

public class GetPCVersionCommand extends BaseCommand {

    @CommandAlias("getpcversion")
    public void run(Player player) {
        Messages.sendMessage(player, "&a" + Mai.getInstance().getDescription().getVersion());
    }

}
