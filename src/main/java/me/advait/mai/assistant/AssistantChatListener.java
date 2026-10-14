package me.advait.mai.assistant;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.advait.mai.Catalog;
import me.advait.mai.Mai;
import me.advait.mai.body.Humanoid;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Lets players talk to humanoids by name in normal chat: "Mai, follow me",
 * "@Mai mine this", "mai: attack nearest". The message still shows in chat;
 * the humanoid answers as itself. Parsing is {@link LlmBrain} when enabled,
 * else (or if the API fails) the {@link ChatIntents} keyword rules; either
 * way execution is {@link Tools}, the same path commands use.
 */
public final class AssistantChatListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(Mai.getInstance(), () -> handle(player, text));
    }

    private void handle(Player player, String text) {
        if (!player.hasPermission("mai.humanoid.command")) return;
        String lower = text.toLowerCase(Locale.ROOT);
        String body = lower.startsWith("@") ? text.substring(1) : text;
        Humanoid target = null;
        String instruction = null;
        for (Humanoid h : Catalog.getInstance().getAllHumanoids()) {
            String n = h.getName();
            if (body.length() < n.length() || !body.regionMatches(true, 0, n, 0, n.length())) continue;
            String after = body.substring(n.length());
            if (!after.isEmpty() && !after.matches("^[\\s,:!.-].*")) continue;   // "Maicraft" isn't "Mai"
            target = h;
            instruction = after.replaceFirst("^[\\s,:!.-]+", "").trim();
            break;
        }
        if (target == null) return;

        Assistant assistant = Assistant.of(target);
        if (instruction.isEmpty() || instruction.equalsIgnoreCase("help")) {
            assistant.say("Hi " + player.getName() + "! " + ChatIntents.HELP);
            return;
        }
        if (LlmBrain.available()) {
            Humanoid h = target;
            String said = instruction;
            LlmBrain.handle(h, player, said, () -> runKeywordIntent(h, player, said));
            return;
        }
        runKeywordIntent(target, player, instruction);
    }

    private void runKeywordIntent(Humanoid target, Player player, String instruction) {
        Assistant assistant = Assistant.of(target);
        ChatIntents.Intent intent = ChatIntents.parse(instruction, player);
        if (intent == null) {
            assistant.say("Sorry, I don't know how to do that yet. " + ChatIntents.HELP);
            return;
        }
        CompletableFuture<String> result = Tools.run(intent.tool(), target, intent.args(), player);
        if (!result.isDone()) assistant.say(ack(intent.tool()));
        result.whenComplete((msg, ex) -> Bukkit.getScheduler().runTask(Mai.getInstance(), () -> {
            if (ex != null) {
                String why = Tools.errorMessage(ex);
                if (!why.equals("Cancelled.") && !why.equals("Queue cancelled.")) assistant.say(why);
            } else if (intent.tool().equals("status")) {
                assistant.say(statusLine(assistant));
            } else {
                assistant.say(msg);
            }
        }));
    }

    private static String ack(String tool) {
        return switch (tool) {
            case "come", "goto", "give" -> "On my way.";
            case "mine" -> "Mining it.";
            case "place" -> "Placing it.";
            default -> "Okay.";
        };
    }

    private static String statusLine(Assistant a) {
        var e = a.humanoid().getRawMannequin();
        if (e == null || !e.isValid()) return "I'm not spawned right now.";
        var l = e.getLocation();
        return String.format("I'm %s, at %d %d %d with %.0f/20 health.", a.activity(),
                l.getBlockX(), l.getBlockY(), l.getBlockZ(), e.getHealth());
    }
}
