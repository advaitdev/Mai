package me.advait.patheticcitizens.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import me.advait.patheticcitizens.PatheticCitizens;
import me.advait.patheticcitizens.npc.PatheticNPC;
import me.advait.patheticcitizens.npc.PatheticNPCRegistry;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.util.NMS;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

@CommandAlias("pnpc")
public class PNPCCommand extends BaseCommand {

    @CommandAlias("walktome")
    public void runWalkToMe(Player player) {
        if (CitizensAPI.getDefaultNPCSelector().getSelected(player) == null) {
            player.sendMessage(Component.text("You have no NPC selected!", NamedTextColor.RED));
            return;
        }

        PatheticNPC npc = PatheticNPCRegistry.getInstance().getPatheticNPC(
                CitizensAPI.getDefaultNPCSelector().getSelected(player));

        if (npc == null) {
            player.sendMessage(Component.text("The selected NPC does not have the Pathetic trait!", NamedTextColor.RED));
            return;
        }

        npc.getNavigator().setWalkableTarget(player.getLocation());
        player.sendMessage(Component.text(npc.getName() + " is walking to you...", NamedTextColor.GREEN));
    }

    @CommandAlias("nmswalktome")
    public void runNMSWalkToMe(Player player) {
        if (CitizensAPI.getDefaultNPCSelector().getSelected(player) == null) {
            player.sendMessage(Component.text("You have no NPC selected!", NamedTextColor.RED));
            return;
        }

        PatheticNPC npc = PatheticNPCRegistry.getInstance().getPatheticNPC(
                CitizensAPI.getDefaultNPCSelector().getSelected(player));

        if (npc == null) {
            player.sendMessage(Component.text("The selected NPC does not have the Pathetic trait!", NamedTextColor.RED));
            return;
        }

        Bukkit.getScheduler().runTaskTimer(PatheticCitizens.getInstance(), task -> {
            NMS.updatePathfindingRange(npc.getCitizensNPC(), 500f);
            if (npc.getLocation().distance(player.getLocation()) <= 1) {
                task.cancel();
                player.sendMessage(Component.text(npc.getName() + "Navigation complete! (Final destination: " + NMS.getDestination(npc.getCitizensNPC().getEntity()) + ")", NamedTextColor.GREEN));
                return;
            }
            NMS.setDestination(npc.getCitizensNPC().getEntity(), player.getLocation().getX(), player.getLocation().getY(), player.getLocation().getZ(), 1.0F);
            player.sendActionBar(Component.text("(Destination: " + NMS.getDestination(npc.getCitizensNPC().getEntity()) + ")", NamedTextColor.GREEN));
        }, 0, 1);
        player.sendMessage(Component.text(npc.getName() + " is walking to you via NMS... (Destination: " + NMS.getDestination(npc.getCitizensNPC().getEntity()) + ")", NamedTextColor.GREEN));
    }

}

