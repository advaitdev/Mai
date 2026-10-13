package me.advait.mai.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.advait.mai.Catalog;
import me.advait.mai.Mai;
import me.advait.mai.assistant.Assistant;
import me.advait.mai.assistant.Selection;
import me.advait.mai.assistant.ToolException;
import me.advait.mai.assistant.Tools;
import me.advait.mai.body.Humanoid;
import me.advait.mai.brain.action.HumanoidActionAgent;
import me.advait.mai.brain.action.mechanic.movement.runnable.HumanoidWalkToRunnable;
import me.advait.mai.file.serialization.LocationSerializer;
import me.advait.mai.gui.HumanoidInfoGUI;
import me.advait.mai.gui.HumanoidListGUI;
import me.advait.mai.pathetic.PatheticAgent;
import me.advait.mai.util.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /h}: talk to humanoids. Instructions (come, follow, mine, ...) go
 * through {@link Tools}, the same tool set chat and a future LLM use. Which
 * humanoid a command means: an explicit name, else your {@code /h select}
 * choice, else the nearest one to you.
 */
@CommandAlias("humanoid|h|npc")
@Description("Manage and instruct Humanoid NPCs")
public class HumanoidCommand extends BaseCommand {

    private static final String DEFAULT_NAME = "Mai";

    // Live action bar task per player
    private static final Map<UUID, BukkitTask> queueWatchers = new HashMap<>();

    // ===== INSTRUCTIONS =====

    @Subcommand("come")
    @CommandPermission("mai.humanoid.command")
    @CommandCompletion("@humanoids")
    @Syntax("[humanoid]")
    @Description("Walk over to you")
    public void onCome(Player player, @Optional String bot) {
        tool(player, bot, "come", new JsonObject());
    }

    @Subcommand("follow")
    @CommandPermission("mai.humanoid.command")
    @CommandCompletion("@players")
    @Syntax("[player]")
    @Description("Follow you (or another player) until told to stop")
    public void onFollow(Player player, @Optional String target) {
        JsonObject a = new JsonObject();
        if (target != null) a.addProperty("player", target);
        tool(player, null, "follow", a);
    }

    @Subcommand("guard|protect")
    @CommandPermission("mai.humanoid.command")
    @CommandCompletion("@players")
    @Syntax("[player]")
    @Description("Follow and fight anything that threatens you")
    public void onGuard(Player player, @Optional String target) {
        JsonObject a = new JsonObject();
        if (target != null) a.addProperty("player", target);
        tool(player, null, "guard", a);
    }

    @Subcommand("attack|fight")
    @CommandPermission("mai.humanoid.command")
    @CommandCompletion("@players|nearest smart|basic")
    @Syntax("<player|nearest|humanoid> [smart|basic]")
    @Description("Fight a target until it dies or escapes")
    public void onAttack(Player player, String target, @Optional String style) {
        JsonObject a = new JsonObject();
        a.addProperty("target", target);
        if (style != null) a.addProperty("style", style);
        tool(player, null, "attack", a);
    }

    @Subcommand("stop|stay")
    @CommandPermission("mai.humanoid.command")
    @CommandCompletion("@humanoids")
    @Syntax("[humanoid]")
    @Description("Stop everything and stand still")
    public void onStop(CommandSender sender, @Optional String bot) {
        tool(sender, bot, "stop", new JsonObject());
    }

    @Subcommand("goto")
    @CommandPermission("mai.humanoid.command")
    @CommandCompletion("@players")
    @Syntax("<x> <y> <z> | <player>")
    @Description("Walk to coordinates or to a player")
    public void onGoto(Player player, String[] args) {
        JsonObject a = new JsonObject();
        if (args.length == 3) {
            try {
                Location l = player.getLocation();
                a.addProperty("x", coord(args[0], l.getX()));
                a.addProperty("y", coord(args[1], l.getY()));
                a.addProperty("z", coord(args[2], l.getZ()));
            } catch (NumberFormatException e) {
                Messages.sendMessage(player, "&cCoordinates must be numbers (~ works too).");
                return;
            }
        } else if (args.length == 1) {
            a.addProperty("player", args[0]);
        } else {
            Messages.sendMessage(player, "&cUsage: /h goto <x> <y> <z> or /h goto <player>");
            return;
        }
        tool(player, null, "goto", a);
    }

    @Subcommand("mine|break")
    @CommandPermission("mai.humanoid.command")
    @CommandCompletion("@humanoids")
    @Syntax("[humanoid]")
    @Description("Break the block you're looking at")
    public void onMine(Player player, @Optional String bot) {
        Block target = player.getTargetBlockExact(6);
        if (target == null || target.getType().isAir()) {
            Messages.sendMessage(player, "&cLook at a block first.");
            return;
        }
        tool(player, bot, "mine", xyz(target));
    }

    @Subcommand("place|build")
    @CommandPermission("mai.humanoid.command")
    @Syntax("[block]")
    @Description("Place a block on the face you're looking at")
    public void onPlace(Player player, @Optional String block) {
        Block target = player.getTargetBlockExact(6);
        BlockFace face = player.getTargetBlockFace(6);
        if (target == null || face == null) {
            Messages.sendMessage(player, "&cLook at a block face first.");
            return;
        }
        JsonObject a = xyz(target.getRelative(face));
        if (block != null) a.addProperty("block", block);
        tool(player, null, "place", a);
    }

    @Subcommand("give")
    @CommandPermission("mai.humanoid.command")
    @CommandCompletion("@humanoids")
    @Syntax("[humanoid]")
    @Description("Hand the humanoid the item stack you're holding")
    public void onGive(Player player, @Optional String bot) {
        Humanoid h = resolve(player, bot);
        if (h == null) return;
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            Messages.sendMessage(player, "&cHold the item you want to give.");
            return;
        }
        LivingEntity e = h.getRawMannequin();
        ItemStack copy = held.clone();
        if (e != null && e.getEquipment() != null && e.getEquipment().getItemInMainHand().getType().isAir()) {
            e.getEquipment().setItemInMainHand(copy);
        } else {
            Map<Integer, ItemStack> left = h.getInventory().addItem(copy);
            if (!left.isEmpty()) {
                Messages.sendMessage(player, "&c" + h.getName() + "'s inventory is full.");
                return;
            }
        }
        player.getInventory().setItemInMainHand(null);
        Catalog.getInstance().save(h);
        Messages.sendMessage(player, "&aGave " + h.getName() + " " + copy.getAmount() + " "
                + copy.getType().getKey().getKey().replace('_', ' ') + ".");
    }

    @Subcommand("take")
    @CommandPermission("mai.humanoid.command")
    @Syntax("<item> [count]")
    @Description("Ask the humanoid to bring you items")
    public void onTake(Player player, String item, @Optional Integer count) {
        JsonObject a = new JsonObject();
        a.addProperty("item", item);
        if (count != null) a.addProperty("count", count);
        tool(player, null, "give", a);
    }

    @Subcommand("hold|equip")
    @CommandPermission("mai.humanoid.command")
    @Syntax("<item>")
    @Description("Make the humanoid hold an item from its inventory")
    public void onHold(Player player, String item) {
        JsonObject a = new JsonObject();
        a.addProperty("item", item);
        tool(player, null, "equip", a);
    }

    @Subcommand("inv|inventory")
    @CommandPermission("mai.humanoid.command")
    @CommandCompletion("@humanoids")
    @Syntax("[humanoid]")
    @Description("Open the humanoid's inventory (edits are live)")
    public void onInventory(Player player, @Optional String bot) {
        Humanoid h = resolve(player, bot);
        if (h != null) player.openInventory(h.getInventory());
    }

    @Subcommand("jump")
    @CommandPermission("mai.humanoid.command")
    @CommandCompletion("@humanoids")
    @Syntax("[humanoid]")
    public void onJump(Player player, @Optional String bot) {
        tool(player, bot, "jump", new JsonObject());
    }

    @Subcommand("look")
    @CommandPermission("mai.humanoid.command")
    @CommandCompletion("@humanoids")
    @Syntax("[humanoid]")
    @Description("Look at you")
    public void onLook(Player player, @Optional String bot) {
        tool(player, bot, "look_at", new JsonObject());
    }

    @Subcommand("status")
    @CommandPermission("mai.humanoid.command")
    @CommandCompletion("@humanoids")
    @Syntax("[humanoid]")
    @Description("What it's doing, health, position and inventory")
    public void onStatus(CommandSender sender, @Optional String bot) {
        Humanoid h = resolve(sender, bot);
        if (h == null) return;
        LivingEntity e = h.getRawMannequin();
        Messages.sendMessage(sender, "&6=== " + h.getName() + " ===");
        Messages.sendMessage(sender, "&7Doing: &f" + Assistant.of(h).activity());
        if (e != null && e.isValid()) {
            Location l = e.getLocation();
            Messages.sendMessage(sender, String.format("&7Health: &f%.0f/20  &7At: &f%d %d %d &7(%s)",
                    e.getHealth(), l.getBlockX(), l.getBlockY(), l.getBlockZ(), l.getWorld().getName()));
        } else {
            Messages.sendMessage(sender, "&cNot spawned.");
        }
        Tools.run("inventory", h, new JsonObject(), sender)
                .thenAccept(s -> Messages.sendMessage(sender, "&7" + s));
    }

    @Subcommand("select")
    @CommandPermission("mai.humanoid.command")
    @CommandCompletion("@humanoids")
    @Syntax("<humanoid>")
    @Description("Choose which humanoid your commands control by default")
    public void onSelect(CommandSender sender, String bot) {
        Humanoid h = resolve(sender, bot);
        if (h == null) return;
        Selection.select(sender, h);
        Messages.sendMessage(sender, "&aYour commands now go to &e" + h.getName() + "&a.");
    }

    @Subcommand("tphere|summon")
    @CommandPermission("mai.humanoid.teleport")
    @CommandCompletion("@humanoids")
    @Syntax("[humanoid]")
    @Description("Teleport the humanoid to you")
    public void onTpHere(Player player, @Optional String bot) {
        tool(player, bot, "teleport_to", new JsonObject());
    }

    @Subcommand("tools")
    @CommandPermission("mai.humanoid.command")
    @Description("List every tool (what chat, commands and an LLM can ask for)")
    public void onTools(CommandSender sender) {
        Messages.sendMessage(sender, "&6=== Tools &7(/h do <tool> {json}) &6===");
        for (Tools.Tool t : Tools.all()) {
            StringBuilder params = new StringBuilder();
            for (Tools.Param p : t.params()) {
                params.append(p.required() ? " <" : " [").append(p.name()).append(p.required() ? ">" : "]");
            }
            Messages.sendMessage(sender, "&e" + t.name() + "&f" + params + " &7- " + t.description());
        }
    }

    @Subcommand("do")
    @CommandPermission("mai.humanoid.command")
    @CommandCompletion("@tools")
    @Syntax("<tool> [json args]")
    @Description("Call a tool directly with JSON arguments, e.g. /h do goto {\"x\":0,\"y\":64,\"z\":0}")
    public void onDo(CommandSender sender, String toolName, @Optional String[] json) {
        JsonObject args = new JsonObject();
        if (json != null && json.length > 0) {
            try {
                args = JsonParser.parseString(String.join(" ", json)).getAsJsonObject();
            } catch (RuntimeException e) {
                Messages.sendMessage(sender, "&cArguments must be a JSON object, e.g. {\"player\":\"Steve\"}");
                return;
            }
        }
        tool(sender, null, toolName, args);
    }

    // ===== MANAGEMENT =====

    @Subcommand("spawn")
    @CommandPermission("mai.humanoid.spawn")
    @CommandCompletion("@nothing")
    @Syntax("[name]")
    @Description("Spawn a new Humanoid at your location (default name: Mai)")
    public void onSpawn(Player player, @Optional String name) {
        if (name == null) name = DEFAULT_NAME;
        if (Catalog.getInstance().nameExists(name)) {
            Messages.sendMessage(player, "&cA Humanoid named '&e" + name + "&c' already exists. Pick another name.");
            return;
        }
        Humanoid humanoid = Catalog.getInstance().register(name, player.getLocation());
        Selection.select(player, humanoid);
        Messages.sendMessage(player, "&aSpawned '&e" + name + "&a' and selected it. Try &f/h follow&a, or say &f"
                + name + ", follow me &ain chat.");
    }

    @Subcommand("spawnat")
    @CommandPermission("mai.humanoid.spawn")
    @CommandCompletion("@nothing @nothing @nothing @nothing")
    @Syntax("<name> <x> <y> <z>")
    @Description("Spawn a new Humanoid at specific coordinates")
    public void onSpawnAt(Player player, String name, double x, double y, double z) {
        if (Catalog.getInstance().nameExists(name)) {
            Messages.sendMessage(player, "&cA Humanoid with that name already exists!");
            return;
        }
        Location location = new Location(player.getWorld(), x, y, z);
        Catalog.getInstance().register(name, location);
        Messages.sendMessage(player, "&aSpawned Humanoid '&e" + name + "&a' at " + LocationSerializer.toReadableString(location));
    }

    @Subcommand("list")
    @CommandPermission("mai.humanoid.list")
    @Description("Open the Humanoid management GUI")
    public void onList(Player player) {
        new HumanoidListGUI(player).open();
    }

    @Subcommand("tp|teleport")
    @CommandPermission("mai.humanoid.teleport")
    @CommandCompletion("@humanoids")
    @Syntax("[humanoid]")
    @Description("Teleport to a Humanoid")
    public void onTeleport(Player player, @Optional String name) {
        Humanoid humanoid = resolve(player, name);
        if (humanoid == null) return;
        if (humanoid.getMannequin() == null) {
            Messages.sendMessage(player, "&cThat Humanoid is not currently spawned!");
            return;
        }
        player.teleport(humanoid.getMannequin().getLocation());
        Messages.sendMessage(player, "&aTeleported to '&e" + humanoid.getName() + "&a'");
    }

    @Subcommand("delete|remove")
    @CommandPermission("mai.humanoid.delete")
    @CommandCompletion("@humanoids")
    @Syntax("<name>")
    @Description("Delete a Humanoid")
    public void onDelete(CommandSender sender, String name) {
        Humanoid humanoid = Catalog.getInstance().getByName(name);
        if (humanoid == null) {
            Messages.sendMessage(sender, "&cNo Humanoid found with name '&e" + name + "&c'");
            return;
        }
        Assistant.forget(humanoid.getUuid());
        Catalog.getInstance().unregister(humanoid);
        Messages.sendMessage(sender, "&aDeleted Humanoid '&e" + name + "&a'");
    }

    @Subcommand("info")
    @CommandPermission("mai.humanoid.info")
    @CommandCompletion("@humanoids")
    @Syntax("[humanoid]")
    @Description("Open the info GUI for a Humanoid")
    public void onInfo(Player player, @Optional String name) {
        Humanoid humanoid = resolve(player, name);
        if (humanoid != null) new HumanoidInfoGUI(player, humanoid).open();
    }

    @Subcommand("save")
    @CommandPermission("mai.humanoid.admin")
    @Description("Force save all Humanoids to file")
    public void onSave(CommandSender sender) {
        Catalog.getInstance().saveAll();
        Messages.sendMessage(sender, "&aSaved " + Catalog.getInstance().getCount() + " Humanoid(s) to file.");
    }

    @Subcommand("rename")
    @CommandPermission("mai.humanoid.rename")
    @CommandCompletion("@humanoids @nothing")
    @Syntax("<oldName> <newName>")
    @Description("Rename a Humanoid")
    public void onRename(CommandSender sender, String oldName, String newName) {
        Humanoid humanoid = Catalog.getInstance().getByName(oldName);
        if (humanoid == null) {
            Messages.sendMessage(sender, "&cNo Humanoid found with name '&e" + oldName + "&c'");
            return;
        }
        if (Catalog.getInstance().nameExists(newName)) {
            Messages.sendMessage(sender, "&cA Humanoid with name '&e" + newName + "&c' already exists!");
            return;
        }
        humanoid.setName(newName);
        Catalog.getInstance().save(humanoid);
        Messages.sendMessage(sender, "&aRenamed Humanoid from '&e" + oldName + "&a' to '&e" + newName + "&a'");
    }

    // ===== DEBUG =====

    @Subcommand("debug particles")
    @CommandPermission("mai.humanoid.debug")
    @Description("Toggle path debug particles + event log")
    public void onDebugParticles(CommandSender sender) {
        boolean current = HumanoidWalkToRunnable.isDebugMode();
        HumanoidWalkToRunnable.setDebugMode(!current);
        Messages.sendMessage(sender, "&7Path debug: " + (!current ? "&aEnabled" : "&cDisabled"));
        if (!current) {
            Messages.sendMessage(sender, "&7Events logged to console. Tail with: &ftail -f mc-server/logs/latest.log | grep PathDebug");
        }
    }

    @Subcommand("debug pathcheck")
    @CommandPermission("mai.humanoid.debug")
    @CommandCompletion("@humanoids")
    @Syntax("[humanoid]")
    @Description("Check if pathfinding to your location is possible")
    public void onDebugPathCheck(Player player, @Optional String name) {
        Humanoid humanoid = resolve(player, name);
        if (humanoid == null || humanoid.getEntity() == null) return;
        long startTime = System.currentTimeMillis();
        PatheticAgent.getInstance().getAnnotatedPath(humanoid, humanoid.getEntity().getLocation(), player.getLocation())
                .thenAccept(pathOpt -> {
                    long elapsed = System.currentTimeMillis() - startTime;
                    if (pathOpt.isPresent()) {
                        Messages.sendMessage(player, "&aPath found! &7(" + pathOpt.get().size() + " waypoints, " + elapsed + "ms)");
                    } else {
                        Messages.sendMessage(player, "&cNo path found! &7(" + elapsed + "ms)");
                    }
                })
                .exceptionally(ex -> {
                    Messages.sendMessage(player, "&cPathfinding error: " + ex.getMessage());
                    return null;
                });
    }

    @Subcommand("debug queue")
    @CommandPermission("mai.humanoid.debug")
    @CommandCompletion("@humanoids")
    @Syntax("[humanoid]")
    @Description("Show the current action queue")
    public void onDebugQueue(CommandSender sender, @Optional String name) {
        Humanoid humanoid = resolve(sender, name);
        if (humanoid == null) return;
        HumanoidActionAgent agent = humanoid.getActionAgent();
        String currentAction = agent.getCurrentActionName();
        List<String> queued = agent.getQueuedActionNames();
        Messages.sendMessage(sender, "&6=== " + humanoid.getName() + " Action Queue ===");
        Messages.sendMessage(sender, "&7Activity: &f" + Assistant.of(humanoid).activity());
        Messages.sendMessage(sender, currentAction != null ? "&a> Running: &f" + currentAction : "&7> No action running");
        for (int i = 0; i < queued.size(); i++) {
            Messages.sendMessage(sender, "&e  " + (i + 1) + ". " + queued.get(i));
        }
    }

    @Subcommand("debug watch")
    @CommandPermission("mai.humanoid.debug")
    @CommandCompletion("@humanoids")
    @Syntax("[humanoid]")
    @Description("Toggle a live action bar showing what the humanoid is doing")
    public void onDebugWatch(Player player, @Optional String name) {
        UUID playerId = player.getUniqueId();
        if (queueWatchers.containsKey(playerId)) {
            queueWatchers.remove(playerId).cancel();
            Messages.sendMessage(player, "&7Watcher &cdisabled");
            player.sendActionBar(Component.empty());
            return;
        }
        Humanoid humanoid = resolve(player, name);
        if (humanoid == null) return;

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(Mai.getInstance(), () -> {
            Player p = Bukkit.getPlayer(playerId);
            if (p == null || !p.isOnline()) {
                BukkitTask t = queueWatchers.remove(playerId);
                if (t != null) t.cancel();
                return;
            }
            p.sendActionBar(Component.text(humanoid.getName() + " ", NamedTextColor.GOLD)
                    .append(Component.text(Assistant.of(humanoid).activity(), NamedTextColor.GREEN, TextDecoration.BOLD)));
        }, 0L, 5L);
        queueWatchers.put(playerId, task);
        Messages.sendMessage(player, "&7Watcher &aenabled &7for '&e" + humanoid.getName() + "&7'");
    }

    @Subcommand("debug")
    @CommandPermission("mai.humanoid.debug")
    public void onDebugHelp(CommandSender sender) {
        Messages.sendMessage(sender, "&6=== Debug ===");
        Messages.sendMessage(sender, "&e/h debug particles &7- Toggle path particles + PathDebug log");
        Messages.sendMessage(sender, "&e/h debug pathcheck [humanoid] &7- Can it path to you?");
        Messages.sendMessage(sender, "&e/h debug queue [humanoid] &7- Show the action queue");
        Messages.sendMessage(sender, "&e/h debug watch [humanoid] &7- Live activity in your action bar");
    }

    @HelpCommand
    @Default
    @CatchUnknown
    public void onHelp(CommandSender sender) {
        Messages.sendMessage(sender, "&6=== Humanoids &7(or just say \"Mai, follow me\" in chat) &6===");
        Messages.sendMessage(sender, "&e/h spawn [name] &7- Spawn one at you (default Mai)");
        Messages.sendMessage(sender, "&e/h come &7| &e/h follow [player] &7| &e/h stop");
        Messages.sendMessage(sender, "&e/h guard [player] &7- Follow and fight threats");
        Messages.sendMessage(sender, "&e/h attack <player|nearest> [smart|basic] &7- Fight");
        Messages.sendMessage(sender, "&e/h goto <x y z|player> &7| &e/h tphere");
        Messages.sendMessage(sender, "&e/h mine &7| &e/h place [block] &7- Where you're looking");
        Messages.sendMessage(sender, "&e/h give &7(your held stack) | &e/h take <item> [n] &7| &e/h hold <item> &7| &e/h inv");
        Messages.sendMessage(sender, "&e/h status &7| &e/h select <name> &7- Default humanoid for your commands");
        Messages.sendMessage(sender, "&e/h tools &7| &e/h do <tool> {json} &7- The LLM-facing tool set");
        Messages.sendMessage(sender, "&e/h list &7| &e/h info &7| &e/h rename &7| &e/h delete &7| &e/h debug");
    }

    // ===== HELPERS =====

    /** Resolve a humanoid for {@code sender}, messaging them and returning null on failure. */
    private static Humanoid resolve(CommandSender sender, String name) {
        try {
            return Selection.resolve(sender, name);
        } catch (ToolException e) {
            Messages.sendMessage(sender, "&c" + e.getMessage());
            return null;
        }
    }

    /** Run a tool and report back: immediately if it's instant, else when it finishes. */
    private static void tool(CommandSender sender, String bot, String toolName, JsonObject args) {
        Humanoid h = resolve(sender, bot);
        if (h == null) return;
        CompletableFuture<String> f = Tools.run(toolName, h, args, sender);
        if (!f.isDone()) Messages.sendMessage(sender, "&7" + h.getName() + " is on it...");
        f.whenComplete((msg, ex) -> Bukkit.getScheduler().runTask(Mai.getInstance(), () -> {
            if (ex != null) {
                String why = Tools.errorMessage(ex);
                if (!why.equals("Cancelled.") && !why.equals("Queue cancelled.")) {
                    Messages.sendMessage(sender, "&c" + h.getName() + ": " + why);
                }
            } else {
                Messages.sendMessage(sender, "&a" + h.getName() + ": &f" + msg);
            }
        }));
    }

    private static JsonObject xyz(Block b) {
        JsonObject a = new JsonObject();
        a.addProperty("x", b.getX());
        a.addProperty("y", b.getY());
        a.addProperty("z", b.getZ());
        return a;
    }

    /** Parse a coordinate, supporting ~ and ~N relative to {@code base}. */
    private static double coord(String s, double base) {
        if (s.startsWith("~")) return Math.floor(base) + (s.length() > 1 ? Double.parseDouble(s.substring(1)) : 0);
        return Double.parseDouble(s);
    }
}
