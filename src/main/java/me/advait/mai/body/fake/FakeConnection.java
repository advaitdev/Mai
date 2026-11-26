package me.advait.mai.body.fake;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * The network connection of a fake player: open (the server only sends a new
 * player its tab entry and starts tracking it when connected), but nothing is
 * on the other end, so every packet is dropped. Nothing ticks it either: the
 * server's connection tick snaps a player back to its tick-start position
 * (a real player's moves arrive as packets), and it would time out keepalives.
 */
final class FakeConnection extends Connection {

    private final Plugin plugin;
    /** The player on this connection, which plays the client's part for the packets that carry physics. */
    FakePlayer player;

    FakeConnection(Plugin plugin) {
        super(PacketFlow.SERVERBOUND);
        this.plugin = plugin;
        // Registering the handler fires channelActive: sets channel and address
        // (as vanilla's own test players do).
        new EmbeddedChannel(this);
        this.address = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
    }

    @Override
    public void send(Packet<?> packet, ChannelFutureListener listener, boolean flush) {
        // The server moves a player it hit or shoved by telling its client
        // the new velocity; the client takes it as its own (see FakePlayer).
        if (player != null && packet instanceof ClientboundSetEntityMotionPacket motion && motion.id() == player.getId()) {
            player.clientMotion = motion.movement();
        }
        // The rest is dropped, but completion callbacks still run (a kick disconnects in one).
        if (listener != null) {
            try {
                listener.operationComplete(channel.newSucceededFuture());
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void flushChannel() {
    }

    @Override
    public void disconnect(DisconnectionDetails details) {
        boolean was = isConnected();
        super.disconnect(details);
        // Nothing ticks this connection, so run the disconnect handling ourselves.
        if (was) Bukkit.getScheduler().runTask(plugin, this::handleDisconnection);
    }
}
