package me.advait.mai.assistant;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.advait.mai.body.Humanoid;
import me.advait.mai.brain.action.mechanic.HumanoidAction;
import me.advait.mai.brain.action.mechanic.building.HumanoidBuildAction;
import me.advait.mai.brain.action.mechanic.building.HumanoidMineAction;
import me.advait.mai.brain.action.mechanic.movement.HumanoidWalkToAction;
import me.advait.mai.brain.action.result.HumanoidActionResult;
import me.advait.mai.pathetic.PatheticAgent;
import me.advait.mai.util.LocationUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Every capability a humanoid exposes, as named tools with typed arguments.
 * Commands, chat and a future LLM all call {@link #run}; nothing else gets
 * to drive the body directly, so the three can never disagree. {@link #schema()}
 * renders the list in the JSON-schema shape LLM function calling expects.
 *
 * <p>A tool returns a future message: immediately for behaviors ("Following
 * advaits."), or when the job finishes for one-shot work ("Mined stone.").
 * Failures complete exceptionally with a {@link ToolException} whose message
 * is meant for the user.
 */
public final class Tools {

    public record Param(String name, String type, String description, boolean required) {}

    @FunctionalInterface
    interface Handler {
        CompletableFuture<String> run(Call call);
    }

    public record Tool(String name, String description, List<Param> params, Handler handler) {}

    /** Survival block interaction range (eyes to block center). */
    private static final double BLOCK_REACH = 4.5;

    private static final Map<String, Tool> TOOLS = new LinkedHashMap<>();

    private static void tool(String name, String description, Handler handler, Param... params) {
        TOOLS.put(name, new Tool(name, description, List.of(params), handler));
    }

    private static Param req(String n, String type, String d) { return new Param(n, type, d, true); }
    private static Param opt(String n, String type, String d) { return new Param(n, type, d, false); }

    static {
        tool("status", "Report position, health, inventory, what you're doing and what's nearby (JSON).",
                c -> done(Observation.of(c.humanoid).toString()));

        tool("come", "Walk to a player (default: whoever asked) and stop next to them.",
                c -> {
                    Player p = c.player("player");
                    Location goal = groundOr(p.getLocation());
                    return finish(c.assistant.run("come to " + p.getName(),
                            new HumanoidWalkToAction(c.humanoid, goal, 2.0)), "I'm here.");
                },
                opt("player", "string", "Player name; defaults to the requester"));

        tool("goto", "Walk to block coordinates, or to a player.",
                c -> {
                    Location goal = c.has("x") ? c.xyz() : groundOr(c.player("player").getLocation());
                    return finish(c.assistant.run("goto", new HumanoidWalkToAction(c.humanoid, goal)),
                            "Arrived.");
                },
                opt("x", "number", "Block X"), opt("y", "number", "Block Y (feet)"), opt("z", "number", "Block Z"),
                opt("player", "string", "Player to walk to instead of coordinates"));

        tool("follow", "Keep following a player until told to stop.",
                c -> {
                    Player p = c.player("player");
                    double dist = c.num("distance", AssistantConfig.followDistance());
                    c.assistant.setBehavior(new FollowBehavior(c.humanoid, p, dist));
                    return done("Following " + p.getName() + ".");
                },
                opt("player", "string", "Player name; defaults to the requester"),
                opt("distance", "number", "How close to stay, in blocks"));

        tool("guard", "Follow a player and fight hostile mobs or anyone who attacks them.",
                c -> {
                    Player p = c.player("player");
                    c.assistant.setBehavior(new GuardBehavior(c.humanoid, p));
                    return done("Guarding " + p.getName() + ".");
                },
                opt("player", "string", "Player name; defaults to the requester"));

        tool("attack", "Fight a target until it dies or escapes: a player name, 'nearest' (nearest hostile mob), or an entity UUID.",
                c -> {
                    LivingEntity target = c.target(c.str("target"));
                    CombatBehavior.Style style = "basic".equalsIgnoreCase(c.str("style", "smart"))
                            ? CombatBehavior.Style.BASIC : CombatBehavior.Style.SMART;
                    c.assistant.setBehavior(new CombatBehavior(c.humanoid, target, style));
                    return done("Fighting " + target.getName() + ".");
                },
                req("target", "string", "Player name, 'nearest', or entity UUID"),
                opt("style", "string", "'smart' (default: strafe, crits, w-tap) or 'basic'"));

        tool("stop", "Stop whatever you're doing and stand still.",
                c -> {
                    c.assistant.stopAll();
                    return done("Stopped.");
                });

        tool("mine", "Walk within reach of a block and break it with the best tool you have.",
                c -> {
                    Block b = c.xyz().getBlock();
                    Material type = b.getType();
                    if (!type.isSolid() || type.getHardness() < 0) {
                        throw new ToolException("I can't break " + name(type) + ".");
                    }
                    List<HumanoidAction> steps = new ArrayList<>();
                    approach(c, b, steps);
                    steps.add(new InstantAction(c.humanoid, "equip tool", h -> {
                        equipBestTool(h, b);
                        return ok("Equipped.");
                    }));
                    steps.add(new HumanoidMineAction(c.humanoid, b, true));
                    return finish(c.assistant.run("mine " + name(type), steps.toArray(HumanoidAction[]::new)),
                            "Mined " + name(type) + ".");
                },
                req("x", "number", "Block X"), req("y", "number", "Block Y"), req("z", "number", "Block Z"));

        tool("place", "Walk within reach and place a block at the coordinates (default: whatever you're holding).",
                c -> {
                    Location at = c.xyz();
                    Block b = at.getBlock();
                    if (!b.isReplaceable()) throw new ToolException("There's already " + name(b.getType()) + " there.");
                    Material want = c.has("block") ? material(c.str("block")) : null;
                    List<HumanoidAction> steps = new ArrayList<>();
                    approach(c, b, steps);
                    steps.add(new InstantAction(c.humanoid, "place", h -> {
                        LivingEntity bot = h.getRawMannequin();
                        Material m = want;
                        if (m == null) {
                            ItemStack hand = bot.getEquipment().getItemInMainHand();
                            if (hand.getType().isAir() || !hand.getType().isBlock()) {
                                return fail("I'm not holding a block. Give me some with /h give.");
                            }
                            m = hand.getType();
                        }
                        if (!equip(h, m)) return fail("I don't have any " + name(m) + ".");
                        if (!LocationUtil.isBuildable(at)) return fail("I can't place a block there.");
                        if (!reachable(bot, b)) return fail("I couldn't get close enough.");
                        ItemStack held = bot.getEquipment().getItemInMainHand();
                        if (!HumanoidBuildAction.placeBlock(bot, at, held)) {
                            return fail("There's nothing to place it against.");
                        }
                        return ok("Placed " + name(m) + ".");
                    }));
                    return finish(c.assistant.run("place", steps.toArray(HumanoidAction[]::new)), null);
                },
                req("x", "number", "Block X"), req("y", "number", "Block Y"), req("z", "number", "Block Z"),
                opt("block", "string", "Block to place, e.g. cobblestone"));

        tool("look_at", "Turn to look at a player (default: the requester) or at coordinates.",
                c -> {
                    LivingEntity bot = c.bot();
                    if (c.has("x")) Motor.look(bot, c.xyz().add(0.5, 0.5, 0.5));
                    else Motor.look(bot, c.player("player").getEyeLocation());
                    return done("Looking.");
                },
                opt("player", "string", "Player name"), opt("x", "number", "X"), opt("y", "number", "Y"), opt("z", "number", "Z"));

        tool("jump", "Jump once.",
                c -> done(Motor.jump(c.bot(), false, PatheticAgent.getInstance().getConfig())
                        ? "Jumped." : "I'm not on the ground."));

        tool("say", "Say something in chat.",
                c -> {
                    c.assistant.say(c.str("message"));
                    return done("Said it.");
                },
                req("message", "string", "What to say"));

        tool("inventory", "List what you're holding and carrying.",
                c -> done(inventorySummary(c.humanoid)));

        tool("equip", "Hold an item from your inventory in your main hand.",
                c -> {
                    Material m = material(c.str("item"));
                    if (!equip(c.humanoid, m)) throw new ToolException("I don't have any " + name(m) + ".");
                    return done("Holding " + name(m) + ".");
                },
                req("item", "string", "Item name, e.g. diamond_sword"));

        tool("give", "Walk to a player and toss them items from your inventory.",
                c -> {
                    Player p = c.player("player");
                    Material m = material(c.str("item"));
                    int count = (int) c.num("count", Integer.MAX_VALUE);
                    if (count(c.humanoid, m) == 0) throw new ToolException("I don't have any " + name(m) + ".");
                    return finish(c.assistant.run("give " + name(m),
                            new HumanoidWalkToAction(c.humanoid, groundOr(p.getLocation()), 2.5),
                            new InstantAction(c.humanoid, "toss", h -> {
                                int n = toss(h, m, count, p);
                                return ok("Gave you " + n + " " + name(m) + ".");
                            })), null);
                },
                req("item", "string", "Item name"), opt("count", "number", "How many (default: all)"),
                opt("player", "string", "Player name; defaults to the requester"));

        tool("teleport_to", "Teleport to a player. An assistant convenience, not a vanilla move.",
                c -> {
                    Player p = c.player("player");
                    c.assistant.stopAll();
                    c.bot().teleport(p.getLocation());
                    return done("Teleported to " + p.getName() + ".");
                },
                opt("player", "string", "Player name; defaults to the requester"));
    }

    private Tools() {}

    public static Collection<Tool> all() {
        return TOOLS.values();
    }

    public static Tool get(String name) {
        return TOOLS.get(name.toLowerCase(Locale.ROOT));
    }

    /** Run a tool. Never throws: problems complete the future exceptionally. */
    public static CompletableFuture<String> run(String name, Humanoid humanoid, JsonObject args, CommandSender requester) {
        Tool t = get(name);
        if (t == null) return CompletableFuture.failedFuture(new ToolException("Unknown tool '" + name + "'. See /h tools"));
        JsonObject a = args != null ? args : new JsonObject();
        for (Param p : t.params()) {
            if (p.required() && (!a.has(p.name()) || a.get(p.name()).isJsonNull())) {
                return CompletableFuture.failedFuture(new ToolException(t.name() + " needs '" + p.name() + "' (" + p.description() + ")"));
            }
        }
        try {
            return t.handler().run(new Call(humanoid, a, requester));
        } catch (ToolException e) {
            return CompletableFuture.failedFuture(e);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(new ToolException(t.name() + " failed: " + e));
        }
    }

    /** Unwraps a tool failure to the message meant for the user. */
    public static String errorMessage(Throwable ex) {
        Throwable t = ex;
        while ((t instanceof CompletionException || t instanceof java.util.concurrent.ExecutionException) && t.getCause() != null) {
            t = t.getCause();
        }
        return t instanceof ToolException ? t.getMessage() : String.valueOf(t);
    }

    /** All tools in the JSON-schema shape used by LLM function calling. */
    public static JsonArray schema() {
        JsonArray arr = new JsonArray();
        for (Tool t : TOOLS.values()) {
            JsonObject fn = new JsonObject();
            fn.addProperty("name", t.name());
            fn.addProperty("description", t.description());
            JsonObject params = new JsonObject();
            params.addProperty("type", "object");
            JsonObject props = new JsonObject();
            JsonArray required = new JsonArray();
            for (Param p : t.params()) {
                JsonObject prop = new JsonObject();
                prop.addProperty("type", p.type());
                prop.addProperty("description", p.description());
                props.add(p.name(), prop);
                if (p.required()) required.add(p.name());
            }
            params.add("properties", props);
            params.add("required", required);
            fn.add("parameters", params);
            arr.add(fn);
        }
        return arr;
    }

    // =========================================================================
    // Call context
    // =========================================================================

    static final class Call {
        final Humanoid humanoid;
        final Assistant assistant;
        final JsonObject args;
        final CommandSender requester;

        Call(Humanoid humanoid, JsonObject args, CommandSender requester) {
            this.humanoid = humanoid;
            this.assistant = Assistant.of(humanoid);
            this.args = args;
            this.requester = requester;
        }

        boolean has(String k) {
            return args.has(k) && !args.get(k).isJsonNull();
        }

        String str(String k) {
            return has(k) ? args.get(k).getAsString() : null;
        }

        String str(String k, String def) {
            return has(k) ? args.get(k).getAsString() : def;
        }

        double num(String k, double def) {
            if (!has(k)) return def;
            JsonElement e = args.get(k);
            try {
                return e.getAsDouble();
            } catch (RuntimeException ex) {
                throw new ToolException("'" + k + "' must be a number.");
            }
        }

        LivingEntity bot() {
            LivingEntity e = humanoid.getEntity();
            if (e == null || !e.isValid()) throw new ToolException(humanoid.getName() + " isn't spawned.");
            return e;
        }

        Location xyz() {
            if (!has("x") || !has("y") || !has("z")) throw new ToolException("Give x, y and z.");
            return new Location(bot().getWorld(), Math.floor(num("x", 0)), Math.floor(num("y", 0)), Math.floor(num("z", 0)));
        }

        /** The named player, or the requester when no name is given. */
        Player player(String key) {
            String n = str(key);
            if (n != null && !n.isBlank() && !n.equalsIgnoreCase("me")) {
                Player p = Bukkit.getPlayerExact(n);
                if (p == null) throw new ToolException("No online player named '" + n + "'.");
                return p;
            }
            if (requester instanceof Player p) return p;
            throw new ToolException("Say which player ('" + key + "').");
        }

        LivingEntity target(String spec) {
            LivingEntity bot = bot();
            if (spec.equalsIgnoreCase("nearest") || spec.equalsIgnoreCase("nearest_hostile")) {
                LivingEntity best = null;
                double bestD = 24 * 24;
                for (Entity e : bot.getNearbyEntities(24, 12, 24)) {
                    if (!(e instanceof Enemy) || !(e instanceof LivingEntity le) || le.isDead()) continue;
                    double d = e.getLocation().distanceSquared(bot.getLocation());
                    if (d < bestD) { bestD = d; best = le; }
                }
                if (best == null) throw new ToolException("I don't see any hostile mobs nearby.");
                return best;
            }
            if (spec.equalsIgnoreCase("me") && requester instanceof Player p) return p;
            Player p = Bukkit.getPlayerExact(spec);
            if (p != null) return p;
            try {
                if (Bukkit.getEntity(UUID.fromString(spec)) instanceof LivingEntity le) return le;
            } catch (IllegalArgumentException ignored) {
                // not a UUID
            }
            me.advait.mai.body.Humanoid other = me.advait.mai.Catalog.getInstance().getByName(spec);
            if (other != null && other != humanoid && other.getRawMannequin() != null) return other.getRawMannequin();
            throw new ToolException("I can't find '" + spec + "'.");
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static CompletableFuture<String> done(String message) {
        return CompletableFuture.completedFuture(message);
    }

    private static HumanoidActionResult ok(String m) { return new HumanoidActionResult(true, m); }
    private static HumanoidActionResult fail(String m) { return new HumanoidActionResult(false, m); }

    /** Map an action-queue result to a tool result; {@code success} null keeps the action's own message. */
    private static CompletableFuture<String> finish(CompletableFuture<HumanoidActionResult> f, String success) {
        return f.thenApply(r -> {
            if (!r.success()) throw new CompletionException(new ToolException(r.message()));
            return success != null ? success : r.message();
        });
    }

    private static Location groundOr(Location at) {
        Location g = FollowBehavior.landingSpot(at);
        return g != null ? g : at;
    }

    static String name(Material m) {
        return m.getKey().getKey().replace('_', ' ');
    }

    private static Material material(String s) {
        Material m = Material.matchMaterial(s.trim().replace(' ', '_'));
        if (m == null) throw new ToolException("I don't know an item called '" + s + "'.");
        return m;
    }

    private static boolean reachable(LivingEntity bot, Block b) {
        return bot.getEyeLocation().distance(b.getLocation().add(0.5, 0.5, 0.5)) <= BLOCK_REACH;
    }

    /** Queue a walk to a spot within reach of {@code b}, unless already in reach. */
    private static void approach(Call c, Block b, List<HumanoidAction> steps) {
        LivingEntity bot = c.bot();
        if (reachable(bot, b)) return;
        Location spot = standSpotNear(b, bot.getLocation());
        if (spot == null) throw new ToolException("I can't find anywhere to stand within reach of that block.");
        steps.add(new HumanoidWalkToAction(c.humanoid, spot));
    }

    /** Closest standable cell (to {@code from}) whose eyes are within reach of {@code b}. */
    private static Location standSpotNear(Block b, Location from) {
        Location center = b.getLocation().add(0.5, 0.5, 0.5);
        Location best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dy = -3; dy <= 2; dy++) {
                    Location cell = b.getLocation().add(dx, dy, dz);
                    if (dx == 0 && dz == 0 && (dy == 0 || dy == -1)) continue;   // inside the block
                    Location eye = cell.clone().add(0.5, 1.62, 0.5);
                    if (eye.distance(center) > BLOCK_REACH - 0.3) continue;
                    if (!PatheticAgent.getInstance().isStandable(cell)) continue;
                    double d = cell.distanceSquared(from);
                    if (d < bestD) { bestD = d; best = cell.clone().add(0.5, 0, 0.5); }
                }
            }
        }
        return best;
    }

    /** Move the fastest tool for {@code b} into the main hand. */
    static void equipBestTool(Humanoid h, Block b) {
        LivingEntity bot = h.getRawMannequin();
        if (bot == null || bot.getEquipment() == null) return;
        float best = b.getDestroySpeed(bot.getEquipment().getItemInMainHand());
        int bestSlot = -1;
        ItemStack[] inv = h.getInventory().getContents();
        for (int i = 0; i < inv.length; i++) {
            if (inv[i] == null || inv[i].getType().isAir()) continue;
            float s = b.getDestroySpeed(inv[i]);
            if (s > best + 1e-3) { best = s; bestSlot = i; }
        }
        if (bestSlot >= 0) h.setItemInMainHand(bestSlot);
    }

    /** Put an item of type {@code m} in the main hand. False if there is none. */
    static boolean equip(Humanoid h, Material m) {
        LivingEntity bot = h.getRawMannequin();
        if (bot == null || bot.getEquipment() == null) return false;
        if (bot.getEquipment().getItemInMainHand().getType() == m) return true;
        int slot = h.getInventory().first(m);
        if (slot < 0) return false;
        h.setItemInMainHand(slot);
        return true;
    }

    private static int count(Humanoid h, Material m) {
        int n = 0;
        LivingEntity bot = h.getRawMannequin();
        if (bot != null && bot.getEquipment() != null && bot.getEquipment().getItemInMainHand().getType() == m) {
            n += bot.getEquipment().getItemInMainHand().getAmount();
        }
        for (ItemStack it : h.getInventory().getContents()) {
            if (it != null && it.getType() == m) n += it.getAmount();
        }
        return n;
    }

    /** Remove up to {@code max} of {@code m} and throw them toward {@code to}. Returns how many. */
    private static int toss(Humanoid h, Material m, int max, Player to) {
        LivingEntity bot = h.getRawMannequin();
        int left = max;
        List<ItemStack> out = new ArrayList<>();
        ItemStack[] inv = h.getInventory().getContents();
        for (int i = 0; i < inv.length && left > 0; i++) {
            ItemStack it = inv[i];
            if (it == null || it.getType() != m) continue;
            int take = Math.min(left, it.getAmount());
            ItemStack part = it.clone();
            part.setAmount(take);
            out.add(part);
            it.setAmount(it.getAmount() - take);
            h.getInventory().setItem(i, it.getAmount() > 0 ? it : null);
            left -= take;
        }
        ItemStack hand = bot.getEquipment().getItemInMainHand();
        if (left > 0 && hand.getType() == m) {
            int take = Math.min(left, hand.getAmount());
            ItemStack part = hand.clone();
            part.setAmount(take);
            out.add(part);
            hand.setAmount(hand.getAmount() - take);
            bot.getEquipment().setItemInMainHand(hand.getAmount() > 0 ? hand : null);
            left -= take;
        }
        Location from = bot.getEyeLocation().add(0, -0.3, 0);
        Vector toward = to.getLocation().toVector().subtract(from.toVector()).setY(0);
        if (toward.lengthSquared() > 0) toward.normalize().multiply(0.3).setY(0.2);
        Motor.look(bot, to.getEyeLocation());
        bot.swingMainHand();
        for (ItemStack part : out) {
            Item drop = bot.getWorld().dropItem(from, part);
            drop.setVelocity(toward);
            drop.setPickupDelay(10);
        }
        return max - left;
    }

    private static String inventorySummary(Humanoid h) {
        LivingEntity bot = h.getRawMannequin();
        Map<Material, Integer> totals = new LinkedHashMap<>();
        for (ItemStack it : h.getInventory().getContents()) {
            if (it != null && !it.getType().isAir()) totals.merge(it.getType(), it.getAmount(), Integer::sum);
        }
        StringBuilder sb = new StringBuilder();
        ItemStack hand = bot != null && bot.getEquipment() != null ? bot.getEquipment().getItemInMainHand() : null;
        sb.append("Holding ").append(hand == null || hand.getType().isAir() ? "nothing"
                : hand.getAmount() + " " + name(hand.getType())).append(". ");
        if (totals.isEmpty()) {
            sb.append("Inventory is empty.");
        } else {
            sb.append("Carrying: ");
            int i = 0;
            for (var e : totals.entrySet()) {
                if (i++ > 0) sb.append(", ");
                sb.append(e.getValue()).append(" ").append(name(e.getKey()));
            }
            sb.append(".");
        }
        return sb.toString();
    }
}
