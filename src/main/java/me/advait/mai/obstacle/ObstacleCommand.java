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
import me.advait.mai.brain.action.mechanic.movement.runnable.HumanoidWalkToRunnable;
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

    @Subcommand("reload")
    @CommandPermission("mai.obstacle.run")
    @Description("Hot-reload settings.yml into the movement config (console-safe)")
    public void onReloadSettings(CommandSender sender) {
        me.advait.mai.Mai.getInstance().reloadSettings();
        Messages.sendMessage(sender, "&aSettings reloaded; new walks use the updated movement config.");
    }

    @Subcommand("tune list")
    @CommandPermission("mai.obstacle.run")
    @Syntax("[prefix]")
    @Description("List movement parameters (optionally filtered by key prefix)")
    public void onTuneList(CommandSender sender, @co.aikar.commands.annotation.Optional String prefix) {
        int n = 0;
        for (var e : me.advait.mai.pathetic.config.MovementTuning.snapshot().entrySet()) {
            if (prefix != null && !e.getKey().startsWith(prefix)) continue;
            Messages.sendMessage(sender, "&e" + e.getKey() + " &7= &f" + e.getValue());
            n++;
        }
        Messages.sendMessage(sender, "&7" + n + " parameter(s). Profiles: &f"
                + me.advait.mai.pathetic.config.MovementTuning.profiles());
    }

    @Subcommand("tune get")
    @CommandPermission("mai.obstacle.run")
    @Syntax("<key>")
    public void onTuneGet(CommandSender sender, String key) {
        Object v = me.advait.mai.pathetic.config.MovementTuning.get(key);
        Messages.sendMessage(sender, v == null ? "&cUnknown key: " + key : "&e" + key + " &7= &f" + v);
    }

    @Subcommand("tune set")
    @CommandPermission("mai.obstacle.run")
    @Syntax("<key> <value>")
    @Description("Set a movement parameter (applies to new walks immediately)")
    public void onTuneSet(CommandSender sender, String key, String value) {
        String err = me.advait.mai.pathetic.config.MovementTuning.set(key, value);
        Messages.sendMessage(sender, err != null ? "&c" + err : "&a" + key + " &7= &f"
                + me.advait.mai.pathetic.config.MovementTuning.get(key));
    }

    @Subcommand("tune profile")
    @CommandPermission("mai.obstacle.run")
    @Syntax("<name>")
    @Description("Apply a named movement profile from settings.yml")
    public void onTuneProfile(CommandSender sender, String name) {
        String err = me.advait.mai.pathetic.config.MovementTuning.applyProfile(name);
        Messages.sendMessage(sender, err != null ? "&c" + err : "&aApplied movement profile '&e" + name + "&a'.");
    }

    @Subcommand("tune save")
    @CommandPermission("mai.obstacle.run")
    @Description("Persist current movement parameters to settings.yml")
    public void onTuneSave(CommandSender sender) {
        me.advait.mai.pathetic.config.MovementTuning.save();
        Messages.sendMessage(sender, "&aSaved movement parameters to settings.yml.");
    }

    @Subcommand("debug")
    @CommandPermission("mai.obstacle.run")
    @Syntax("[on|off]")
    @Description("Toggle PathDebug console logging (console-safe)")
    public void onDebug(CommandSender sender, @co.aikar.commands.annotation.Optional String state) {
        boolean enable;
        if (state == null) enable = !HumanoidWalkToRunnable.isDebugMode();
        else enable = state.equalsIgnoreCase("on") || state.equalsIgnoreCase("true");
        HumanoidWalkToRunnable.setDebugMode(enable);
        Messages.sendMessage(sender, "&7Path debug: " + (enable ? "&aEnabled" : "&cDisabled"));
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
        Messages.sendMessage(sender, "&e/mai debug [on|off] &7- Toggle PathDebug console log");
        Messages.sendMessage(sender, "&e/mai reload &7- Hot-reload settings.yml movement config");
        Messages.sendMessage(sender, "&e/mai tune list|get|set|profile|save &7- Runtime movement tuning");
    }

    @Subcommand("do")
    @CommandPermission("mai.obstacle.run")
    @CommandCompletion("@humanoids @tools")
    @Syntax("<humanoid> <tool> [json args]")
    @Description("Call an assistant tool from the console, e.g. mai do Mai follow {\"player\":\"Steve\"}")
    public void onDo(CommandSender sender, String humanoid, String tool, @co.aikar.commands.annotation.Optional String[] json) {
        me.advait.mai.body.Humanoid h = me.advait.mai.Catalog.getInstance().getByName(humanoid);
        if (h == null) {
            Messages.sendMessage(sender, "&cNo humanoid named " + humanoid);
            return;
        }
        com.google.gson.JsonObject args = new com.google.gson.JsonObject();
        if (json != null && json.length > 0) {
            try {
                args = com.google.gson.JsonParser.parseString(String.join(" ", json)).getAsJsonObject();
            } catch (RuntimeException e) {
                Messages.sendMessage(sender, "&cArguments must be a JSON object.");
                return;
            }
        }
        me.advait.mai.assistant.Tools.run(tool, h, args, sender).whenComplete((msg, ex) ->
                org.bukkit.Bukkit.getScheduler().runTask(me.advait.mai.Mai.getInstance(), () ->
                        Messages.sendMessage(sender, ex != null
                                ? "&c[Tool] " + tool + " failed: " + me.advait.mai.assistant.Tools.errorMessage(ex)
                                : "&a[Tool] " + tool + ": " + msg)));
    }

    @Subcommand("tools")
    @CommandPermission("mai.obstacle.run")
    @Description("Print the assistant tool schema as JSON (for LLM function calling)")
    public void onTools(CommandSender sender) {
        sender.sendMessage(me.advait.mai.assistant.Tools.schema().toString());
    }

    @Subcommand("observe")
    @CommandPermission("mai.obstacle.run")
    @CommandCompletion("@humanoids")
    @Syntax("<humanoid>")
    @Description("Print a humanoid's observation JSON")
    public void onObserve(CommandSender sender, String humanoid) {
        me.advait.mai.body.Humanoid h = me.advait.mai.Catalog.getInstance().getByName(humanoid);
        if (h == null) {
            Messages.sendMessage(sender, "&cNo humanoid named " + humanoid);
            return;
        }
        sender.sendMessage(me.advait.mai.assistant.Observation.of(h).toString());
    }

    @Subcommand("ask")
    @CommandPermission("mai.obstacle.run")
    @CommandCompletion("@humanoids")
    @Syntax("<humanoid> <message...>")
    @Description("Say something to a humanoid through the Claude chat brain (assistant.llm)")
    public void onAsk(CommandSender sender, String humanoid, String message) {
        me.advait.mai.body.Humanoid h = me.advait.mai.Catalog.getInstance().getByName(humanoid);
        if (h == null) {
            Messages.sendMessage(sender, "&cNo humanoid named " + humanoid);
            return;
        }
        if (!me.advait.mai.assistant.LlmBrain.available()) {
            Messages.sendMessage(sender, "&cThe chat brain is off: set assistant.llm.enabled and an API key"
                    + " (assistant.llm.api_key or ANTHROPIC_API_KEY), then /mai reload.");
            return;
        }
        me.advait.mai.assistant.LlmBrain.handle(h, sender, message,
                () -> Messages.sendMessage(sender, "&cCouldn't reach the model; see the console."));
    }

    @Subcommand("spawn")
    @CommandPermission("mai.obstacle.run")
    @Syntax("<name> <x> <y> <z> [world]")
    @Description("Spawn a humanoid at coordinates (console-safe)")
    public void onSpawn(CommandSender sender, String name, double x, double y, double z,
                        @co.aikar.commands.annotation.Optional String world) {
        if (me.advait.mai.Catalog.getInstance().nameExists(name)) {
            Messages.sendMessage(sender, "&cA humanoid named " + name + " already exists.");
            return;
        }
        org.bukkit.World w = world != null ? org.bukkit.Bukkit.getWorld(world) : org.bukkit.Bukkit.getWorlds().getFirst();
        if (w == null) {
            Messages.sendMessage(sender, "&cNo world named " + world);
            return;
        }
        me.advait.mai.Catalog.getInstance().register(name, new org.bukkit.Location(w, x, y, z));
        Messages.sendMessage(sender, "&aSpawned " + name + " in " + w.getName());
    }

    @Subcommand("give")
    @CommandPermission("mai.obstacle.run")
    @Syntax("<humanoid> <item> [count]")
    @Description("Put items in a humanoid's inventory (admin setup, console-safe)")
    public void onGive(CommandSender sender, String humanoid, String item,
                       @co.aikar.commands.annotation.Optional Integer count) {
        me.advait.mai.body.Humanoid h = me.advait.mai.Catalog.getInstance().getByName(humanoid);
        org.bukkit.Material m = org.bukkit.Material.matchMaterial(item);
        if (h == null || m == null) {
            Messages.sendMessage(sender, "&cUnknown humanoid or item.");
            return;
        }
        h.getInventory().addItem(new org.bukkit.inventory.ItemStack(m, count == null ? 1 : count));
        me.advait.mai.Catalog.getInstance().save(h);
        Messages.sendMessage(sender, "&aGave " + humanoid + " " + (count == null ? 1 : count) + " " + m.getKey().getKey());
    }

    @Subcommand("duel trace")
    @CommandPermission("mai.obstacle.run")
    @Description("Toggle per-tick combat decision logging")
    public void onDuelTrace(CommandSender sender) {
        me.advait.mai.assistant.CombatBehavior.trace = !me.advait.mai.assistant.CombatBehavior.trace;
        Messages.sendMessage(sender, "&7Combat trace: " + me.advait.mai.assistant.CombatBehavior.trace);
    }

    @Subcommand("duel")
    @CommandPermission("mai.obstacle.run")
    @CommandCompletion("@nothing smart|basic smart|basic @nothing")
    @Syntax("[rounds] [styleA] [styleB] [weapon]")
    @Description("PvP benchmark: two humanoids fight on a platform in the obstacle world")
    public void onDuel(CommandSender sender, @co.aikar.commands.annotation.Optional Integer rounds,
                       @co.aikar.commands.annotation.Optional String a,
                       @co.aikar.commands.annotation.Optional String b,
                       @co.aikar.commands.annotation.Optional String weapon) {
        org.bukkit.Material w = org.bukkit.Material.matchMaterial(weapon == null ? "iron_sword" : weapon);
        if (w == null) {
            Messages.sendMessage(sender, "&cUnknown weapon " + weapon);
            return;
        }
        me.advait.mai.assistant.Duel.start(sender, rounds == null ? 10 : rounds, style(a, true), style(b, false), w);
    }

    private static me.advait.mai.assistant.CombatBehavior.Style style(String s, boolean smartDefault) {
        if (s == null) return smartDefault ? me.advait.mai.assistant.CombatBehavior.Style.SMART
                : me.advait.mai.assistant.CombatBehavior.Style.BASIC;
        return s.equalsIgnoreCase("basic") ? me.advait.mai.assistant.CombatBehavior.Style.BASIC
                : me.advait.mai.assistant.CombatBehavior.Style.SMART;
    }
}
