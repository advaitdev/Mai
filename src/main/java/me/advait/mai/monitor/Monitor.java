package me.advait.mai.monitor;

import me.advait.mai.Mai;
import me.advait.mai.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class Monitor {

    public static void log(String message) {
        Mai.log().info("&6\uD83D\uDD27" + message);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getEquipment().getItemInMainHand().getType() == Material.KNOWLEDGE_BOOK
                || player.getEquipment().getItemInOffHand().getType() == Material.KNOWLEDGE_BOOK) {
                Messages.sendMessage(player, "&6\uD83D\uDD27" + message);
            }
        }
    }

    public static void logError(String message) {
        Mai.log().severe("&6\uD83D\uDD27" + message);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getEquipment().getItemInMainHand().getType() == Material.KNOWLEDGE_BOOK
                    || player.getEquipment().getItemInOffHand().getType() == Material.KNOWLEDGE_BOOK) {
                Messages.sendMessage(player, "&c\uD83D\uDD27" + message);
            }
        }
    }

}
