package me.advait.patheticcitizens.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import de.metaphoriker.pathetic.api.pathing.result.PathfinderResult;
import de.metaphoriker.pathetic.bukkit.mapper.BukkitMapper;
import me.advait.patheticcitizens.pathfinder.PatheticAgent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

@CommandAlias("pathetictest")
public class PatheticTestCommand extends BaseCommand {

    private static final Map<UUID, PlayerSession> SESSION_MAP = new HashMap<>();

    private final PatheticAgent patheticAgent = PatheticAgent.getInstance();

    @CommandAlias("pos1")
    public void runNPCPos1(Player player) {
        PlayerSession session = SESSION_MAP.computeIfAbsent(player.getUniqueId(), k -> new PlayerSession());
        session.setPos1(player.getLocation());
        player.sendMessage(Component.text("Position 1 set to " + session.getPos1()).color(NamedTextColor.GREEN));
    }

    @CommandAlias("pos2")
    public void runNPCPos2(Player player) {
        PlayerSession session = SESSION_MAP.computeIfAbsent(player.getUniqueId(), k -> new PlayerSession());
        session.setPos2(player.getLocation());
        player.sendMessage(Component.text("Position 2 set to " + session.getPos1()).color(NamedTextColor.GREEN));
    }

    @CommandAlias("start npc")
    public void runNPCStart(Player player) {
        PlayerSession session = SESSION_MAP.computeIfAbsent(player.getUniqueId(), k -> new PlayerSession());
        if (!session.isComplete()) {
            player.sendMessage(Component.text("Set both locations first!").color(NamedTextColor.RED));
            return;
        }

        Location start = session.getPos1();
        Location end = session.getPos2();

        player.sendMessage(Component.text("Starting ").color(NamedTextColor.GREEN)
                .append(Component.text("NPC path ").color(NamedTextColor.GOLD))
                .append(Component.text("test...").color(NamedTextColor.GREEN)));
        player.sendMessage(Component.text("Calculating path... [Distance: " + start.distance(end) + "]").color(NamedTextColor.GREEN));

        CompletionStage<PathfinderResult> pathfinderResult = patheticAgent.getNPCPath(start, end);

        pathfinderResult.thenAccept(
                result -> {
                    player.sendMessage(Component.text("State: " + result.getPathState().name()).color(NamedTextColor.GOLD));
                    player.sendMessage(Component.text("Bridge path length: " + result.getPath().length()).color(NamedTextColor.GREEN));

                    // If pathfinding is successful, show the path to the player
                    if (result.successful() || result.hasFallenBack()) {
                        result
                                .getPath()
                                .forEach(
                                        position -> {
                                            Location location = BukkitMapper.toLocation(position);
                                            player.sendBlockChange(
                                                    location, Material.YELLOW_STAINED_GLASS.createBlockData());
                                        });
                    } else {
                        player.sendMessage(Component.text("Path not found!").color(NamedTextColor.RED));
                    }
                });
    }

    @CommandAlias("start ground")
    public void runGroundStart(Player player) {
        PlayerSession session = SESSION_MAP.computeIfAbsent(player.getUniqueId(), k -> new PlayerSession());
        if (!session.isComplete()) {
            player.sendMessage(Component.text("Set both locations first!").color(NamedTextColor.RED));
            return;
        }

        Location start = session.getPos1();
        Location end = session.getPos2();

        player.sendMessage(Component.text("Starting ").color(NamedTextColor.GREEN)
                .append(Component.text("ground path ").color(NamedTextColor.GOLD))
                .append(Component.text("test...").color(NamedTextColor.GREEN)));
        player.sendMessage(
                Component.text("Calculating path... [Distance: " + start.distance(end) + "]").color(NamedTextColor.GREEN));

        CompletionStage<PathfinderResult> pathfinderResult = patheticAgent.getGroundPath(start, end);

        pathfinderResult.thenAccept(
                result -> {
                    player.sendMessage(Component.text("State: " + result.getPathState().name()).color(NamedTextColor.GOLD));
                    player.sendMessage(Component.text("Path length: " + result.getPath().length()).color(NamedTextColor.GREEN));

                    // If pathfinding is successful, show the path to the player
                    if (result.successful() || result.hasFallenBack()) {
                        result
                                .getPath()
                                .forEach(
                                        position -> {
                                            Location location = BukkitMapper.toLocation(position);
                                            player.sendBlockChange(
                                                    location, Material.YELLOW_STAINED_GLASS.createBlockData());
                                        });
                    } else {
                        player.sendMessage(Component.text("Path not found!").color(NamedTextColor.RED));
                    }
                });
    }

    private static class PlayerSession {

        private Location pos1;
        private Location pos2;

        public void setPos1(Location pos1) {
            this.pos1 = pos1;
        }

        public void setPos2(Location pos2) {
            this.pos2 = pos2;
        }

        // Check if both positions are set
        public boolean isComplete() {
            return pos1 != null && pos2 != null;
        }

        public Location getPos1() {
            return pos1;
        }

        public Location getPos2() {
            return pos2;
        }
    }

}
