package me.advait.patheticcitizens.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import me.advait.patheticcitizens.navigator.PatheticNavigator;
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
        NPC npc = CitizensAPI.getDefaultNPCSelector().getSelected(player);
        if (npc == null) {
            player.sendMessage(Component.text("You have no NPC selected!").color(NamedTextColor.RED));
            return;
        }

        try {
            Field navigatorField = CitizensNPC.class.getDeclaredField("navigator");
            navigatorField.setAccessible(true);
            navigatorField.set(npc, new PatheticNavigator(npc));
        } catch (Exception e) {
            player.sendMessage(Component.text("Failed to modify navigator field: " + e.getMessage()).color(NamedTextColor.RED));
        }

        player.sendMessage(npc.getNavigator().getClass().toString());

    }

}
