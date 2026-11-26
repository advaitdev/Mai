package me.advait.mai.body.fake;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.ChatVisiblity;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spawns and removes {@link FakePlayer}s: server-side players a humanoid
 * inhabits instead of a Mannequin. They join like a player (tab list,
 * tracking, chunk loading, {@code PlayerJoinEvent}) without join/quit
 * messages, start fresh every time (never saved), and are removed on
 * disable. Main thread only.
 */
public final class FakePlayers implements Listener {

    private static final Set<UUID> FAKES = ConcurrentHashMap.newKeySet();
    private static Plugin plugin;

    private FakePlayers() {}

    public static void init(Plugin p) {
        plugin = p;
        Bukkit.getPluginManager().registerEvents(new FakePlayers(), p);
    }

    public static boolean isFake(Entity e) {
        return e instanceof Player && FAKES.contains(e.getUniqueId());
    }

    /**
     * A name a client accepts as a player's: 3-16 of [A-Za-z0-9_], not taken
     * by anyone online.
     */
    private static String profileName(String name) {
        String base = name.replaceAll("[^A-Za-z0-9_]", "_");
        if (base.length() < 3) base = (base + "___").substring(0, 3);
        if (base.length() > 16) base = base.substring(0, 16);
        String n = base;
        for (int i = 2; Bukkit.getPlayerExact(n) != null; i++) {
            String suffix = Integer.toString(i);
            n = base.substring(0, Math.min(base.length(), 16 - suffix.length())) + suffix;
        }
        return n;
    }

    /** Spawns a fake player named for {@code name} at {@code at}; {@code id} is its entity UUID. */
    public static Player spawn(String name, UUID id, Location at) {
        MinecraftServer server = MinecraftServer.getServer();
        ServerLevel level = ((CraftWorld) at.getWorld()).getHandle();
        GameProfile profile = new GameProfile(id, profileName(name));
        // 0x7F: every skin layer shown (createDefault hides them all).
        ClientInformation info = new ClientInformation(Locale.US.toString().toLowerCase(Locale.ROOT), 2,
                ChatVisiblity.FULL, true, 0x7F, HumanoidArm.RIGHT, false, false, ParticleStatus.MINIMAL);
        FakePlayer fp = new FakePlayer(server, level, profile, info);
        fp.snapTo(at.getX(), at.getY(), at.getZ(), at.getYaw(), at.getPitch());
        FAKES.add(id);
        FakeConnection connection = new FakeConnection(plugin);
        connection.player = fp;
        server.getPlayerList().placeNewPlayer(connection, fp, CommonListenerCookie.createInitial(profile, false));
        // A joining player is invulnerable until its client says it has
        // loaded the world; this one never will.
        fp.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        Player bukkit = fp.getBukkitEntity();
        bukkit.setPersistent(false);
        bukkit.setDisplayName(name);
        bukkit.setPlayerListName(name);
        return bukkit;
    }

    /** Disconnects and removes a fake player (quit event, tab list, world). */
    public static void remove(Player p) {
        if (!isFake(p)) return;
        var handle = ((org.bukkit.craftbukkit.entity.CraftPlayer) p).getHandle();
        try {
            handle.connection.onDisconnect(new DisconnectionDetails(net.minecraft.network.chat.Component.literal("removed")));
        } finally {
            handle.connection.connection.channel.close();
            FAKES.remove(p.getUniqueId());
        }
    }

    /** Respawns a dead fake player where vanilla would (same player object). */
    public static void respawn(Player p) {
        if (!isFake(p) || !p.isDead()) return;
        p.spigot().respawn();
        var handle = ((org.bukkit.craftbukkit.entity.CraftPlayer) p).getHandle();
        handle.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
    }

    public static void removeAll() {
        for (Player p : Bukkit.getOnlinePlayers().toArray(new Player[0])) remove(p);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent e) {
        if (isFake(e.getPlayer())) e.joinMessage(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent e) {
        if (isFake(e.getPlayer())) e.quitMessage(null);
    }
}
