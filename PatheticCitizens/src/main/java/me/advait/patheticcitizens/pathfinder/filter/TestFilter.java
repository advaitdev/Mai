package me.advait.patheticcitizens.pathfinder.filter;

import de.metaphoriker.pathetic.api.pathing.filter.PathFilter;
import de.metaphoriker.pathetic.api.pathing.filter.PathValidationContext;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Test filter; always returns true and plays a sound to indicate that it shouldn't be run in practice.
 */
public class TestFilter implements PathFilter {

    @Override
    public boolean filter(PathValidationContext pathValidationContext) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.BLOCK_BELL_USE, 1f, 1f);
        }

        return true;
    }
}
