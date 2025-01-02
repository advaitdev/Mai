package me.advait.mai.npc;

import me.advait.mai.Mai;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class NPCUtil {

    public static void createTestNPC(String playerName) {
        Player player = Bukkit.getPlayer(playerName);
        if (player == null) return;

        Bukkit.getScheduler().runTaskLater(JavaPlugin.getPlugin(Mai.class), () -> {
            CitizensAPI.getNPCRegistries().forEach(registry -> registry.sorted().forEach(NPC::destroy));
            NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, "Mai");
            npc.spawn(player.getLocation());
        }, 20);
    }

    public static NPC getMai() {
        for (NPC npc : CitizensAPI.getNPCRegistry()) return npc;
        return null;
    }

}
