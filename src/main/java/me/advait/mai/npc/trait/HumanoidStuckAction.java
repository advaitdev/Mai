package me.advait.mai.npc.trait;

import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.ai.StuckAction;
import net.citizensnpcs.api.npc.NPC;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class HumanoidStuckAction implements StuckAction {

    private void debugMessage(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendActionBar(Component.text(message).color(NamedTextColor.YELLOW));
        }
    }

    @Override
    public boolean run(NPC npc, Navigator navigator) {
        debugMessage("NPC is stuck.");
        return true;
    }

}
