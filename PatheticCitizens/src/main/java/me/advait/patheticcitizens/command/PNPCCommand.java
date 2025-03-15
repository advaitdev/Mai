package me.advait.patheticcitizens.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import me.advait.patheticcitizens.navigator.PatheticNavigator;
import me.advait.patheticcitizens.npc.PatheticNPC;
import me.advait.patheticcitizens.npc.PatheticNPCRegistry;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.npc.CitizensNPC;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;

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

}

