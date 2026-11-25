package me.advait.mai.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Mai's chat style, kept vanilla: gold headers, yellow names and commands,
 * white text, green success, red errors. No gray: it reads badly in chat.
 */
public final class Messages {

    public static final TextColor HEADER = NamedTextColor.GOLD;
    public static final TextColor HIGHLIGHT = NamedTextColor.YELLOW;
    public static final TextColor TEXT = NamedTextColor.WHITE;
    public static final TextColor SUCCESS = NamedTextColor.GREEN;
    public static final TextColor ERROR = NamedTextColor.RED;
    public static final TextColor INFO = NamedTextColor.AQUA;

    private Messages() {}

    /** A legacy {@code &}-coded message. */
    public static void sendMessage(CommandSender sender, String message) {
        sender.sendMessage(legacy(message));
    }

    /** {@code --- Title ---}, with an optional note after it. */
    public static void header(CommandSender sender, String title, String note) {
        Component line = Component.text("--- " + title + " ---", HEADER);
        if (note != null) line = line.append(Component.text(" " + note, TEXT));
        sender.sendMessage(line);
    }

    public static void header(CommandSender sender, String title) {
        header(sender, title, null);
    }

    /** One help entry, like Bukkit's help: {@code /usage: description}. Clicking fills the chat box. */
    public static void help(CommandSender sender, String usage, String description) {
        // The fixed words, up to the first placeholder or alternative list.
        String base = usage.replaceAll("\\s+\\S*[<\\[{|].*$", "");
        sender.sendMessage(Component.text(usage, HIGHLIGHT)
                .append(Component.text(": " + description, TEXT))
                .hoverEvent(HoverEvent.showText(Component.text("Click to type " + base, TEXT)))
                .clickEvent(ClickEvent.suggestCommand(base + " ")));
    }

    /** {@code Label: value}. */
    public static void field(CommandSender sender, String label, Component value) {
        sender.sendMessage(Component.text(label + ": ", HEADER).append(value));
    }

    /** Duration as {@code 9.4s} or {@code 9m 22s}. */
    public static String duration(long ms) {
        if (ms < 60_000) return String.format("%.1fs", ms / 1000.0);
        return (ms / 60_000) + "m " + (ms / 1000 % 60) + "s";
    }

    public static Component legacy(String message) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(message);
    }

    public static void debugMessage(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendActionBar(Component.text(message).color(HIGHLIGHT));
        }
    }
}
