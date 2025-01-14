package me.advait.mai.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class Messages {

    public static void sendMessage(CommandSender sender, String message) {
        Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(message);
        sender.sendMessage(component);
    }

    public static void debugMessage(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendActionBar(Component.text(message).color(NamedTextColor.YELLOW));
        }
    }

}
