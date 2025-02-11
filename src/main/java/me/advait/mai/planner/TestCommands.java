package me.advait.mai.planner;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TestCommands implements CommandExecutor {
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
        CraftingPlanner craftingPlanner = new CraftingPlanner((Player)sender);
        String test = craftingPlanner.craftItem(args[0], Integer.parseInt(args[1]));
        sender.sendMessage(test);
        return true;
    }
}
