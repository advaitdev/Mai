package me.advait.mai.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.advait.mai.GeminiAgent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class ChatListener implements Listener {

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        String chatMessage = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (chatMessage.startsWith("!")) {
            Component aiResponse = PlainTextComponentSerializer.plainText().deserialize(GeminiAgent.getInstance().askAndReceive(chatMessage));

            Bukkit.broadcast(Component.text("[USER] " + event.getPlayer().getName() + ": ").append(event.message()));
            Bukkit.broadcast(Component.text("[AI] ").append(aiResponse));
            event.setCancelled(true);
        }

    }

}
