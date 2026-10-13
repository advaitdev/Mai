package me.advait.mai.assistant;

import com.google.gson.JsonObject;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a chat instruction ("follow me", "mine this", "attack Steve") into a
 * {@link Tools} call. Deliberately simple keyword rules: this is the slot an
 * LLM replaces later, and it returns the same thing an LLM tool call would
 * (a tool name plus JSON arguments), so nothing downstream changes.
 */
public final class ChatIntents {

    /** A parsed instruction: which tool, with which arguments. */
    public record Intent(String tool, JsonObject args) {}

    public static final String HELP = "Try: follow me, come here, stop, guard me, attack <player|nearest>, "
            + "mine this, place cobblestone, give me <item>, hold <item>, go to x y z, jump, status, inventory.";

    private static final Pattern GOTO = Pattern.compile("^(?:go|walk|move) to (-?\\d+) (-?\\d+) (-?\\d+)$");
    private static final Pattern GIVE = Pattern.compile("^give (?:me|(\\w+)) (?:(\\d+) )?(?:of )?(?:your )?([a-z_ ]+?)s?$");

    private ChatIntents() {}

    /** Parse {@code text} spoken by {@code speaker}; null when it isn't understood. */
    public static Intent parse(String text, Player speaker) {
        String t = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_ -]", " ").replaceAll("\\s+", " ").trim();
        t = t.replaceFirst("^(please|can you|could you|pls|now) ", "").replaceFirst(" (please|pls|now)$", "");
        JsonObject a = new JsonObject();
        String[] w = t.split(" ");
        String first = w[0];
        String rest = t.contains(" ") ? t.substring(t.indexOf(' ') + 1) : "";

        switch (first) {
            case "stop", "stay", "wait", "halt", "freeze", "cancel", "nevermind", "sit" -> {
                return new Intent("stop", a);
            }
            case "follow" -> {
                if (!rest.isEmpty() && !rest.equals("me")) a.addProperty("player", w[1]);
                return new Intent("follow", a);
            }
            case "come", "here" -> {
                return new Intent("come", a);
            }
            case "guard", "protect", "defend" -> {
                if (!rest.isEmpty() && !rest.equals("me")) a.addProperty("player", w[1]);
                return new Intent("guard", a);
            }
            case "attack", "fight", "kill", "hit" -> {
                if (rest.isEmpty() || rest.matches("(that|it|them|those|nearest|mobs?|monsters?|the mob)")) {
                    a.addProperty("target", "nearest");
                } else {
                    a.addProperty("target", rest.equals("me") ? speaker.getName() : w[1]);
                }
                return new Intent("attack", a);
            }
            case "jump" -> {
                return new Intent("jump", a);
            }
            case "mine", "break", "dig" -> {
                Block b = speaker.getTargetBlockExact(6);
                if (b == null || b.getType().isAir()) return null;
                xyz(a, b);
                return new Intent("mine", a);
            }
            case "place", "build", "put" -> {
                Block b = speaker.getTargetBlockExact(6);
                BlockFace face = speaker.getTargetBlockFace(6);
                if (b == null || face == null) return null;
                xyz(a, b.getRelative(face));
                String item = rest.replaceAll("\\b(here|there|this|that|a|an|block|it)\\b", "").trim();
                if (!item.isEmpty()) a.addProperty("block", item.replace(' ', '_'));
                return new Intent("place", a);
            }
            case "hold", "equip", "wield" -> {
                if (rest.isEmpty()) return null;
                a.addProperty("item", rest.replaceFirst("^(your|the|a) ", "").replace(' ', '_'));
                return new Intent("equip", a);
            }
            case "status", "report" -> {
                return new Intent("status", a);
            }
            case "inventory", "inv" -> {
                return new Intent("inventory", a);
            }
            case "tp", "teleport" -> {
                return new Intent("teleport_to", a);
            }
            default -> { }
        }

        if (t.matches("(what are you doing|how are you|where are you|you ok|are you ok)")) return new Intent("status", a);
        if (t.matches("(what do you have|what are you carrying|whats in your inventory)")) return new Intent("inventory", a);
        if (t.matches("look at me|look here")) return new Intent("look_at", a);
        Matcher m = GOTO.matcher(t);
        if (m.matches()) {
            a.addProperty("x", Integer.parseInt(m.group(1)));
            a.addProperty("y", Integer.parseInt(m.group(2)));
            a.addProperty("z", Integer.parseInt(m.group(3)));
            return new Intent("goto", a);
        }
        if (t.startsWith("go to ") || t.startsWith("walk to ")) {
            String who = t.substring(t.indexOf(" to ") + 4);
            if (!who.equals("me")) a.addProperty("player", who.split(" ")[0]);
            return new Intent(who.equals("me") ? "come" : "goto", a);
        }
        m = GIVE.matcher(t);
        if (m.matches()) {
            if (m.group(1) != null) a.addProperty("player", m.group(1));
            if (m.group(2) != null) a.addProperty("count", Integer.parseInt(m.group(2)));
            a.addProperty("item", m.group(3).trim().replace(' ', '_'));
            return new Intent("give", a);
        }
        return null;
    }

    private static void xyz(JsonObject a, Block b) {
        a.addProperty("x", b.getX());
        a.addProperty("y", b.getY());
        a.addProperty("z", b.getZ());
    }
}
