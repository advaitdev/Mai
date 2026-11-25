package me.advait.mai.course;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

/** Player events during a course: F restarts, no starving or dying, leaving restores the player. */
public final class CourseListener implements Listener {

    private final CourseManager courses = CourseManager.getInstance();

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (!courses.inCourse(e.getPlayer())) return;
        e.setCancelled(true);
        courses.onSwapHands(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && courses.onDamage(p, e.getFinalDamage())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && courses.inCourse(p)) e.setCancelled(true);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() != null) courses.onInteract(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        courses.onQuit(e.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        SavedState.recover(e.getPlayer());
    }
}
