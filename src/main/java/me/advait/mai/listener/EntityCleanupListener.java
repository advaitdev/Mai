package me.advait.mai.listener;

import me.advait.mai.Catalog;
import me.advait.mai.body.Humanoid;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Removes stale Mannequin entities when chunks load.
 * deploy.sh kills the server, so onDisable's killAll() may not persist
 * entity removals to disk — old Mannequins linger in unloaded chunks.
 */
public class EntityCleanupListener implements Listener {

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        Set<UUID> ownedIds = getOwnedMannequinIds();

        for (Entity entity : event.getEntities()) {
            if (entity instanceof Mannequin && !ownedIds.contains(entity.getUniqueId())) {
                entity.remove();
            }
        }
    }

    private Set<UUID> getOwnedMannequinIds() {
        Set<UUID> ids = new HashSet<>();
        for (Humanoid h : Catalog.getInstance().getAllHumanoids()) {
            // Use raw field access to avoid auto-respawn triggering
            Mannequin m = h.getRawMannequin();
            if (m != null) {
                ids.add(m.getUniqueId());
            }
        }
        return ids;
    }
}
