package me.advait.patheticcitizens.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Subcommand;
import me.advait.patheticcitizens.npc.PatheticNPC;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

@CommandAlias("pnpc")
public class PNPCCommand extends BaseCommand {

    @CommandAlias("walktome")
    public void runWalkToMe(Player player) {
        NPC npc = CitizensAPI.getDefaultNPCSelector().getSelected(player);
        if (npc == null) {
            player.sendMessage(Component.text("You have no NPC selected!").color(NamedTextColor.RED));
            return;
        }

        player.sendMessage(Component.text(npc.getClass().getSimpleName()).color(NamedTextColor.GREEN));

        PatheticNPC patheticNPC = (PatheticNPC) npc;
        patheticNPC.getNavigator().setWalkableTarget(player.getLocation());
    }

}
